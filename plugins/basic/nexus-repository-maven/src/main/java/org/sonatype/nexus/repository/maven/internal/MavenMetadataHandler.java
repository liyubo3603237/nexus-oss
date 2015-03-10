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

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.maven.internal.storage.MavenContentsFacet;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import static org.sonatype.nexus.repository.http.HttpMethods.DELETE;
import static org.sonatype.nexus.repository.http.HttpMethods.GET;
import static org.sonatype.nexus.repository.http.HttpMethods.PUT;

/**
 * Maven metadata handler.
 *
 * @since 3.0
 */
@Singleton
@Named
public class MavenMetadataHandler
    extends ComponentSupport
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(final @Nonnull Context context) throws Exception {
    final String action = context.getRequest().getAction();
    final MavenContentsFacet mavenContentsFacet = context.getRepository().facet(MavenContentsFacet.class);
    final Coordinates coordinates = context.getAttributes().require(Coordinates.class);
    switch (action) {
      case GET: {
        final Payload content = mavenContentsFacet.getMetadata(coordinates);
        if (content == null) {
          return HttpResponses.notFound(coordinates.getPath());
        }
        return HttpResponses.ok(content);
      }

      case PUT: {
        mavenContentsFacet.putMetadata(coordinates, context.getRequest().getPayload());
        return HttpResponses.created();
      }

      case DELETE: {
        final boolean deleted = mavenContentsFacet.deleteMetadata(coordinates);
        if (!deleted) {
          return HttpResponses.notFound(coordinates.getPath());
        }
        return HttpResponses.noContent();
      }

      default:
        return HttpResponses.methodNotAllowed(context.getRequest().getAction(), GET, PUT, DELETE);
    }
  }
}
