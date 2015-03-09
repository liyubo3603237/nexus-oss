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
package org.sonatype.nexus.repository.maven.internal.policy;

import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Maven proxy checksum policy for Maven artifacts, enforcing them during transport or not.
 *
 * @since 3.0
 */
public class ChecksumPolicy
{
  private static final String V_IGNORE = "ignore";

  private static final String V_WARN = "warn";

  private static final String V_STRICT_IF_EXISTS = "strictIfExists";

  private static final String V_STRICT = "strict";

  public static final ChecksumPolicy IGNORE = new ChecksumPolicy(V_IGNORE);

  public static final ChecksumPolicy WARN = new ChecksumPolicy(V_WARN);

  public static final ChecksumPolicy STRICT_IF_EXISTS = new ChecksumPolicy(V_STRICT_IF_EXISTS);

  public static final ChecksumPolicy STRICT = new ChecksumPolicy(V_STRICT);

  public static ChecksumPolicy valueOf(final String value) {
    checkNotNull(value);
    switch (value.toLowerCase(Locale.US)) {
      case V_IGNORE: {
        return IGNORE;
      }
      case V_WARN: {
        return WARN;
      }
      case V_STRICT_IF_EXISTS: {
        return STRICT_IF_EXISTS;
      }
      case V_STRICT: {
        return STRICT;
      }
      default:
        throw new IllegalArgumentException("Unknown checksum policy: " + value);
    }
  }

  private final String value;

  private ChecksumPolicy(final String value) {
    this.value = checkNotNull(value);
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChecksumPolicy policy = (ChecksumPolicy) o;
    if (!value.equals(policy.value)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
