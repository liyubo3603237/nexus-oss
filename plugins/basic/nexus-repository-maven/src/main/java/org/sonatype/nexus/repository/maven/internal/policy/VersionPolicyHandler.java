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

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.maven.internal.ArtifactCoordinates;
import org.sonatype.nexus.repository.maven.internal.MavenFacet;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import static org.sonatype.nexus.repository.maven.internal.ContextHelper.artifactCoordinates;
import static org.sonatype.nexus.repository.maven.internal.policy.VersionPolicy.RELEASE;
import static org.sonatype.nexus.repository.maven.internal.policy.VersionPolicy.SNAPSHOT;

/**
 * Handler enforcing version policy on Maven repositories.
 *
 * @since 3.0
 */
@Singleton
@Named
public class VersionPolicyHandler
    extends ComponentSupport
    implements Handler
{
  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    final ArtifactCoordinates artifactCoordinates = artifactCoordinates(context);
    if (artifactCoordinates != null) {
      final MavenFacet mavenFacet = context.getRepository().facet(MavenFacet.class);
      final VersionPolicy versionPolicy = mavenFacet.getVersionPolicy();
      final boolean isSnapshot = mavenFacet.getPathParser().isSnapshotVersion(artifactCoordinates.getVersion());
      if ((SNAPSHOT.equals(versionPolicy) && !isSnapshot) || (RELEASE.equals(versionPolicy) && isSnapshot)) {
        return HttpResponses.badRequest(
            "VersionPolicy '" + versionPolicy + "' forbids version '" + artifactCoordinates.getVersion() + "'");
      }
    }
    return context.proceed();
  }
}
