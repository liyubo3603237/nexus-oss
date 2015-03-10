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
package org.sonatype.nexus.repository.httpbridge.internal.describe;

import java.util.Collections;
import java.util.Map;

/**
 * A do-nothing implementation of {@link Description}.
 *
 * @since 3.0
 */
public class NullDescription
    extends Description
{
  public NullDescription() {
    super(Collections.<String, Object>emptyMap());
  }

  @Override
  public boolean isDescribing() {
    return false;
  }

  @Override
  public Description topic(final String name) {
    return this;
  }

  @Override
  public Description add(final String name, final Object value) {
    return this;
  }

  @Override
  public Description addTable(final String name, final Map<String, Object> values) {
    return this;
  }

  @Override
  public Description addAll(final String name, final Iterable values) {
    return this;
  }
}
