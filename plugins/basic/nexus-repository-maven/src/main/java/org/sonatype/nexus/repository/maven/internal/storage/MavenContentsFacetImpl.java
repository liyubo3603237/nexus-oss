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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.repository.storage.StorageFacet.*;

/**
 * A {@link MetadataContentsFacet} that persists Maven metadata as assets to a {@link StorageFacet}.
 *
 * @since 3.0
 */
@Named
public class MavenContentsFacetImpl
    extends FacetSupport
    implements MavenContentsFacet
{
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
      if (!coordinates.isSubordinate()) {
        final BlobRef blobRef = getBlobRef(coordinates, asset);
        final Blob blob = tx.getBlob(blobRef);
        checkState(blob != null, "asset of component with at path %s refers to missing blob %s", coordinates.getPath(),
            blobRef);
        return marshall(asset, blob);
      }
      else {
        // TODO: get HashCode attribute and make it a content
        throw new RuntimeException("tbd");
      }
    }
  }

  @Override
  public void putArtifact(final ArtifactCoordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex bucket = tx.getBucket();
      OrientVertex component = getArtifactComponent(tx, coordinates, bucket);
      OrientVertex asset;
      if (component == null) {
        // CREATE
        component = tx.createComponent(bucket);

        // Set normalized properties: format, group, and name
        component.setProperty(P_FORMAT, getRepository().getFormat().getValue()); // M1 or M2!!!
        final ArtifactCoordinates artifactCoordinates = (ArtifactCoordinates) coordinates;
        component.setProperty(P_GROUP, artifactCoordinates.getGroupId());
        component.setProperty(P_NAME, artifactCoordinates.getArtifactId());
        component.setProperty(P_VERSION, artifactCoordinates.getBaseVersion());

        // Set attributes map to contain "raw" format-specific metadata (in this case, path)
        tx.getAttributes(component).child(getRepository().getFormat().getValue()).set(P_PATH, coordinates.getPath());
        tx.getAttributes(component).child(getRepository().getFormat().getValue())
            .set("groupId", artifactCoordinates.getGroupId());
        tx.getAttributes(component).child(getRepository().getFormat().getValue())
            .set("artifactId", artifactCoordinates.getArtifactId());
        tx.getAttributes(component).child(getRepository().getFormat().getValue())
            .set("version", artifactCoordinates.getVersion());
        tx.getAttributes(component).child(getRepository().getFormat().getValue())
            .set("baseVersion", artifactCoordinates.getBaseVersion());
        tx.getAttributes(component).child(getRepository().getFormat().getValue())
            .set("extension", artifactCoordinates.getExtension());
        if (artifactCoordinates.getClassifier() != null) {
          tx.getAttributes(component).child(getRepository().getFormat().getValue())
              .set("classifier", artifactCoordinates.getClassifier());
        }

        asset = tx.createAsset(bucket);
        asset.addEdge(E_PART_OF_COMPONENT, component);
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

      try (TempStreamSupplier supplier = new TempStreamSupplier(content.openInputStream())) {
        try (InputStream is1 = supplier.get(); InputStream is2 = supplier.get()) {
          tx.setBlob(is1, headers, asset, hashAlgorithms,
              determineContentType(coordinates, is2, content.getContentType()));
        }
      }

      final DateTime lastUpdated = content.getLastUpdated();
      if (lastUpdated != null) {
        asset.setProperty(P_LAST_UPDATED, new Date(lastUpdated.getMillis()));
      }

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
    String property = String.format("%s.%s.%s", P_ATTRIBUTES, getRepository().getFormat().getValue(), P_PATH);
    return tx.findComponentWithProperty(property, coordinates.getPath(), bucket);
  }

  private OrientVertex getArtifactAsset(OrientVertex component) {
    List<Vertex> vertices = Lists.newArrayList(component.getVertices(Direction.IN, E_PART_OF_COMPONENT));
    checkState(!vertices.isEmpty());
    return (OrientVertex) vertices.get(0);
  }

  @Nullable
  @Override
  public Content getMetadata(final Coordinates coordinates) {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        return null;
      }

      if (!coordinates.isSubordinate()) {
        final BlobRef blobRef = getBlobRef(coordinates, asset);
        final Blob blob = tx.getBlob(blobRef);
        checkState(blob != null, "asset of component with at path %s refers to missing blob %s", coordinates.getPath(),
            blobRef);

        return marshall(asset, blob);
      }
      else {
        // TODO: get HashCode attribute and make it a content
        throw new RuntimeException("tbd");
      }
    }
  }

  @Override
  public void putMetadata(final Coordinates coordinates, final Content content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        // CREATE
        asset = tx.createAsset(tx.getBucket());

        asset.setProperty(P_FORMAT, getRepository().getFormat().getValue());
        asset.setProperty(P_NAME, coordinates.getPath());

        // "key", see getArtifactAsset?
        tx.getAttributes(asset).child(getRepository().getFormat().getValue()).set(P_PATH, coordinates.getPath());
      }

      // TODO: Figure out created-by header
      final ImmutableMap<String, String> headers = ImmutableMap.of(
          BlobStore.BLOB_NAME_HEADER, coordinates.getPath(),
          BlobStore.CREATED_BY_HEADER, "unknown"
      );

      try (TempStreamSupplier supplier = new TempStreamSupplier(content.openInputStream())) {
        try (InputStream is1 = supplier.get(); InputStream is2 = supplier.get()) {
          tx.setBlob(is1, headers, asset, hashAlgorithms,
              determineContentType(coordinates, is2, content.getContentType()));
        }
      }

      final DateTime lastUpdated = content.getLastUpdated();
      if (lastUpdated != null) {
        asset.setProperty(P_LAST_UPDATED, new Date(lastUpdated.getMillis()));
        asset.setProperty(P_PATH, coordinates.getPath());
      }

      tx.commit();
    }
  }

  @Override
  public boolean deleteMetadata(final Coordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = tx.findAssetWithProperty(P_PATH, coordinates.getPath(), tx.getBucket());
      if (asset == null) {
        return false;
      }

      tx.deleteBlob(getBlobRef(coordinates, asset));
      tx.deleteVertex(asset);
      tx.commit();
      return true;
    }
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
    String blobRefStr = asset.getProperty(P_BLOB_REF);
    checkState(blobRefStr != null, "asset of component at path %s has missing blob reference", coordinates.getPath());
    return BlobRef.parse(blobRefStr);
  }

  private Content marshall(final OrientVertex asset, final Blob blob) {
    final String contentType = asset.getProperty(P_CONTENT_TYPE);

    final Date date = asset.getProperty(P_LAST_UPDATED);
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
    final String contentType = asset.getProperty(P_CONTENT_TYPE);

    final Date date = asset.getProperty(P_LAST_UPDATED);
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
