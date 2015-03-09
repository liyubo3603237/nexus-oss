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
package org.sonatype.nexus.repository.maven.internal.maven2

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

import org.sonatype.nexus.repository.Format
import org.sonatype.nexus.repository.RecipeSupport
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.Type
import org.sonatype.nexus.repository.group.GroupFacetImpl
import org.sonatype.nexus.repository.group.GroupHandler
import org.sonatype.nexus.repository.maven.internal.MavenArtifactMatcher
import org.sonatype.nexus.repository.maven.internal.MavenMetadataMatcher
import org.sonatype.nexus.repository.maven.internal.MetadataMergeHandler
import org.sonatype.nexus.repository.security.SecurityHandler
import org.sonatype.nexus.repository.types.HostedType
import org.sonatype.nexus.repository.view.ConfigurableViewFacet
import org.sonatype.nexus.repository.view.Route
import org.sonatype.nexus.repository.view.Router
import org.sonatype.nexus.repository.view.ViewFacet
import org.sonatype.nexus.repository.view.handlers.TimingHandler

import static org.sonatype.nexus.repository.http.HttpHandlers.notFound

/**
 * Maven 2 group repository recipe.
 *
 * @since 3.0
 */
@Named(Maven2GroupRecipe.NAME)
@Singleton
class Maven2GroupRecipe
    extends RecipeSupport
{
  static final String NAME = "maven2-group"

  @Inject
  Provider<Maven2SecurityFacet> securityFacet

  @Inject
  Provider<ConfigurableViewFacet> viewFacet

  @Inject
  Provider<GroupFacetImpl> groupFacet

  @Inject
  TimingHandler timingHandler

  @Inject
  SecurityHandler securityHandler

  @Inject
  GroupHandler groupHandler

  @Inject
  MetadataMergeHandler metadataMergeHandler

  @Inject
  Maven2GroupRecipe(@Named(HostedType.NAME) final Type type,
                    @Named(Maven2Format.NAME) final Format format)
  {
    super(type, format)
  }

  @Override
  void apply(@Nonnull final Repository repository) throws Exception {
    repository.attach(securityFacet.get())
    repository.attach(groupFacet.get());
    repository.attach(configure(viewFacet.get()))
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder()

    builder.route(new Route.Builder()
        .matcher(new MavenArtifactMatcher(new Maven2ArtifactCoordinatesParser()))
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(groupHandler)
        .create())

    builder.route(new Route.Builder()
        .matcher(new MavenMetadataMatcher())
        .handler(timingHandler)
        .handler(securityHandler)
        .handler(metadataMergeHandler)
        .create())

    builder.defaultHandlers(notFound())

    facet.configure(builder.create())

    return facet
  }
}
