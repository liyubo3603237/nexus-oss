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
package org.sonatype.nexus.proxy.item;

import java.io.File;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.access.Action;
import org.sonatype.nexus.proxy.attributes.inspectors.DigestCalculatingInspector;
import org.sonatype.nexus.proxy.maven.MUtils;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.storage.local.fs.DefaultFSLocalRepositoryStorage;
import org.sonatype.nexus.proxy.walker.AbstractFileWalkerProcessor;
import org.sonatype.nexus.proxy.walker.DefaultWalkerContext;
import org.sonatype.nexus.proxy.walker.Walker;
import org.sonatype.nexus.proxy.walker.WalkerContext;
import org.sonatype.nexus.proxy.walker.WalkerException;
import org.sonatype.nexus.util.DigesterUtils;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.io.ByteStreams;
import org.apache.commons.lang.StringUtils;

/**
 * Reconciles any item attribute checksums affected by NEXUS-8178.
 *
 * @since 2.11.3
 */
@Named
public class ChecksumReconciler
    extends ComponentSupport
{
  private static final long MILLIS_IN_A_DAY = 86400000L;

  private final Walker walker;

  private final DigestCalculatingInspector digestCalculatingInspector;

  private final MessageDigest sha1;

  private final MessageDigest md5;

  private File attributesBaseDir;

  private long modifiedThreshold;

  @Inject
  public ChecksumReconciler(final Walker walker, final DigestCalculatingInspector digestCalculatingInspector)
      throws NoSuchAlgorithmException
  {
    this.walker = walker;

    this.digestCalculatingInspector = digestCalculatingInspector;

    sha1 = MessageDigest.getInstance("SHA1");
    md5 = MessageDigest.getInstance("MD5");
  }

  /**
   * Walks the selected sub-tree in the given repository attempting to reconcile their item attribute checksums.
   */
  public void reconcileChecksums(final Repository repo, final ResourceStoreRequest request, final int sinceDays) {
    final WalkerContext context = new DefaultWalkerContext(repo, request);

    final String repositoryId = repo.getId();

    attributesBaseDir = getAttributesBaseDir(repo);
    modifiedThreshold = getModifiedThreshold(sinceDays);

    if (attributesBaseDir == null) {
      return; // no point walking repository with no local storage
    }

    context.getProcessors().add(new AbstractFileWalkerProcessor()
    {
      @Override
      protected void processFileItem(final WalkerContext ctx, final StorageFileItem item) throws Exception {
        if (repositoryId.equals(item.getRepositoryId())) {
          reconcileItemChecksum(ctx.getRepository(), item);
        }
      }
    });

    try {
      walker.walk(context);
    }
    catch (final WalkerException e) {
      if (!(e.getWalkerContext().getStopCause() instanceof ItemNotFoundException)) {
        // everything that is not ItemNotFound should be reported, otherwise just neglect it
        throw e;
      }
    }
  }

  /**
   * Checks the checksum cached in the item's attributes to see if it needs reconciling with the stored content.
   */
  void reconcileItemChecksum(final Repository repo, final StorageFileItem item) throws Exception {

    final String attributeSHA1 = item.getRepositoryItemAttributes().get(DigestCalculatingInspector.DIGEST_SHA1_KEY);
    if (attributeSHA1 != null) {

      final String itemPath = item.getPath();
      if (shouldAttemptReconciliation(itemPath)) {

        sha1.reset();
        md5.reset();

        // look for checksums affected by the link persister pre-fetching the first 8 bytes (see NEXUS-8178)
        try (final InputStream is = new DigestInputStream(new DigestInputStream(item.getContentLocator().getContent(),
            sha1), md5)) {

          final byte[] buf = new byte[8];
          ByteStreams.read(is, buf, 0, 8);
          sha1.update(buf);
          md5.update(buf);

          ByteStreams.copy(is, ByteStreams.nullOutputStream());
        }

        final String affectedSHA1 = DigesterUtils.getDigestAsString(sha1.digest());

        if (attributeSHA1.equals(affectedSHA1)) {
          log.info("Reconciling attribute checksums for {}", item);
          final RepositoryItemUid uid = item.getRepositoryItemUid();
          uid.getLock().lock(Action.update);
          try {
            digestCalculatingInspector.processStorageItem(item);
            repo.getAttributesHandler().storeAttributes(item);
          }
          finally {
            uid.getLock().unlock();
          }
        }

        reconcileChecksumFile(repo, itemPath + ".sha1", affectedSHA1,
            item.getRepositoryItemAttributes().get(DigestCalculatingInspector.DIGEST_SHA1_KEY));

        final String affectedMD5 = DigesterUtils.getDigestAsString(md5.digest());

        reconcileChecksumFile(repo, itemPath + ".md5", affectedMD5,
            item.getRepositoryItemAttributes().get(DigestCalculatingInspector.DIGEST_MD5_KEY));
      }
    }
  }

  /**
   * Determines the base directory of local attribute storage.
   */
  private File getAttributesBaseDir(final Repository repo) {
    try {
      if (repo.getLocalStorage() instanceof DefaultFSLocalRepositoryStorage) {
        final File baseDir = ((DefaultFSLocalRepositoryStorage) repo.getLocalStorage()).getBaseDir(repo, null);
        return new File(new File(baseDir, ".nexus"), "attributes");
      }
    }
    catch (final Exception e) {
      log.warn("Problem finding local storage for {}", repo, e);
    }
    return null;
  }

  /**
   * Calculates the lastModified threshold for attribute files.
   */
  private static long getModifiedThreshold(final long modifiedSinceDays) {
    if (modifiedSinceDays >= 0 && modifiedSinceDays < (System.currentTimeMillis() / MILLIS_IN_A_DAY)) {
      return System.currentTimeMillis() - (MILLIS_IN_A_DAY * modifiedSinceDays);
    }
    return 0;
  }

  /**
   * Should we attempt checksum reconciliation for the given path?
   */
  private boolean shouldAttemptReconciliation(final String itemPath) {
    if (itemPath == null || itemPath.endsWith(".sha1") || itemPath.endsWith(".md5")) {
      return false; // ignore associated checksum files, we'll fix them when we process the main item
    }
    final File attributesFile = new File(attributesBaseDir, StringUtils.strip(itemPath, "/"));
    return attributesFile.lastModified() >= modifiedThreshold;
  }

  /**
   * Reconciles checksum file with the new value, but only if the file exists and contains the old value.
   */
  private void reconcileChecksumFile(final Repository repo, final String path, final String oldValue,
      final String newValue)
  {
    try {
      final ResourceStoreRequest request = new ResourceStoreRequest(path, true, false);
      if (repo.getLocalStorage().containsItem(repo, request)) {
        final StorageFileItem item = (StorageFileItem) repo.getLocalStorage().retrieveItem(repo, request);
        final RepositoryItemUid uid = item.getRepositoryItemUid();
        uid.getLock().lock(Action.update);
        try {
          if (oldValue.equals(MUtils.readDigestFromFileItem(item))) {
            log.info("Reconciling checksum in {}", item);
            item.setContentLocator(new StringContentLocator(newValue));
            repo.getLocalStorage().storeItem(repo, item);
          }
        }
        finally {
          uid.getLock().unlock();
        }
      }
    }
    catch (final Exception e) {
      log.warn("Problem reconciling {} with new checksum", path, e);
    }
  }
}
