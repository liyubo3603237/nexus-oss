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
package org.sonatype.nexus.common.stateguard

import org.junit.Before
import org.junit.Test
import org.sonatype.sisu.litmus.testsupport.TestSupport

import static org.junit.Assert.fail
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.DESTROYED
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.FAILED
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.INITIALISED
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.NEW
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.STARTED
import static org.sonatype.nexus.common.stateguard.StateGuardTest.State.STOPPED

/**
 * Tests for {@link StateGuard}.
 */
class StateGuardTest
    extends TestSupport
{
  static class State
  {
    public static final String NEW = 'NEW'

    public static final String INITIALISED = 'INITIALISED'

    public static final String STARTED = 'STARTED'

    public static final String STOPPED = 'STOPPED'

    public static final String DESTROYED = 'DESTROYED'

    public static final String FAILED = 'FAILED'
  }

  private static class TriggeredAction
      implements Action<Void>
  {
    boolean triggered = false

    @Override
    Void run() throws Exception {
      triggered = true
      return null;
    }
  }

  private static class NopAction
      implements Action<Void>
  {
    @Override
    Void run() throws Exception {
      return null;
    }
  }

  private StateGuard underTest

  @Before
  void setUp() {
    underTest = new StateGuard.Builder()
        .initial(NEW)
        .failure(FAILED)
        .create()

    assert underTest.current == NEW
  }

  @Test
  void 'basic transition'() {
    def action = new TriggeredAction()
    underTest.transition(INITIALISED)
        .from(NEW)
        .run(action)

    assert underTest.current == INITIALISED
    assert action.triggered
  }

  @Test
  void 'nested transition'() {
    underTest.transition(INITIALISED)
        .from(NEW)
        .run(new NopAction())
    assert underTest.current == INITIALISED

    underTest.transition(STARTED)
        .from(INITIALISED)
        .run(new NopAction())
    assert underTest.current == STARTED

    def stoppedAction = new TriggeredAction()
    def stop = {
      underTest.transition(STOPPED)
          .from(STARTED)
          .run(stoppedAction)
    }

    def destroyedAction = new Action<Void>() {
      boolean triggered
      @Override
      Void run() throws Exception {
        if (underTest.is(STARTED)) {
          stop()
        }
        assert underTest.current == STOPPED
        triggered = true
        return null
      }
    }

    underTest.transition(DESTROYED)
        .run(destroyedAction)

    assert underTest.current == DESTROYED
    assert stoppedAction.triggered
    assert destroyedAction.triggered
  }

  @Test
  void 'invalid transition'() {
    def action = new TriggeredAction()
    try {
      underTest.transition(STARTED)
          .from(INITIALISED)
          .run(action)

      fail()
    }
    catch (IllegalStateException e) {
      // expected
    }

    assert underTest.current == NEW
    assert !action.triggered
  }

  private static class FailureException
      extends Exception
  {
    FailureException() {
      super('FAILED')
    }
  }

  @Test
  void 'transition with action failing with exception'() {
    try {
      underTest.transition(INITIALISED)
          .from(NEW)
          .run(new Action<Void>() {
        @Override
        Void run() throws Exception {
          throw new FailureException()
        }
      })

      fail()
    }
    catch (FailureException e) {
      // expected
    }

    assert underTest.current == FAILED
  }

  private static class FailureError
      extends Error
  {
    FailureError() {
      super('FAILED')
    }
  }

  @Test
  void 'transition with action failing with error'() {
    try {
      underTest.transition(INITIALISED)
          .from(NEW)
          .run(new Action<Void>() {
        @Override
        Void run() throws Exception {
          throw new FailureError()
        }
      })

      fail()
    }
    catch (FailureError e) {
      // expected
    }

    assert underTest.current == FAILED
  }

  @Test
  void 'basic guard'() {
    def action = new TriggeredAction()
    underTest.guard(NEW)
        .run(action)

    assert underTest.current == NEW
    assert action.triggered
  }

  @Test
  void 'invalid guard'() {
    def action = new TriggeredAction()

    try {
      underTest.guard(INITIALISED)
          .run(action)

      fail()
    }
    catch (IllegalStateException e) {
      // expected
    }

    assert underTest.current == NEW
    assert !action.triggered
  }
}