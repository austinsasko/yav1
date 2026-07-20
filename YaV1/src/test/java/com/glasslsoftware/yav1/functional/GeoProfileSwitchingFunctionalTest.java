package com.glasslsoftware.yav1.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.geo.GeoProfileEngine;
import com.glasslsoftware.yav1.geo.StateResolver;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [QA-FUNC] Geo profile switching, end to end on the real shipped state
 * dataset (assets/geo/us_states.json): a simulated drive across a state
 * boundary flows through StateResolver (offline point-in-polygon) into
 * GeoProfileEngine, and the assertions are the profile pushes and TTS
 * announcements the driver would get.
 */
public class GeoProfileSwitchingFunctionalTest
{
    private static StateResolver sDataset;

    // I-71/75 crossing of the Ohio river: Cincinnati OH -> Covington KY
    private static final double[][] DRIVE_OH_TO_KY = {
        {39.1400, -84.5200},   // north Cincinnati, OH
        {39.1200, -84.5150},   // downtown Cincinnati, OH
        {39.1031, -84.5120},   // riverfront, OH
        {39.0837, -84.5086},   // Covington, KY
        {39.0500, -84.5080},   // further south, KY
    };

    @BeforeClass
    public static void loadDataset() throws IOException
    {
        FileInputStream in = new FileInputStream(RepoFile.find("src/main/assets/geo/us_states.json"));
        try
        {
            sDataset = StateResolver.fromStream(in);
        }
        finally
        {
            in.close();
        }
    }

    /** Recording gateway: connected, live, quiet unless told otherwise. */
    private static class Gate implements GeoProfileEngine.Gateway
    {
        boolean connected  = true;
        boolean demo       = false;
        boolean quiet      = true;
        int     settingId  = -1;
        int     nextId     = 100;

        final List<String> pushes    = new ArrayList<String>();
        final List<String> announced = new ArrayList<String>();

        public boolean isConnected()          { return connected; }
        public boolean isDemo()               { return demo; }
        public boolean isAlertQuiet()         { return quiet; }
        public int     getCurrentSettingId()  { return settingId; }
        public void    log(String msg)        { }

        public boolean pushProfile(String profileName)
        {
            pushes.add(profileName);
            settingId = nextId++;
            return true;
        }

        public void announce(String profileName, String stateName)
        {
            announced.add(profileName + " for " + stateName);
        }
    }

    private Gate             mGate;
    private long             mNow;
    private GeoProfileEngine mEngine;
    private StateResolver    mResolver;

    @Before
    public void setUp()
    {
        mGate     = new Gate();
        mNow      = 1_000_000L;
        mEngine   = new GeoProfileEngine(mGate, new GeoProfileEngine.Clock()
        {
            public long now() { return mNow; }
        });
        // fresh resolver over the shared dataset; re-resolve every 100 m so
        // a city-block drive re-evaluates the state at each fix
        mResolver = new StateResolver(sDataset.getStates());
        mResolver.setReResolveDistanceMeters(100);

        Map<String, String> rules = new HashMap<String, String>();
        rules.put("OH", "Ohio Highway");
        rules.put("KY", "Kentucky");
        mEngine.setRules(rules);
    }

    /** one GPS fix: resolve then feed the engine, like GeoProfileManager */
    private String fix(double lat, double lon)
    {
        String code = mResolver.resolve(lat, lon);
        mEngine.onState(code, mResolver.nameOf(code));
        return code;
    }

    @Test
    public void datasetResolvesTheRiverCrossing()
    {
        assertEquals("OH", mResolver.resolveUncached(39.1031, -84.5120));
        assertEquals("KY", mResolver.resolveUncached(39.0837, -84.5086));
    }

    @Test
    public void crossingTheStateLinePushesTheMappedProfile()
    {
        mEngine.setDebounceMs(0);   // isolate the switching logic from timing

        for(double[] p : DRIVE_OH_TO_KY)
            fix(p[0], p[1]);

        // exactly two pushes: entering OH (first fix) and entering KY
        assertEquals(2, mGate.pushes.size());
        assertEquals("Ohio Highway", mGate.pushes.get(0));
        assertEquals("Kentucky", mGate.pushes.get(1));

        // each announced with the full state name
        assertEquals(2, mGate.announced.size());
        assertEquals("Ohio Highway for Ohio", mGate.announced.get(0));
        assertEquals("Kentucky for Kentucky", mGate.announced.get(1));
    }

