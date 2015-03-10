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
package org.sonatype.nexus.repository.maven.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import org.sonatype.nexus.repository.maven.internal.Coordinates;
import org.sonatype.nexus.repository.maven.internal.Coordinates.HashType;
import org.sonatype.nexus.repository.maven.internal.MavenChecksumReader;
import org.sonatype.nexus.repository.maven.internal.policy.VersionPolicy;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.util.NestedAttributesMap;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.StreamPayload;
import org.sonatype.nexus.repository.view.payloads.StringPayload;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * A {@link MavenFacet} that persists Maven artifacts and metadata to a {@link StorageFacet}.
 * <p/>
 * Structure for artifacts (CMA components and assets):
 * <ul>
 * <li>CMA components: keyed by groupId:artifactId:baseVersion</li>
 * <li>CMA assets: keyed by groupId:artifactId:version[:classifier]:extension</li>
 * </ul>
 * <p/>
 * Structure for metadata (CMA assets only):
 * <ul>
 * <li>CMA assets: keyed by path</li>
 * </ul>
 * In both cases, "external" hashes are stored as asset attributes.
 *
 * @since 3.0
 */
@Named
public class MavenFacetImpl
    extends FacetSupport
    implements MavenFacet
{
  // artifact shared properties of both, component and asset

  private static final String P_GROUP_ID = "groupId";

  private static final String P_ARTIFACT_ID = "artifactId";

  private static final String P_VERSION = "version";

  private static final String P_CLASSIFIER = "classifier";

  private static final String P_EXTENSION = "extension";

  private static final String P_SNAPSHOT = "snapshot"; // boolean

  // artifact component properties

  private static final String P_COMPONENT_KEY = "key"; // component key: G:A:bV for fast look-ups

  // artifact asset properties

  private static final String P_ASSET_KEY = "key"; // asset key: G:A:V[:C]:E for fast look-ups

  // shared for both artifact and metadata assets

  private static final String P_EXT_CHECKSUM = "extChecksum"; // similar to P_CHECKSUM, child map of hashes got externally

  // ==

  private final static List<HashAlgorithm> hashAlgorithms = Lists.newArrayList(MD5, SHA1);

  // ==

  private final MimeSupport mimeSupport;

  private final Map<String, ArtifactCoordinatesParser> artifactCoordinatesParsers;

  public static final String CONFIG_KEY = "maven";

  // ==

  private ArtifactCoordinatesParser artifactCoordinatesParser;

  private VersionPolicy versionPolicy;

  private boolean strictContentTypeValidation = false;

  @Inject
  public MavenFacetImpl(final MimeSupport mimeSupport, final Map<String, ArtifactCoordinatesParser> artifactCoordinatesParsers) {
    this.mimeSupport = checkNotNull(mimeSupport);
    this.artifactCoordinatesParsers = checkNotNull(artifactCoordinatesParsers);
  }

  @Override
  protected void doConfigure() throws Exception {
    super.doConfigure();
    NestedAttributesMap attributes = getRepository().getConfiguration().attributes(CONFIG_KEY);
    this.versionPolicy = VersionPolicy.valueOf(
        attributes.require("versionPolicy", String.class)
    );
    this.artifactCoordinatesParser = checkNotNull(
        artifactCoordinatesParsers.get(getRepository().getFormat().getValue()),
        "No ArtifactCoordinatesParser for format %s",
        getRepository().getFormat().getValue()
    );
  }

  @Nonnull
  @Override
  public ArtifactCoordinatesParser getArtifactCoordinatesParser() {
    return artifactCoordinatesParser;
  }

  @Override
  public VersionPolicy getVersionPolicy() {
    return versionPolicy;
  }

  @Nullable
  @Override
  public Payload getArtifact(final ArtifactCoordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex component = findArtifactComponent(tx, getComponentKey(coordinates), tx.getBucket());
      if (component == null) {
        return null;
      }
      final OrientVertex asset = selectArtifactAsset(tx, component, getAssetKey(coordinates));
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
        final String extHash = getExtHashCode(tx, asset, coordinates.getHashType());
        if (extHash == null) {
          return null; // unknown external hash
        }
        return marshall(asset, extHash);
      }
    }
  }

  @Override
  public void putArtifact(final ArtifactCoordinates coordinates, final Payload content)
      throws IOException, InvalidContentException
  {
    if (coordinates.isHash()) {
      putArtifactHash(coordinates, content);
    }
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex bucket = tx.getBucket();
      final String componentKey = getComponentKey(coordinates);
      OrientVertex component = findArtifactComponent(tx, componentKey, bucket);
      if (component == null) {
        component = tx.createComponent(bucket);

        // Set normalized properties: format, group, and name
        component.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue()); // M1 or M2!!!
        component.setProperty(StorageFacet.P_GROUP, coordinates.getGroupId());
        component.setProperty(StorageFacet.P_NAME, coordinates.getArtifactId());
        component.setProperty(StorageFacet.P_VERSION, coordinates.getBaseVersion());

        final NestedAttributesMap componentAttributes = getFormatAttributes(tx, component);
        componentAttributes.set(P_COMPONENT_KEY, componentKey);
        componentAttributes.set(P_GROUP_ID, coordinates.getGroupId());
        componentAttributes.set(P_ARTIFACT_ID, coordinates.getArtifactId());
        componentAttributes.set(P_VERSION, coordinates.getBaseVersion());
        componentAttributes.set(P_SNAPSHOT, coordinates.isSnapshot());
      }

      final String assetKey = getAssetKey(coordinates);
      OrientVertex asset = selectArtifactAsset(tx, component, assetKey);
      if (asset == null) {
        asset = tx.createAsset(bucket);
        asset.addEdge(StorageFacet.E_PART_OF_COMPONENT, component);
        final NestedAttributesMap assetAttributes = getFormatAttributes(tx, asset);
        assetAttributes.set(P_ASSET_KEY, assetKey);
        assetAttributes.set(P_GROUP_ID, coordinates.getGroupId());
        assetAttributes.set(P_ARTIFACT_ID, coordinates.getArtifactId());
        assetAttributes.set(P_VERSION, coordinates.getVersion());
        if (coordinates.getClassifier() != null) {
          assetAttributes.set(P_CLASSIFIER, coordinates.getClassifier());
        }
        assetAttributes.set(P_EXTENSION, coordinates.getExtension());
        assetAttributes.set(P_SNAPSHOT, coordinates.isSnapshot());
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

      asset.setProperty(StorageFacet.P_LAST_UPDATED, new Date());

      tx.commit();
    }
  }

  private void putArtifactHash(final ArtifactCoordinates coordinates, final Payload content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex bucket = tx.getBucket();
      OrientVertex component = findArtifactComponent(tx, getComponentKey(coordinates), bucket);
      if (component == null) {
        // does not exists? Reject it
        throw new InvalidContentException("No component for hash: " + coordinates.getPath());
      }
      final OrientVertex asset = selectArtifactAsset(tx, component, getAssetKey(coordinates));
      if (asset == null) {
        // does not exists? Reject it
        throw new InvalidContentException("No asset for hash: " + coordinates.getPath());
      }

      setAssetHashCode(tx, asset, coordinates, content);

      tx.commit();
    }
  }

  @Override
  public boolean deleteArtifact(final ArtifactCoordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex component = findArtifactComponent(tx, getComponentKey(coordinates), tx.getBucket());
      if (component == null) {
        return false;
      }
      OrientVertex asset = selectArtifactAsset(tx, component, getAssetKey(coordinates));
      if (asset == null) {
        return false;
      }
      // TODO: clean up component w/o assets?
      final boolean lastAsset = getArtifactAssets(component).size() == 1;

      tx.deleteBlob(getBlobRef(coordinates, asset));
      tx.deleteVertex(asset);
      if (lastAsset) {
        tx.deleteVertex(component);
      }
      tx.commit();
      return true;
    }
  }

  private String getComponentKey(final ArtifactCoordinates coordinates) {
    // TODO: maybe sha1() the resulting string?
    return coordinates.getGroupId()
        + ":" + coordinates.getArtifactId()
        + ":" + coordinates.getBaseVersion();
  }

  private String getAssetKey(final ArtifactCoordinates coordinates) {
    // TODO: maybe sha1() the resulting string?
    if (Strings.isNullOrEmpty(coordinates.getClassifier())) {
      return coordinates.getGroupId()
          + ":" + coordinates.getArtifactId()
          + ":" + coordinates.getVersion()
          + ":" + coordinates.getExtension();
    }
    else {
      return coordinates.getGroupId()
          + ":" + coordinates.getArtifactId()
          + ":" + coordinates.getVersion()
          + ":" + coordinates.getClassifier()
          + ":" + coordinates.getExtension();
    }
  }

  /**
   * Finds component by key.
   */
  @Nullable
  private OrientVertex findArtifactComponent(final StorageTx tx,
                                             final String componentKey,
                                             final OrientVertex bucket)
  {
    final String componentKeyName =
        StorageFacet.P_ATTRIBUTES + "." + getRepository().getFormat().getValue() + "." + P_COMPONENT_KEY;
    return tx.findComponentWithProperty(componentKeyName, componentKey, bucket);
  }

  /**
   * Returns a list of component assets.
   */
  private List<OrientVertex> getArtifactAssets(final OrientVertex component)
  {
    final List<Vertex> vertices = Lists
        .newArrayList(component.getVertices(Direction.IN, StorageFacet.E_PART_OF_COMPONENT));
    final List<OrientVertex> result = Lists.newArrayList();
    for (Vertex v : vertices) {
      if (v instanceof OrientVertex) {
        result.add((OrientVertex) v);
      }
    }
    return result;
  }

  /**
   * Selects a component asset by key.
   */
  @Nullable
  private OrientVertex selectArtifactAsset(final StorageTx tx,
                                           final OrientVertex component,
                                           final String assetKey)
  {
    final List<OrientVertex> assets = getArtifactAssets(component);
    for (OrientVertex v : assets) {
      final NestedAttributesMap attributesMap = getFormatAttributes(tx, v);
      if (assetKey.equals(attributesMap.get(P_ASSET_KEY, String.class))) {
        return v;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Payload getMetadata(final Coordinates coordinates) {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = findMetadataAsset(tx, getMetadataKey(coordinates), tx.getBucket());
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
        final String extHash = getExtHashCode(tx, asset, coordinates.getHashType());
        if (extHash == null) {
          return null; // unknown external hash
        }
        return marshall(asset, extHash);
      }
    }
  }

  @Override
  public void putMetadata(final Coordinates coordinates, final Payload content)
      throws IOException, InvalidContentException
  {
    if (coordinates.isHash()) {
      putMetadataHash(coordinates, content);
    }
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = findMetadataAsset(tx, getMetadataKey(coordinates), tx.getBucket());
      if (asset == null) {
        asset = tx.createAsset(tx.getBucket());

        asset.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue());
        asset.setProperty(StorageFacet.P_NAME, coordinates.getFileName());
        asset.setProperty(StorageFacet.P_PATH, coordinates.getPath());

        getFormatAttributes(tx, asset).set(StorageFacet.P_NAME, coordinates.getFileName());
        getFormatAttributes(tx, asset).set(StorageFacet.P_PATH, coordinates.getPath());
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

      asset.setProperty(StorageFacet.P_LAST_UPDATED, new Date());
      tx.commit();
    }
  }

  private void putMetadataHash(final Coordinates coordinates, final Payload content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = findMetadataAsset(tx, getMetadataKey(coordinates), tx.getBucket());
      if (asset == null) {
        throw new InvalidContentException("No asset for hash: " + coordinates.getPath());
      }

      setAssetHashCode(tx, asset, coordinates, content);

      tx.commit();
    }
  }

  @Override
  public boolean deleteMetadata(final Coordinates coordinates) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      OrientVertex asset = findMetadataAsset(tx, getMetadataKey(coordinates), tx.getBucket());
      if (asset == null) {
        return false;
      }

      tx.deleteBlob(getBlobRef(coordinates, asset));
      tx.deleteVertex(asset);
      tx.commit();
      return true;
    }
  }

  private String getMetadataKey(final Coordinates coordinates) {
    // TODO: maybe sha1() the resulting string?
    return coordinates.getPath();
  }

  /**
   * Assumes payload is a hash file, parses it like that. Takes existing -- same type -- hash from asset attributes and
   * compares the two, throws if not. Finally, if all ok, stores the supplied hash in format specific attributes of the
   * asset, see {@link #putExtHashCode(StorageTx, OrientVertex, HashType, String)}.
   */
  private void setAssetHashCode(final StorageTx tx, final OrientVertex asset, final Coordinates coordinates,
                                final Payload content)
      throws IOException, InvalidContentException
  {
    // TODO: sort hash parsing, this might be text produced by some tool like sha1sum is!
    // TODO: sanity check for size!
    final String artifactHash = MavenChecksumReader.readChecksum(content);
    if (artifactHash == null) {
      // we could not interpret the payload
      throw new InvalidContentException("Unrecognized checksum for path: " + coordinates.getPath());
    }
    final String existingHash = tx.getAttributes(asset).child(StorageFacet.P_CHECKSUM)
        .get(coordinates.getHashType().getHashAlgorithm().name(), String.class);
    checkArgument(existingHash != null);

    if (!Objects.equals(artifactHash, existingHash)) {
      tx.rollback();
      throw new InvalidContentException(
          "Invalid hash for " + coordinates.getPath() + ": expected '" + existingHash + "' got '" + artifactHash +
              "'");
    }

    putExtHashCode(tx, asset, coordinates.getHashType(), artifactHash);
  }

  /**
   * Finds metadata by key.
   */
  @Nullable
  private OrientVertex findMetadataAsset(final StorageTx tx,
                                         final String metadataKey,
                                         final OrientVertex bucket)
  {
    final String metadataKeyName =
        StorageFacet.P_ATTRIBUTES + "." + getRepository().getFormat().getValue() + "." + StorageFacet.P_PATH;
    return tx.findAssetWithProperty(metadataKeyName, metadataKey, bucket);
  }

  private NestedAttributesMap getFormatAttributes(final StorageTx tx, final OrientVertex vertex) {
    return tx.getAttributes(vertex).child(getRepository().getFormat().getValue());
  }

  @Nullable
  private String getExtHashCode(final StorageTx tx,
                                final OrientVertex vertex,
                                final Coordinates.HashType hashType)
  {
    final NestedAttributesMap hashes = getFormatAttributes(tx, vertex).child(P_EXT_CHECKSUM);
    final String hashCodeString = hashes.get(hashType.getHashAlgorithm().name(), String.class);
    if (hashCodeString == null) {
      return null;
    }
    return hashCodeString;
  }

  private void putExtHashCode(final StorageTx tx,
                              final OrientVertex vertex,
                              final Coordinates.HashType hashType,
                              final String hashCode)
  {
    final NestedAttributesMap hashes = getFormatAttributes(tx, vertex).child(P_EXT_CHECKSUM);
    hashes.set(hashType.getHashAlgorithm().name(), hashCode);
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

  private Payload marshall(final OrientVertex asset, final Blob blob) {
    final String contentType = asset.getProperty(StorageFacet.P_CONTENT_TYPE);
    return new StreamPayload(blob.getInputStream(), blob.getMetrics().getContentSize(), contentType);
  }

  /**
   * Creates a payload out of String, for "subordinate" contents like hashCodes which content is stored as attribute of
   * "main" content..
   */
  private Payload marshall(final OrientVertex asset, final String payload) {
    final String contentType = asset.getProperty(StorageFacet.P_CONTENT_TYPE);
    return new StringPayload(payload, Charsets.UTF_8, contentType);
  }
}
