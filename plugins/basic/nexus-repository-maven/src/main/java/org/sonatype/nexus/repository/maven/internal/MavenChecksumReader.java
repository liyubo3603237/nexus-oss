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
import java.io.InputStreamReader;
import java.util.Locale;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import org.codehaus.plexus.util.StringUtils;

/**
 * Utility class for reading up Maven .sha1/.md5 files that might be created by vast different tools out there.
 *
 * @since 3.0
 */
public class MavenChecksumReader
    extends ComponentSupport
{
  private MavenChecksumReader() {}

  /**
   * Reads the checksum file from a payload.
   *
   * @see {@link #readChecksum(String)}
   */
  @Nullable
  public static String readChecksum(final Payload payload)
      throws IOException
  {
    // TODO: either check payload size or ensure we read only first few bytes
    try (InputStreamReader isr = new InputStreamReader(payload.openInputStream(), Charsets.UTF_8)) {
      return readChecksum(CharStreams.toString(isr));
    }
  }

  /**
   * Reads the checksum from a string. If result appears to be a checksum (cannot be 100% sure), it is returned,
   * otherwise {@code null}.
   */
  @Nullable
  public static String readChecksum(final String input) {
    // TODO: remove this
    String raw = StringUtils.chomp(input).trim();

    if (Strings.isNullOrEmpty(raw)) {
      return null;
    }

    String digest;
    // digest string at end with separator, e.g.:
    // MD5 (pom.xml) = 68da13206e9dcce2db9ec45a9f7acd52
    // ant-1.5.jar: DCAB 88FC 2A04 3C24 79A6 DE67 6A2F 8179 E9EA 2167
    if (raw.contains("=") || raw.contains(":")) {
      digest = raw.split("[=:]", 2)[1].trim();
    }
    else {
      // digest string at start, e.g. '68da13206e9dcce2db9ec45a9f7acd52 pom.xml'
      digest = raw.split(" ", 2)[0];
    }

    if (!isDigest(digest)) {
      // maybe it's "uncompressed", e.g. 'DCAB 88FC 2A04 3C24 79A6 DE67 6A2F 8179 E9EA 2167'
      digest = compress(digest);
    }

    if (!isDigest(digest)) {
      // check if the raw string is an uncompressed checksum, e.g.
      // 'DCAB 88FC 2A04 3C24 79A6 DE67 6A2F 8179 E9EA 2167'
      digest = compress(raw);
    }

    if (!isDigest(digest) && raw.contains(" ")) {
      // check if the raw string is an uncompressed checksum with file name suffix, e.g.
      // 'DCAB 88FC 2A04 3C24 79A6 DE67 6A2F 8179 E9EA 2167 pom.xml'
      digest = compress(raw.substring(0, raw.lastIndexOf(" ")).trim());
    }

    if (!isDigest(digest)) {
      // we have to return some string even if it's not a valid digest, because 'null' is treated as
      // "checksum does not exist" elsewhere (AbstractChecksumContentValidator)
      // -> fallback to original behavior
      digest = raw.split(" ", 2)[0];
    }

    if (isDigest(digest)) {
      return digest;
    }
    else {
      return null;
    }
  }

  /**
   * Returns {@code true} if the string "looks like" maven hash file content (md5 or sha1).
   */
  public static boolean isDigest(final String digest) {
    return digest.length() >= 32 && digest.matches("^[a-z0-9]+$");
  }

  // ==

  private static String compress(String digest) {
    digest = digest.replaceAll(" ", "").toLowerCase(Locale.US);
    return digest;
  }
}
