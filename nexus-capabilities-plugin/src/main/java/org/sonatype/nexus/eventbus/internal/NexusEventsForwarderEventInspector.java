/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.eventbus.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.sonatype.nexus.eventbus.NexusEventBus;
import org.sonatype.nexus.proxy.events.EventInspector;
import org.sonatype.nexus.proxy.events.NexusInitializedEvent;
import org.sonatype.plexus.appevents.Event;

/**
 * Listens to Nexus events and forwards them to {@link NexusEventBus}.
 * <p/>
 * It also registers all {@link NexusEventBus.LoadOnStart} marked handlers.
 *
 * @since 2.0
 */
@Singleton
public class NexusEventsForwarderEventInspector
    implements EventInspector
{

    private final NexusEventBus eventBus;

    private final List<NexusEventBus.LoadOnStart> handlers;

    @Inject
    public NexusEventsForwarderEventInspector( final NexusEventBus eventBus,
                                               final List<NexusEventBus.LoadOnStart> handlers )
    {
        this.eventBus = checkNotNull( eventBus );
        this.handlers = checkNotNull( handlers );
    }

    public boolean accepts( final Event<?> evt )
    {
        return evt != null;
    }

    public void inspect( final Event<?> evt )
    {
        if ( !accepts( evt ) )
        {
            return;
        }
        if ( evt instanceof NexusInitializedEvent )
        {
            for ( final NexusEventBus.LoadOnStart handler : checkNotNull( handlers ) )
            {
                eventBus.register( handler );
            }
        }
        eventBus.post( evt );
    }

}
