/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.maven.internal.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobRef;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.common.io.TempStreamSupplier;
import org.sonatype.nexus.mime.MimeSupport;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.content.InvalidContentException;
import org.sonatype.nexus.repository.maven.internal.ArtifactCoordinates;
import org.sonatype.nexus.repository.maven.internal.Content;
import org.sonatype.nexus.repository.maven.internal.Coordinates;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.util.NestedAttributesMap;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.io.ByteStreams;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * A {@link MavenContentsFacet} that persists Maven artifacts and metadata to a {@link StorageFacet}.
 *
 * @since 3.0
 */
@Named
public class MavenContentsFacetImpl
    extends FacetSupport
    implements MavenContentsFacet
{
  // component properties

  private static final String P_GROUP_ID = "groupId";

  private static final String P_ARTIFACT_ID = "artifactId";

  private static final String P_VERSION = "version";

  private static final String P_BASE_VERSION = "baseVersion";

  private static final String P_CLASSIFIER = "classifier";

  private static final String P_EXTENSION = "extension";

  private static final String P_SNAPSHOT = "snapshot"; // boolean

  // asset properties

  private static final String P_EXT_HASHCODES = "exthashcodes"; // child map keyed by HashType.ext -- hashes got externally

  private final static List<HashAlgorithm> hashAlgorithms = Lists.newArrayList(MD5, SHA1);

  private final MimeSupport mimeSupport;

  private boolean strictContentTypeValidation = false;

  @Inject
  public MavenContentsFacetImpl(final MimeSupport mimeSupport) {
    this.mimeSupport = checkNotNull(mimeSupport);
  }

  @Nullable
  @Override
  public Content getArtifact(final ArtifactCoordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex component = getArtifactComponent(tx, coordinates, tx.getBucket());
      if (component == null) {
        return null;
      }

      final OrientVertex asset = getArtifactAsset(component);
      if (coordinates.getHashType() == null) {
        final BlobRef blobRef = getBlobRef(coordinates, asset);
        final Blob blob = tx.getBlob(blobRef);
        checkState(blob != null, "asset of component with at path %s refers to missing blob %s", coordinates.getPath(),
            blobRef);
        return marshall(asset, blob);
      }
      else {
        final HashCode extHash = getExtHashCode(tx.getAttributes(asset), coordinates.getHashType().getHashAlgorithm());
        if (extHash == null) {
          return null; // unknown external hash
        }
        return marshall(asset, extHash.toString());
      }
    }
  }

  @Override
  public void putArtifact(final ArtifactCoordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    if (coordinates.isHash()) {
      putArtifactHash(coordinates, content);
    }
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex bucket = tx.getBucket();
      OrientVertex component = getArtifactComponent(tx, coordinates, bucket);
      OrientVertex asset;
      if (component == null) {
        // CREATE
        component = tx.createComponent(bucket);

        // Set normalized properties: format, group, and name
        component.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue()); // M1 or M2!!!
        component.setProperty(StorageFacet.P_GROUP, coordinates.getGroupId());
        component.setProperty(StorageFacet.P_NAME, coordinates.getArtifactId());
        component.setProperty(StorageFacet.P_VERSION, coordinates.getBaseVersion());

        final NestedAttributesMap attributes = getFormatAttributes(tx.getAttributes(component));
        attributes.set(StorageFacet.P_PATH, coordinates.getPath());
        attributes.set(P_GROUP_ID, coordinates.getGroupId());
        attributes.set(P_ARTIFACT_ID, coordinates.getArtifactId());
        attributes.set(P_VERSION, coordinates.getVersion());
        attributes.set(P_BASE_VERSION, coordinates.getBaseVersion());
        attributes.set(P_EXTENSION, coordinates.getExtension());
        attributes.set(P_SNAPSHOT, coordinates.isSnapshot());
        if (coordinates.getClassifier() != null) {
          tx.getAttributes(component).child(getRepository().getFormat().getValue())
              .set(P_CLASSIFIER, coordinates.getClassifier());
        }

        asset = tx.createAsset(bucket);
        asset.addEdge(StorageFacet.E_PART_OF_COMPONENT, component);
      }
      else {
        // UPDATE
        asset = getArtifactAsset(component);
      }

      // TODO: Figure out created-by header
      final ImmutableMap<String, String> headers = ImmutableMap.of(
          BlobStore.BLOB_NAME_HEADER, coordinates.getPath(),
          BlobStore.CREATED_BY_HEADER, "unknown"
      );

      // TODO: hash the content for SHA1 and MD5?
      try (TempStreamSupplier supplier = new TempStreamSupplier(content.openInputStream())) {
        try (InputStream is1 = supplier.get(); InputStream is2 = supplier.get()) {
          tx.setBlob(is1, headers, asset, hashAlgorithms,
              determineContentType(coordinates, is2, content.getContentType()));
        }
      }

      final DateTime lastUpdated = content.getLastUpdated();
      if (lastUpdated != null) {
        asset.setProperty(StorageFacet.P_LAST_UPDATED, new Date(lastUpdated.getMillis()));
      }

      tx.commit();
    }
  }

  /**
   * verifies client sent hash and sets it into attributes. Expected that main artifact is present, verifies client
   * expectations regarding artifact are met.
   */
  private void putArtifactHash(final ArtifactCoordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex bucket = tx.getBucket();
      OrientVertex component = getArtifactComponent(tx, coordinates, bucket);
      if (component == null) {
        // does not exists?
        // TODO: how to reject this now with 400 Bad Request?
      }
      final OrientVertex asset = getArtifactAsset(component);

      // TODO: sanity check for size!
      final HashCode hashCode = HashCode.fromBytes(ByteStreams.toByteArray(content.openInputStream()));
      putExtHashCode(tx.getAttributes(asset), coordinates.getHashType().getHashAlgorithm(), hashCode);

      // TODO: grab NX internal hash it content on blob, compare it
      // TODO: if OK, store it/update EXT child map with hash value (as "externallly" provided)
      // TODO: if not-OK, reject upload with 400, possible transfer corruption? Or in case of proxy?
      // TODO: ChecksumPolicy?

      tx.commit();
    }
  }

  @Override
  public boolean deleteArtifact(final ArtifactCoordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex component = getArtifactComponent(tx, coordinates, tx.getBucket());
      if (component == null) {
        return false;
      }
      OrientVertex asset = getArtifactAsset(component);

      tx.deleteBlob(getBlobRef(coordinates, asset));
      tx.deleteVertex(asset);
      tx.deleteVertex(component);

      tx.commit();

      return true;
    }
  }

  // TODO: Consider a top-level indexed property (e.g. "locator") to make these common lookups fast
  private OrientVertex getArtifactComponent(StorageTx tx, ArtifactCoordinates coordinates, OrientVertex bucket) {
    String property = String
        .format("%s.%s.%s", StorageFacet.P_ATTRIBUTES, getRepository().getFormat().getValue(), StorageFacet.P_PATH);
    return tx.findComponentWithProperty(property, coordinates.getPath(), bucket);
  }

  private OrientVertex getArtifactAsset(OrientVertex component) {
    List<Vertex> vertices = Lists.newArrayList(component.getVertices(Direction.IN, StorageFacet.E_PART_OF_COMPONENT));
    checkState(!vertices.isEmpty());
    return (OrientVertex) vertices.get(0);
  }

  @Nullable
  @Override
  public Content getMetadata(final Coordinates coordinates) {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(StorageFacet.P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        return null;
      }

      if (coordinates.getHashType() == null) {
        final BlobRef blobRef = getBlobRef(coordinates, asset);
        final Blob blob = tx.getBlob(blobRef);
        checkState(blob != null, "asset of component with at path %s refers to missing blob %s", coordinates.getPath(),
            blobRef);
        return marshall(asset, blob);
      }
      else {
        final HashCode extHash = getExtHashCode(tx.getAttributes(asset), coordinates.getHashType().getHashAlgorithm());
        if (extHash == null) {
          return null; // unknown external hash
        }
        return marshall(asset, extHash.toString());
      }
    }
  }

  @Override
  public void putMetadata(final Coordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    if (coordinates.isHash()) {
      putMetadataHash(coordinates, content);
    }
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(StorageFacet.P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        // CREATE
        asset = tx.createAsset(tx.getBucket());

        asset.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue());
        asset.setProperty(StorageFacet.P_NAME, coordinates.getPath());

        // "key", see getArtifactAsset?
        tx.getAttributes(asset).child(getRepository().getFormat().getValue())
            .set(StorageFacet.P_PATH, coordinates.getPath());
      }

      // TODO: Figure out created-by header
      final ImmutableMap<String, String> headers = ImmutableMap.of(
          BlobStore.BLOB_NAME_HEADER, coordinates.getPath(),
          BlobStore.CREATED_BY_HEADER, "unknown"
      );

      // TODO: hash the content for SHA1 and MD5?
      try (TempStreamSupplier supplier = new TempStreamSupplier(content.openInputStream())) {
        try (InputStream is1 = supplier.get(); InputStream is2 = supplier.get()) {
          tx.setBlob(is1, headers, asset, hashAlgorithms,
              determineContentType(coordinates, is2, content.getContentType()));
        }
      }

      final DateTime lastUpdated = content.getLastUpdated();
      if (lastUpdated != null) {
        asset.setProperty(StorageFacet.P_LAST_UPDATED, new Date(lastUpdated.getMillis()));
        asset.setProperty(StorageFacet.P_PATH, coordinates.getPath());
      }

      tx.commit();
    }
  }

  /**
   * verifies client sent hash and sets it into attributes. Expected that main metadata is present, verifies client
   * expectations regarding artifact are met.
   */
  private void putMetadataHash(final Coordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(StorageFacet.P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        // illegal
      }


      // TODO: sanity check for size!
      final HashCode hashCode = HashCode.fromBytes(ByteStreams.toByteArray(content.openInputStream()));
      putExtHashCode(tx.getAttributes(asset), coordinates.getHashType().getHashAlgorithm(), hashCode);

      tx.commit();
    }
  }

  @Override
  public boolean deleteMetadata(final Coordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(StorageFacet.P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        return false;
      }

      tx.deleteBlob(getBlobRef(coordinates, asset));
      tx.deleteVertex(asset);
      tx.commit();
      return true;
    }
  }

  private NestedAttributesMap getFormatAttributes(final NestedAttributesMap attributes) {
    return attributes.child(getRepository().getFormat().getValue());
  }

  @Nullable
  private HashCode getExtHashCode(final NestedAttributesMap attributes,
                                  final HashAlgorithm hashAlgorithm)
  {
    final NestedAttributesMap hashes = getFormatAttributes(attributes).child(P_EXT_HASHCODES);
    final String hashCodeString = hashes.get(hashAlgorithm.name(), String.class);
    if (hashCodeString == null) {
      return null;
    }
    return HashCode.fromString(hashCodeString);
  }

  private void putExtHashCode(final NestedAttributesMap attributes,
                              final HashAlgorithm hashAlgorithm,
                              final HashCode hashCode)
  {
    final NestedAttributesMap hashes = getFormatAttributes(attributes).child(P_EXT_HASHCODES);
    hashes.set(hashAlgorithm.name(), hashCode.toString());
  }

  /**
   * Determines or confirms the content type for the content, or throws {@link InvalidContentException} if it cannot.
   */
  @Nonnull
  private String determineContentType(final Coordinates coordinates,
                                      final InputStream is,
                                      final String declaredContentType)
      throws IOException
  {
    String contentType = declaredContentType;

    if (contentType == null) {
      log.trace("Content PUT to {} has no content type.", coordinates);
      contentType = mimeSupport.detectMimeType(is, coordinates.getPath());
      log.trace("Mime support implies content type {}", contentType);

      if (contentType == null && strictContentTypeValidation) {
        throw new InvalidContentException(String.format("Content type could not be determined."));
      }
    }
    else {
      final List<String> types = mimeSupport.detectMimeTypes(is, coordinates.getPath());
      if (!types.isEmpty() && !types.contains(contentType)) {
        log.debug("Discovered content type {} ", types.get(0));
        if (strictContentTypeValidation) {
          throw new InvalidContentException(
              String.format("Declared content type %s, but declared %s.", contentType, types.get(0)));
        }
      }
    }
    return contentType;
  }

  private StorageFacet getStorage() {
    return getRepository().facet(StorageFacet.class);
  }

  /**
   * Creates a content out of a Blob, for content originating from Blobs.
   */
  private BlobRef getBlobRef(final Coordinates coordinates, final OrientVertex asset) {
    String blobRefStr = asset.getProperty(StorageFacet.P_BLOB_REF);
    checkState(blobRefStr != null, "asset of component at path %s has missing blob reference", coordinates.getPath());
    return BlobRef.parse(blobRefStr);
  }

  private Content marshall(final OrientVertex asset, final Blob blob) {
    final String contentType = asset.getProperty(StorageFacet.P_CONTENT_TYPE);

    final Date date = asset.getProperty(StorageFacet.P_LAST_UPDATED);
    final DateTime lastUpdated = date == null ? null : new DateTime(date.getTime());

    return new Content()
    {
      @Override
      public String getContentType() {
        return contentType;
      }

      @Override
      public long getSize() {
        return blob.getMetrics().getContentSize();
      }

      @Override
      public InputStream openInputStream() {
        return blob.getInputStream();
      }

      @Override
      public DateTime getLastUpdated() {
        return lastUpdated;
      }
    };
  }

  /**
   * Creates a content out of String, for "subordinate" contents like hashCodes.
   */
  private Content marshall(final OrientVertex asset, final String payload) {
    final String contentType = asset.getProperty(StorageFacet.P_CONTENT_TYPE);

    final Date date = asset.getProperty(StorageFacet.P_LAST_UPDATED);
    final DateTime lastUpdated = date == null ? null : new DateTime(date.getTime());
    final byte[] bytes = payload.getBytes(Charsets.UTF_8);

    return new Content()
    {
      @Override
      public String getContentType() {
        return contentType;
      }

      @Override
      public long getSize() {
        return bytes.length;
      }

      @Override
      public InputStream openInputStream() {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public DateTime getLastUpdated() {
        return lastUpdated;
      }
    };
  }
}
