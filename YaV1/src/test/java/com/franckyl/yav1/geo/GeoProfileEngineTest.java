package com.franckyl.yav1.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [P3-GEO] Tests for the geo profile rule engine: state-change triggered
 * pushes, debounce, connection/demo/alert guards with pending retry, and
 * manual-override suppression. The push machinery is mocked behind
 * {@link GeoProfileEngine.Gateway}.
 */
public class GeoProfileEngineTest
{
    /** Controllable fake of the V1 push machinery. */
    private static class FakeGateway implements GeoProfileEngine.Gateway
    {
        boolean connected  = true;
        boolean demo       = false;
        boolean alertQuiet = true;
        boolean pushResult = true;

        int currentSettingId = -1;
        int nextPushedId     = 100;

        final List<String> pushes    = new ArrayList<String>();
        final List<String> announces = new ArrayList<String>();
        final List<String> logs      = new ArrayList<String>();

        @Override
        public boolean isConnected()
        {
            return connected;
        }

        @Override
        public boolean isDemo()
        {
            return demo;
        }

        @Override
        public boolean isAlertQuiet()
        {
            return alertQuiet;
        }

        @Override
        public boolean pushProfile(String profileName)
        {
            if(pushResult)
            {
                pushes.add(profileName);
                currentSettingId = ++nextPushedId;
            }
            return pushResult;
        }

        @Override
        public int getCurrentSettingId()
        {
            return currentSettingId;
        }

        @Override
        public void announce(String profileName, String stateName)
        {
            announces.add(profileName + "|" + stateName);
        }

        @Override
        public void log(String msg)
        {
            logs.add(msg);
        }
    }

    private static class FakeClock implements GeoProfileEngine.Clock
    {
        long time = 1000000L;

        @Override
        public long now()
        {
            return time;
        }

        void advance(long ms)
        {
            time += ms;
        }
    }

    private FakeGateway      mGateway;
    private FakeClock        mClock;
    private GeoProfileEngine mEngine;

    @Before
    public void setUp()
    {
        mGateway = new FakeGateway();
        mClock   = new FakeClock();
        mEngine  = new GeoProfileEngine(mGateway, mClock);

        Map<String, String> rules = new HashMap<String, String>();
        rules.put("OH", "X band on");
        rules.put("KY", "K off");
        mEngine.setRules(rules);
    }

    // -------------------------------------------------------- basic pushes

    @Test
    public void entersStateWithRulePushes()
    {
        mEngine.onState("OH", "Ohio");

        assertEquals(1, mGateway.pushes.size());
        assertEquals("X band on", mGateway.pushes.get(0));
        assertEquals(1, mGateway.announces.size());
        assertEquals("X band on|Ohio", mGateway.announces.get(0));
    }

    @Test
    public void stateWithoutRuleDoesNothing()
    {
        mEngine.onState("IN", "Indiana");

        assertEquals(0, mGateway.pushes.size());
        assertEquals(0, mGateway.announces.size());
    }

    @Test
    public void nullStateDoesNothing()
    {
        mEngine.onState(null, null);

        assertEquals(0, mGateway.pushes.size());
    }

    @Test
    public void stayingInStateDoesNotRepush()
    {
        mEngine.onState("OH", "Ohio");
        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");
        mEngine.onState("OH", "Ohio");

        assertEquals(1, mGateway.pushes.size());
    }

    @Test
    public void reenteringStatePushesAgain()
    {
        mEngine.onState("OH", "Ohio");
        mClock.advance(120000);
        mEngine.onState("KY", "Kentucky");
        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");

        assertEquals(3, mGateway.pushes.size());
        assertEquals("X band on", mGateway.pushes.get(2));
    }

    // ------------------------------------------------------------ debounce

    @Test
    public void secondPushWithinDebounceIsDeferred()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        mClock.advance(30000); // 30 s < 60 s
        mEngine.onState("KY", "Kentucky");

        assertEquals("push must be deferred inside the debounce window",
                1, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        // still pending on the next fix inside the window
        mClock.advance(10000);
        mEngine.onState("KY", "Kentucky");
        assertEquals(1, mGateway.pushes.size());

        // window elapsed: retried and sent
        mClock.advance(25000);
        mEngine.onState("KY", "Kentucky");

        assertEquals(2, mGateway.pushes.size());
        assertEquals("K off", mGateway.pushes.get(1));
        assertFalse(mEngine.hasPendingPush());
    }

    @Test
    public void firstEverPushIsNotDebounced()
    {
        // no push has happened yet: no debounce applies
        mEngine.onState("KY", "Kentucky");
        assertEquals(1, mGateway.pushes.size());
    }

    // ------------------------------------------------------------- guards