    @Test
    public void debounceDefersTheBorderPushThenDeliversIt()
    {
        mEngine.setDebounceMs(60_000);

        // drive to the river: OH profile pushed immediately (first push is
        // not debounced)
        fix(39.1400, -84.5200);
        fix(39.1031, -84.5120);
        assertEquals(1, mGate.pushes.size());

        // cross into KY 10 s later: rule matches but the debounce window is
        // still open, so the push stays pending
        mNow += 10_000;
        fix(39.0837, -84.5086);
        assertEquals(1, mGate.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        // a minute down the road the pending push is delivered
        mNow += 60_000;
        fix(39.0500, -84.5080);
        assertEquals(2, mGate.pushes.size());
        assertEquals("Kentucky", mGate.pushes.get(1));
    }

    @Test
    public void pushWaitsForTheAlertToClear()
    {
        mEngine.setDebounceMs(0);
        fix(39.1400, -84.5200);
        assertEquals(1, mGate.pushes.size());

        // a Ka alert is on screen while we cross the river
        mGate.quiet = false;
        fix(39.0837, -84.5086);
        assertEquals("no profile push while an alert is showing", 1, mGate.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        // alert over: the very next fix delivers the pending push
        mGate.quiet = true;
        fix(39.0500, -84.5080);
        assertEquals(2, mGate.pushes.size());
    }

    @Test
    public void neverPushesWhileDisconnectedOrInDemoMode()
    {
        mEngine.setDebounceMs(0);

        mGate.connected = false;
        fix(39.1400, -84.5200);
        assertEquals(0, mGate.pushes.size());

        mGate.connected = true;
        mGate.demo      = true;
        fix(39.0837, -84.5086);
        assertEquals(0, mGate.pushes.size());
    }

    @Test
    public void stateWithoutARuleSwitchesNothing()
    {
        mEngine.setDebounceMs(0);

        // Indianapolis: dataset resolves IN, but no rule is configured
        String code = fix(39.7684, -86.1581);
        assertEquals("IN", code);
        assertEquals(0, mGate.pushes.size());
        assertEquals(0, mGate.announced.size());
    }

    @Test
    public void offshorePositionResolvesNoStateAndSwitchesNothing()
    {
        mEngine.setDebounceMs(0);

        String code = fix(42.2000, -81.5000);   // Lake Erie
        assertNull(code);
        assertEquals(0, mGate.pushes.size());
    }

    @Test
    public void drivingAroundTownDoesNotRepush()
    {
        mEngine.setDebounceMs(0);
        fix(39.1400, -84.5200);
        assertEquals(1, mGate.pushes.size());

        // wandering around Cincinnati: same state on every fix
        fix(39.1350, -84.5100);
        fix(39.1300, -84.5000);
        fix(39.1250, -84.5150);
        assertEquals(1, mGate.pushes.size());
    }

    @Test
    public void manualPushOnTheV1SuspendsAutoSwitchingUntilTheNextBorder()
    {
        mEngine.setDebounceMs(0);
        fix(39.1400, -84.5200);
        assertEquals(1, mGate.pushes.size());

        // the user pushes a different profile by hand (setting id changes
        // outside the engine)
        mGate.settingId = 9999;
        fix(39.1350, -84.5100);
        assertTrue(mEngine.isManualOverride());

        // crossing into KY clears the override and switches again
        fix(39.0837, -84.5086);
        assertEquals(2, mGate.pushes.size());
        assertEquals("Kentucky", mGate.pushes.get(1));
    }

    @Test
    public void resolverCachesShortMovements()
    {
        mResolver.setReResolveDistanceMeters(2000);
        int before = mResolver.getResolveCount();

        mResolver.resolve(39.1400, -84.5200);
        mResolver.resolve(39.1401, -84.5201);   // a few meters away
        mResolver.resolve(39.1402, -84.5199);

        assertEquals("small movements must reuse the cached state",
                     before + 1, mResolver.getResolveCount());
    }
}
