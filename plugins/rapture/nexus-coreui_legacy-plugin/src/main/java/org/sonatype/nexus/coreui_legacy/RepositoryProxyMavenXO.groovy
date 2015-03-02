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
package org.sonatype.nexus.coreui_legacy

import groovy.transform.ToString
import org.sonatype.nexus.proxy.maven.ChecksumPolicy
import org.sonatype.nexus.proxy.maven.RepositoryPolicy

import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull

/**
 * Repository Proxy Maven exchange object.
 *
 * @since 3.0
 */
@ToString(includePackage = false, includeNames = true)
class RepositoryProxyMavenXO
extends RepositoryProxyXO
{
  RepositoryPolicy repositoryPolicy

  @NotNull
  Boolean downloadRemoteIndexes

  ChecksumPolicy checksumPolicy

  @Min(-1L)
  @Max(511000L)
  Integer artifactMaxAge

  @Min(-1L)
  @Max(511000L)
  Integer metadataMaxAge
}
