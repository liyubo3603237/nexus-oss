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
package org.sonatype.nexus.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.sonatype.nexus.configuration.ApplicationConfiguration;
import org.sonatype.nexus.configuration.ConfigurationChangeEvent;
import org.sonatype.nexus.proxy.attributes.AttributesHandler;
import org.sonatype.nexus.proxy.events.NexusStartedEvent;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.router.RepositoryRouter;
import org.sonatype.nexus.proxy.storage.remote.RemoteProviderHintFactory;
import org.sonatype.sisu.goodies.common.Loggers;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.slf4j.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Class AbstractProxyTestEnvironment.
 *
 * @author cstamas
 */
public abstract class AbstractProxyTestEnvironment
    extends AbstractNexusTestEnvironment
{
  private final Logger logger = Loggers.getLogger(getClass());

  /**
   * The config
   */
  private ApplicationConfiguration applicationConfiguration;

  /**
   * The repository registry.
   */
  private RepositoryRegistry repositoryRegistry;

  /**
   * The hint provider for remote repository storage.
   */
  private RemoteProviderHintFactory remoteProviderHintFactory;

  /**
   * The root router
   */
  private RepositoryRouter rootRouter;

  public ApplicationConfiguration getApplicationConfiguration() {
    return applicationConfiguration;
  }

  /**
   * Gets the repository registry.
   *
   * @return the repository registry
   */
  public RepositoryRegistry getRepositoryRegistry() {
    return repositoryRegistry;
  }

  public RemoteProviderHintFactory getRemoteProviderHintFactory() {
    return remoteProviderHintFactory;
  }

  /**
   * Gets the logger.
   *
   * @return the logger
   */
  public Logger getLogger() {
    return logger;
  }

  /**
   * Gets the root router.
   */
  public RepositoryRouter getRootRouter() {
    return rootRouter;
  }


  /*
   * (non-Javadoc)
   */
  @Override
  public void setUp()
      throws Exception
  {
    super.setUp();

    applicationConfiguration = lookup(ApplicationConfiguration.class);

    repositoryRegistry = lookup(RepositoryRegistry.class);

    // "ping" it
    lookup(AttributesHandler.class);

    remoteProviderHintFactory = lookup(RemoteProviderHintFactory.class);

    rootRouter = lookup(RepositoryRouter.class);

    environmentBuilder().startService();

    environmentBuilder().buildEnvironment(this);

    eventBus().post(new ConfigurationChangeEvent(applicationConfiguration, null, null));

    eventBus().post(new NexusStartedEvent(null));
  }

  /*
   * (non-Javadoc)
   */
  @Override
  public void tearDown()
      throws Exception
  {
    try {
      environmentBuilder().stopService();
    }
    finally {
      super.tearDown();
    }
  }

  private EnvironmentBuilder environmentBuilder;

  /**
   * Caches the {@link EnvironmentBuilder} to use same instance throughtout the test.
   */
  protected EnvironmentBuilder environmentBuilder() throws Exception {
    if (environmentBuilder == null) {
      environmentBuilder = getEnvironmentBuilder();
    }
    return environmentBuilder;
  }

  /**
   * Creates the environment builder instance, every time a NEW one.
   *
   * @return the environment builder
   */
  protected abstract EnvironmentBuilder getEnvironmentBuilder()
      throws Exception;

  /**
   * Check for file and match contents.
   *
   * @param item the item
   * @return true, if successful
   */
  protected void checkForFileAndMatchContents(StorageItem item)
      throws Exception
  {
    // file exists
    assertTrue(new File(getBasedir(), "target/test-classes/"
        + item.getRepositoryItemUid().getRepository().getId() + item.getRepositoryItemUid().getPath()).exists());
    // match content
    checkForFileAndMatchContents(item, new File(getBasedir(), "target/test-classes/"
        + item.getRepositoryItemUid().getRepository().getId() + item.getRepositoryItemUid().getPath()));
  }

  protected void checkForFileAndMatchContents(StorageItem item, StorageFileItem expected)
      throws Exception
  {
    assertStorageFileItem(item);

    StorageFileItem fileItem = (StorageFileItem) item;

    assertTrue("content equals", contentEquals(fileItem.getInputStream(), expected.getInputStream()));
  }

  /**
   * Check for file and match contents.
   *
   * @param item     the item
   * @param expected the wanted content
   * @throws Exception the exception
   */
  protected void checkForFileAndMatchContents(StorageItem item, File expected)
      throws Exception
  {
    assertStorageFileItem(item);

    StorageFileItem fileItem = (StorageFileItem) item;

    assertTrue("content equals", contentEquals(fileItem.getInputStream(), new FileInputStream(expected)));
  }

  protected void assertStorageFileItem(StorageItem item) {
    // is file
    assertTrue(item instanceof StorageFileItem);

    // is non-virtual
    assertFalse(item.isVirtual());

    // have UID
    assertTrue(item.getRepositoryItemUid() != null);

    StorageFileItem file = (StorageFileItem) item;

    // is reusable
    assertTrue(file.isReusableStream());
  }

  protected File getFile(Repository repository, String path)
      throws IOException
  {
    return new File(getApplicationConfiguration().getWorkingDirectory(), "proxy/store/" + repository.getId()
        + path);
  }

  protected File getRemoteFile(Repository repository, String path)
      throws IOException
  {
    return new File(getBasedir(), "target/test-classes/" + repository.getId() + path);
  }

  protected void saveItemToFile(StorageFileItem item, File file)
      throws IOException
  {
    try (InputStream is = item.getInputStream();
         FileOutputStream fos = new FileOutputStream(file)) {
      IOUtils.copy(is, fos);
      fos.flush();
    }
  }

  protected Metadata readMetadata(File mdf)
      throws Exception
  {
    MetadataXpp3Reader metadataReader = new MetadataXpp3Reader();
    try (InputStreamReader isr = new InputStreamReader(new FileInputStream(mdf))) {
      return metadataReader.read(isr);
    }
  }

  protected String contentAsString(StorageItem item)
      throws IOException
  {
    try (InputStream is = ((StorageFileItem) item).getInputStream()) {
      return IOUtils.toString(is, "UTF-8");
    }
  }

  protected File createTempFile(String prefix, String sufix)
      throws IOException
  {
    final File tmpDir = new File(getWorkHomeDir(), "ftemp");
    tmpDir.mkdirs();

    return File.createTempFile(prefix, sufix, tmpDir);
  }
}
