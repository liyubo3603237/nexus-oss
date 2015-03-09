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

import java.util.Map;

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.group.GroupFacet;
import org.sonatype.nexus.repository.group.GroupHandler.DispatchedRepositories;
import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.http.HttpStatus;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.ViewFacet;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.collect.Maps;

import static org.sonatype.nexus.repository.http.HttpMethods.GET;

/**
 * Maven metadata merge handler.
 *
 * @since 3.0
 */
@Singleton
@Named
public class MetadataMergeHandler
    extends ComponentSupport
    implements Handler
{
  @Override
  public Response handle(final @Nonnull Context context) throws Exception {
    final String action = context.getRequest().getAction();
    switch (action) {
      case GET: {
        return handleMerged(context);
      }

      default:
        return HttpResponses.methodNotAllowed(action, GET);
    }
  }

  // TODO: merge and then cache in group storage (for next hashCode req)? If so, then
  // TODO: add event handler that would listen for member content changes and evict?
  // TODO: ie. group storage would contain something only if asked for, and would be safe to just nuke store anytime?
  @Nonnull
  private Response handleMerged(final @Nonnull Context context) throws Exception {
    final Request request = context.getRequest();
    final DispatchedRepositories dispatched = request.getAttributes().getOrCreate(DispatchedRepositories.class);
    final Repository repository = context.getRepository();
    final GroupFacet group = repository.facet(GroupFacet.class);
    final Map<Repository, Response> responses = Maps.newHashMap();
    for (Repository member : group.members()) {
      log.trace("Trying member: {}", member);

      // track repositories we have dispatched to, prevent circular dispatch for nested groups
      if (dispatched.contains(member)) {
        log.trace("Skipping already dispatched member: {}", member);
        continue;
      }
      dispatched.add(member);

      final ViewFacet view = member.facet(ViewFacet.class);
      final Response response = view.dispatch(request);
      if (response.getStatus().getCode() == HttpStatus.OK) {
        responses.put(member, response);
      }
    }
    if (responses.isEmpty()) {
      return HttpResponses.notFound();
    }

    // TODO: do the actual metadatamerge
    return responses.values().iterator().next();
  }
}