    @Test
    public void noPushWhenDisconnectedThenPushedOnReconnect()
    {
        mGateway.connected = false;

        mEngine.onState("OH", "Ohio");
        assertEquals(0, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        // still disconnected on next fix
        mEngine.onState("OH", "Ohio");
        assertEquals(0, mGateway.pushes.size());

        // reconnect: pending rule fires on the next fix in the same state
        mGateway.connected = true;
        mEngine.onState("OH", "Ohio");

        assertEquals(1, mGateway.pushes.size());
        assertEquals("X band on", mGateway.pushes.get(0));
    }

    @Test
    public void noPushInDemoMode()
    {
        mGateway.demo = true;

        mEngine.onState("OH", "Ohio");
        mEngine.onState("OH", "Ohio");

        assertEquals(0, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());
    }

    @Test
    public void noPushDuringAlertThenPushedWhenQuiet()
    {
        mGateway.alertQuiet = false;

        mEngine.onState("OH", "Ohio");
        assertEquals("must never push mid-alert", 0, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        mGateway.alertQuiet = true;
        mEngine.onState("OH", "Ohio");

        assertEquals(1, mGateway.pushes.size());
    }

    @Test
    public void failedPushDropsTheRule()
    {
        mGateway.pushResult = false;

        mEngine.onState("OH", "Ohio");

        assertEquals(0, mGateway.pushes.size());
        assertFalse("failed push must not be retried forever", mEngine.hasPendingPush());
        assertEquals(0, mGateway.announces.size());
    }

    // ----------------------------------------------------- manual override

    @Test
    public void manualPushSuspendsAutoSwitching()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        // the user pushes another profile by hand: current id changes
        mGateway.currentSettingId = 999;

        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");

        assertTrue(mEngine.isManualOverride());

        // pending pushes in the same state are suppressed
        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());
    }

    @Test
    public void manualOverrideCancelsPendingPush()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        // enter KY inside the debounce window: push pending
        mClock.advance(10000);
        mEngine.onState("KY", "Kentucky");
        assertTrue(mEngine.hasPendingPush());

        // user pushes manually while the engine is waiting
        mGateway.currentSettingId = 999;
        mClock.advance(120000);
        mEngine.onState("KY", "Kentucky");

        assertEquals("manual override must cancel the pending push",
                1, mGateway.pushes.size());
        assertFalse(mEngine.hasPendingPush());
        assertTrue(mEngine.isManualOverride());
    }

    @Test
    public void overrideClearedOnNextStateChange()
    {
        mEngine.onState("OH", "Ohio");
        mGateway.currentSettingId = 999; // manual push
        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");
        assertTrue(mEngine.isManualOverride());

        // next state change: auto switching resumes
        mClock.advance(120000);
        mEngine.onState("KY", "Kentucky");

        assertFalse(mEngine.isManualOverride());
        assertEquals(2, mGateway.pushes.size());
        assertEquals("K off", mGateway.pushes.get(1));
    }

    @Test
    public void ownPushIsNotMistakenForManualOverride()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        // nothing changed externally; subsequent fixes must not latch override
        mClock.advance(120000);
        mEngine.onState("OH", "Ohio");

        assertFalse(mEngine.isManualOverride());
    }

    // --------------------------------------------------------------- misc

    @Test
    public void rulesCanBeReplacedAtRuntime()
    {
        Map<String, String> rules = new HashMap<String, String>();
        rules.put("IN", "Indiana profile");
        mEngine.setRules(rules);

        mEngine.onState("OH", "Ohio");
        assertEquals("OH rule was removed", 0, mGateway.pushes.size());

        mClock.advance(120000);
        mEngine.onState("IN", "Indiana");
        assertEquals(1, mGateway.pushes.size());
        assertEquals("Indiana profile", mGateway.pushes.get(0));
    }

    @Test
    public void resetKeepsDebounceClock()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        mEngine.reset();

        // re-enter immediately after reset: state change fires but debounce
        // from the pre-reset push still applies
        mClock.advance(1000);
        mEngine.onState("KY", "Kentucky");
        assertEquals(1, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        mClock.advance(60000);
        mEngine.onState("KY", "Kentucky");
        assertEquals(2, mGateway.pushes.size());
    }

    @Test
    public void decisionsAreLogged()
    {
        mEngine.onState("OH", "Ohio");
        mClock.advance(120000);
        mEngine.onState(null, null);

        boolean sawChange = false, sawPush = false, sawNone = false;

        for(String l: mGateway.logs)
        {
            if(l.contains("State change") && l.contains("OH"))
                sawChange = true;
            if(l.contains("Pushed profile"))
                sawPush = true;
            if(l.contains("none"))
                sawNone = true;
        }

        assertTrue(sawChange);
        assertTrue(sawPush);
        assertTrue(sawNone);
    }
}
