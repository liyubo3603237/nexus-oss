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
package org.sonatype.nexus.repository.view;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.internal.describe.Description;
import org.sonatype.nexus.repository.internal.describe.DescriptionRenderer;
import org.sonatype.nexus.repository.internal.describe.DescriptionUtils;
import org.sonatype.nexus.repository.internal.describe.NullDescription;
import org.sonatype.nexus.repository.util.NestedAttributesMap;
import org.sonatype.nexus.repository.view.payloads.StringPayload;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;

/**
 * Configurable {@link ViewFacet} implementation.
 *
 * @since 3.0
 */
@Named
public class ConfigurableViewFacet
    extends FacetSupport
    implements ViewFacet
{
  public static final String CONFIG_KEY = "view";

  private final DescriptionRenderer descriptionRenderer;

  private Router router;

  private Boolean online;

  @Inject
  public ConfigurableViewFacet(final DescriptionRenderer descriptionRenderer) {
    this.descriptionRenderer = checkNotNull(descriptionRenderer);
  }

  public void configure(final Router router) {
    checkNotNull(router);
    checkState(this.router == null, "Router already configured");
    this.router = router;
  }

  @Override
  protected void doConfigure() throws Exception {
    NestedAttributesMap attributes = getRepository().getConfiguration().attributes(CONFIG_KEY);
    online = attributes.get("online", Boolean.class, true);
    log.debug("Online: {}", online);
  }

  @Override
  public Response dispatch(final Request request) throws Exception {
    checkState(router != null, "Router not configured");

    Description desc = descriptionBuilder(request);

    desc.add("Repository", getRepository().getName());

    try {
      if (desc.isDescribing()) {
        DescriptionUtils.describeRequest(desc, request);
      }

      final Response response = router.dispatch(getRepository(), request, desc);

      if (desc.isDescribing()) {
        DescriptionUtils.describeResponse(desc, response);
        return toHtmlResponse(desc);
      }

      return response;
    }
    catch (Exception e) {
      if (desc.isDescribing()) {
        DescriptionUtils.describeException(desc, e);
        return toHtmlResponse(desc);
      }
      throw e;
    }
  }

  @Override
  public boolean isOnline() {
    return online;
  }

  @VisibleForTesting
  Description descriptionBuilder(final Request request) {
    if (request.getParameters().contains("describe")) {
      return new Description(
          ImmutableMap.<String, Object>of(
              "path", nullToEmpty(request.getPath()),
              "nexusUrl", "" // TODO: We need a real Nexus URL
          )
      );
    }
    else {
      return new NullDescription();
    }
  }

  @VisibleForTesting
  Response toHtmlResponse(final Description description) {
    final String html = descriptionRenderer.renderHtml(description);
    return HttpResponses.ok(new StringPayload(html, Charsets.UTF_8, "text/html"));
  }

  @VisibleForTesting
  @Override
  protected Repository getRepository() {
    return super.getRepository();
  }
}
