/*
 * Copyright 2005-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sonatype.security.ldap;

/**
 * Helper class to encode and decode ldap names and values.
 *
 * <p>
 * NOTE: This is a copy from Spring LDAP so that both Spring LDAP 1.x and 2.x
 * can be supported without reflection.
 * </p>
 *
 * @author Adam Skogman
 * @author Mattias Hellborg Arthursson
 */
public final class LdapEncoder
{

  private static String[] NAME_ESCAPE_TABLE = new String[96];

  static {

    // Name encoding table -------------------------------------

    // all below 0x20 (control chars)
    for (char c = 0; c < ' '; c++) {
      NAME_ESCAPE_TABLE[c] = "\\" + toTwoCharHex(c);
    }

    NAME_ESCAPE_TABLE['#'] = "\\#";
    NAME_ESCAPE_TABLE[','] = "\\,";
    NAME_ESCAPE_TABLE[';'] = "\\;";
    NAME_ESCAPE_TABLE['='] = "\\=";
    NAME_ESCAPE_TABLE['+'] = "\\+";
    NAME_ESCAPE_TABLE['<'] = "\\<";
    NAME_ESCAPE_TABLE['>'] = "\\>";
    NAME_ESCAPE_TABLE['\"'] = "\\\"";
    NAME_ESCAPE_TABLE['\\'] = "\\\\";
  }

  /**
   * All static methods - not to be instantiated.
   */
  private LdapEncoder() {
  }

  protected static String toTwoCharHex(char c) {

    String raw = Integer.toHexString(c).toUpperCase();

    if (raw.length() > 1) {
      return raw;
    }
    else {
      return "0" + raw;
    }
  }

  /**
   * LDAP Encodes a value for use with a DN. Escapes for LDAP, not JNDI!
   *
   * <br/>Escapes:<br/> ' ' [space] - "\ " [if first or last] <br/> '#'
   * [hash] - "\#" <br/> ',' [comma] - "\," <br/> ';' [semicolon] - "\;" <br/> '=
   * [equals] - "\=" <br/> '+' [plus] - "\+" <br/> '&lt;' [less than] -
   * "\&lt;" <br/> '&gt;' [greater than] - "\&gt;" <br/> '"' [double quote] -
   * "\"" <br/> '\' [backslash] - "\\" <br/>
   *
   * @param value the value to escape.
   * @return The escaped value.
   */
  public static String nameEncode(String value) {

    if (value == null) {
      return null;
    }

    // make buffer roomy
    StringBuilder encodedValue = new StringBuilder(value.length() * 2);

    int length = value.length();
    int last = length - 1;

    for (int i = 0; i < length; i++) {

      char c = value.charAt(i);

      // space first or last
      if (c == ' ' && (i == 0 || i == last)) {
        encodedValue.append("\\ ");
        continue;
      }

      if (c < NAME_ESCAPE_TABLE.length) {
        // check in table for escapes
        String esc = NAME_ESCAPE_TABLE[c];

        if (esc != null) {
          encodedValue.append(esc);
          continue;
        }
      }

      // default: add the char
      encodedValue.append(c);
    }

    return encodedValue.toString();
  }

}
