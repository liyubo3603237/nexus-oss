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
package org.sonatype.nexus.internal.blobstore

import com.google.inject.util.Providers
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.orient.DatabaseInstanceRule
import org.sonatype.sisu.litmus.testsupport.TestSupport

import static org.junit.Assert.fail

/**
 * Tests for {@link BlobStoreConfigurationStoreImpl}.
 */
class BlobStoreConfigurationStoreImplTest
    extends TestSupport
{
  @Rule
  public DatabaseInstanceRule database = new DatabaseInstanceRule('test')

  private BlobStoreConfigurationStoreImpl underTest

  @Before
  void setup() {
    underTest = new BlobStoreConfigurationStoreImpl(
        Providers.of(database.instance),
        new BlobStoreConfigurationEntityAdapter()
    )
    underTest.start()
  }

  @After
  void tearDown() {
    if (underTest) {
      underTest.stop()
      underTest = null
    }
  }

  @Test
  void 'Can create a new BlobStoreConfiguration'() {
    createConfig()
  }

  @Test
  void 'Can list the persisted configurations'() {
    BlobStoreConfiguration entity = createConfig()
    List<BlobStoreConfiguration> list = underTest.list()
    assert list.size() == 1
    assert list[0].name == entity.name
    assert list[0].attributes == entity.attributes
  }

  @Test
  void 'Can delete an existing BlobStoreConfiguration'() {
    BlobStoreConfiguration entity = createConfig()
    assert underTest.list()
    underTest.delete(entity)
    assert !underTest.list()
  }

  @Test
  void 'Names are unique'() {
    BlobStoreConfiguration entity = createConfig()

    try {
      createConfig(entity.name, 'path2')
      fail()
    }
    catch (ORecordDuplicatedException e) {
      // FIXME: This is fragile for refactoring
      assert e.toString().contains('name_idx')
    }
  }

  private BlobStoreConfiguration createConfig(name = 'foo', path = 'bar') {
    def entity = new BlobStoreConfiguration(
        name: name,
        recipeName: 'file',
        attributes: [file:[path:path]]
        //TODO - where to enforce path validation rules, and are these rules already defined somewhere in Nexus for 
        // reuse?
    )
    underTest.create(entity)
    return entity
  }
}
