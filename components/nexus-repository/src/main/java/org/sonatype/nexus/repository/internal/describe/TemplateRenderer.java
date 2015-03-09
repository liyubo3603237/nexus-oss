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

import java.io.IOException;
import java.util.Map;

import org.sonatype.nexus.repository.view.Payload;

/**
 * Template rendering helper.
 *
 * @since 3.0
 */
public interface TemplateRenderer
{
  interface TemplateLocator
  {
    public String name();

    public ClassLoader classloader();
  }

  /**
   * Returns a template locator instance. The template existence in this moment is not checked, only the locator
   * is constructed.
   *
   * @param name        A FQ binary name of the template.
   * @param classloader The ClassLoader to be used to locate the template.
   */
  TemplateLocator template(String name, ClassLoader classloader);

  /**
   * Renders a template to servlet response. After rendering, the response will be commitet. Before calling this
   * method, a proper response code and all the response headers should be set.
   *
   * @throws IllegalArgumentException if the template locator does not points to an existing template.
   */
  String render(TemplateLocator tl, Map<String, Object> dataModel)
      throws IOException, IllegalArgumentException;
}
