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
package org.sonatype.nexus.common.validation

import org.junit.Test
import org.sonatype.sisu.litmus.testsupport.TestSupport

/**
 * Tests for {@link ValidationResponse}
 */
class ValidationResponseTest
    extends TestSupport
{
  @Test
  void 'empty'() {
    def response = new ValidationResponse()
    assert response.empty
  }

  @Test
  void 'not empty with warnings'() {
    def response = new ValidationResponse()
    response.addWarning(new ValidationMessage('foo', 'bar'))
    assert !response.empty
  }

  @Test
  void 'not empty with errors'() {
    def response = new ValidationResponse()
    response.addError(new ValidationMessage('foo', 'bar'))
    assert !response.empty
  }

  @Test
  void 'not empty with warnings and errors'() {
    def response = new ValidationResponse()
    response.addWarning(new ValidationMessage('foo', 'bar'))
    response.addError(new ValidationMessage('foo', 'bar'))
    assert !response.empty
  }
}
