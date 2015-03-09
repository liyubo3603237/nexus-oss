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
package org.sonatype.nexus.repository.internal.describe;

import java.util.Map;
import java.util.Map.Entry;

import org.sonatype.nexus.repository.util.StringMultimap;

import com.google.common.collect.Maps;

/**
 * Utility methods for transforming content int
 *
 * @since 3.0
 */
public class DescriptionUtils
{
  private DescriptionUtils() {
  }

  public static Map<String, Object> toMap(final Iterable<Entry<String, Object>> entries) {
    Map<String, Object> table = Maps.newHashMap();
    for (Entry<String, Object> entry : entries) {
      table.put(entry.getKey(), entry.getValue());
    }
    return table;
  }

  public static Map<String, Object> toMap(final StringMultimap headers) {
    Map<String, Object> table = Maps.newHashMap();
    final Iterable<Entry<String, String>> entries = headers.entries();
    for (Entry<String, String> e : entries) {
      table.put(e.getKey(), e.getValue());
    }
    return table;
  }
}
