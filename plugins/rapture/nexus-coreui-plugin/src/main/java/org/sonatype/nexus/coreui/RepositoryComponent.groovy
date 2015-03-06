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
package org.sonatype.nexus.coreui

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.validation.Valid
import javax.validation.constraints.NotNull
import javax.validation.groups.Default

import org.sonatype.nexus.common.validation.Create
import org.sonatype.nexus.common.validation.Update
import org.sonatype.nexus.common.validation.Validate
import org.sonatype.nexus.extdirect.DirectComponent
import org.sonatype.nexus.extdirect.DirectComponentSupport
import org.sonatype.nexus.repository.MissingFacetException
import org.sonatype.nexus.repository.Recipe
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.httpclient.HttpClientFacet
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.view.ViewFacet

import com.softwarementors.extjs.djn.config.annotations.DirectAction
import com.softwarementors.extjs.djn.config.annotations.DirectMethod
import org.apache.shiro.authz.annotation.RequiresAuthentication
import org.hibernate.validator.constraints.NotEmpty

/**
 * Repository {@link DirectComponent}.
 *
 * @since 3.0
 */
@Named
@Singleton
@DirectAction(action = 'coreui_Repository')
class RepositoryComponent
extends DirectComponentSupport
{
  @Inject
  RepositoryManager repositoryManager

  @Inject
  Map<String, Recipe> recipes
  
  @Inject
  AttributeConverter attributeConverter

  @DirectMethod
  List<RepositoryXO> read() {
    repositoryManager.browse().collect { asRepository(it) }
  }

  @DirectMethod
  List<ReferenceXO> readRecipes() {
    recipes.collect { key, value ->
      new ReferenceXO(
          id: key,
          name: "${value.format} (${value.type})"
      )
    }
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate(groups = [Create.class, Default.class])
  RepositoryXO create(final @NotNull(message = '[repository] may not be null') @Valid RepositoryXO repository) {
    return asRepository(repositoryManager.create(new Configuration(
        repositoryName: repository.name,
        recipeName: repository.recipe,
        attributes: attributeConverter.asAttributes(repository.attributes)
    )))
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate(groups = [Update.class, Default.class])
  RepositoryXO update(final @NotNull(message = '[repository] may not be null') @Valid RepositoryXO repository) {
    return asRepository(repositoryManager.update(repositoryManager.get(repository.name).configuration.with {
      attributes = attributeConverter.asAttributes(repository.attributes)
      return it
    }))
  }

  @DirectMethod
  @RequiresAuthentication
  @Validate
  void remove(final @NotEmpty(message = '[name] may not be empty') String name) {
    repositoryManager.delete(name)
  }

  RepositoryXO asRepository(Repository input) {
    def status = input.facet(ViewFacet).online ? 'Online' : 'Offline'
    try {
      def remoteStatus = input.facet(HttpClientFacet).status
      status += " - ${remoteStatus.description}"
      if (remoteStatus.reason) {
        status += "<br/><i>${remoteStatus.reason}</i>"
      }
    }
    catch (MissingFacetException e) {
      // no proxy, no remote status
    }
    return new RepositoryXO(
        name: input.name,
        type: input.type,
        format: input.format,
        online: input.facet(ViewFacet).online,
        status: status,
        attributes: attributeConverter.asAttributes(input.configuration.attributes)
    )
  }

}
