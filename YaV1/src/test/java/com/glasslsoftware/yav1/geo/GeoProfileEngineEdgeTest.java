package com.glasslsoftware.yav1.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [QA] Component tests for GeoProfileEngine edge cases beyond
 * GeoProfileEngineTest: the exact debounce boundary, pending rules dropped
 * on state change, null rule maps, and reset re-arming the current state.
 */
public class GeoProfileEngineEdgeTest
{
    private static class FakeGateway implements GeoProfileEngine.Gateway
    {
        boolean connected  = true;
        boolean demo       = false;
        boolean alertQuiet = true;

        int currentSettingId = -1;
        int nextPushedId     = 100;

        final List<String> pushes = new ArrayList<String>();
        final List<String> logs   = new ArrayList<String>();

        @Override public boolean isConnected()  { return connected; }
        @Override public boolean isDemo()       { return demo; }
        @Override public boolean isAlertQuiet() { return alertQuiet; }

        @Override
        public boolean pushProfile(String profileName)
        {
            pushes.add(profileName);
            currentSettingId = ++nextPushedId;
            return true;
        }

        @Override public int getCurrentSettingId() { return currentSettingId; }
        @Override public void announce(String p, String s) { }
        @Override public void log(String msg) { logs.add(msg); }
    }

    private static class FakeClock implements GeoProfileEngine.Clock
    {
        long time = 1_000_000L;
        @Override public long now() { return time; }
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

    @Test
    public void debounceBoundaryIsExactlyTheConfiguredWindow()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        // exactly debounceMs later: elapsed is NOT strictly less -> push
        mClock.time += mEngine.getDebounceMs();
        mEngine.onState("KY", "Kentucky");

        assertEquals(2, mGateway.pushes.size());
        assertEquals("K off", mGateway.pushes.get(1));
    }

    @Test
    public void pendingRuleIsDroppedWhenLeavingTheStateBeforeItFired()
    {
        mGateway.connected = false;

        mEngine.onState("OH", "Ohio");
        assertTrue(mEngine.hasPendingPush());

        // cross into a state without a rule while still disconnected
        mEngine.onState("IN", "Indiana");
        assertFalse("stale pending rule must die on state change", mEngine.hasPendingPush());

        // reconnecting must not resurrect the OH push
        mGateway.connected = true;
        mEngine.onState("IN", "Indiana");
        assertEquals(0, mGateway.pushes.size());
    }

    @Test
    public void nullRuleMapMeansNoPushesAnywhere()
    {
        mEngine.setRules(null);

        mEngine.onState("OH", "Ohio");
        mEngine.onState("KY", "Kentucky");

        assertEquals(0, mGateway.pushes.size());
        assertFalse(mEngine.hasPendingPush());
    }

    @Test
    public void resetReArmsTheCurrentState()
    {
        mEngine.onState("OH", "Ohio");
        assertEquals(1, mGateway.pushes.size());

        mClock.time += 120_000;
        mEngine.reset();

        // the same state now looks like a fresh state change
        mEngine.onState("OH", "Ohio");
        assertEquals(2, mGateway.pushes.size());
    }

    @Test
    public void currentStateTracksTransitionsIncludingNull()
    {
        assertNull(mEngine.getCurrentState());

        mEngine.onState("OH", "Ohio");
        assertEquals("OH", mEngine.getCurrentState());

        mClock.time += 120_000;
        mEngine.onState(null, null);
        assertNull(mEngine.getCurrentState());

        mClock.time += 120_000;
        mEngine.onState("KY", "Kentucky");
        assertEquals("KY", mEngine.getCurrentState());
        assertEquals(2, mGateway.pushes.size());
    }

    @Test
    public void deferralReasonIsLoggedOncePerCause()
    {
        mGateway.connected = false;

        mEngine.onState("OH", "Ohio");
        mEngine.onState("OH", "Ohio");
        mEngine.onState("OH", "Ohio");

        int deferrals = 0;
        for(String l : mGateway.logs)
        {
            if(l.contains("deferred: V1 not connected"))
                deferrals++;
        }

        assertEquals("the same skip reason must be logged once, not per fix", 1, deferrals);
    }
}
