package com.glasslsoftware.yav1.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.geo.GeoProfileEngine;
import com.glasslsoftware.yav1.geo.GeoRuleStore;
import com.glasslsoftware.yav1.geo.StateResolver;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [QA][integration] Geo profile switching, end to end on real
 * collaborators and the real shipped us_states.json dataset:
 *
 *   GPS fix -> StateResolver (offline polygons + hysteresis cache)
 *     -> persisted rules via GeoRuleStore JSON -> GeoProfileEngine
 *     -> profile push / announce through a fake V1 gateway
 */
public class GeoProfileSwitchingIntegrationTest
{
    private static StateResolver sDataset;

    @BeforeClass
    public static void loadDataset() throws IOException
    {
        String[] candidates = {
            "src/main/assets/geo/us_states.json",
            "YaV1/src/main/assets/geo/us_states.json",
        };

        for(String c : candidates)
        {
            File f = new File(c);
            if(f.isFile())
            {
                FileInputStream in = new FileInputStream(f);
                try
                {
                    sDataset = StateResolver.fromStream(in);
                }
                finally
                {
                    in.close();
                }
                return;
            }
        }

        throw new IOException("us_states.json asset not found from " + new File(".").getAbsolutePath());
    }

    /** controllable fake of the V1 push machinery */
    private static class FakeGateway implements GeoProfileEngine.Gateway
    {
        boolean connected  = true;
        boolean demo       = false;
        boolean alertQuiet = true;

        int currentSettingId = -1;
        int nextPushedId     = 100;

        final List<String> pushes    = new ArrayList<String>();
        final List<String> announces = new ArrayList<String>();

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

        @Override
        public void announce(String profileName, String stateName)
        {
            announces.add(profileName + "|" + stateName);
        }

        @Override public void log(String msg) { }
    }

    private static class FakeClock implements GeoProfileEngine.Clock
    {
        long time = 1_000_000L;
        @Override public long now() { return time; }
    }

    private StateResolver    mResolver;
    private FakeGateway      mGateway;
    private FakeClock        mClock;
    private GeoProfileEngine mEngine;

    @Before
    public void setUp()
    {
        mResolver = new StateResolver(sDataset.getStates());
        mGateway  = new FakeGateway();
        mClock    = new FakeClock();
        mEngine   = new GeoProfileEngine(mGateway, mClock);

        // rules exactly as persisted in SharedPreferences
        Map<String, String> rules = GeoRuleStore.fromJson(
            "{\"OH\":\"Ohio highway\",\"KY\":\"Bluegrass\"}");
        assertEquals(2, rules.size());
        mEngine.setRules(rules);
    }

    /** one GPS fix through the full resolve -> rule -> push chain */
    private void fix(double lat, double lon)
    {
        String code = mResolver.resolve(lat, lon);
        mEngine.onState(code, mResolver.nameOf(code));
    }

    @Test
    public void crossingTheOhioRiverSwitchesProfiles()
    {
        // Cincinnati riverfront: Ohio
        fix(39.1031, -84.5120);

        assertEquals(1, mGateway.pushes.size());
        assertEquals("Ohio highway", mGateway.pushes.get(0));
        assertEquals("Ohio highway|Ohio", mGateway.announces.get(0));

        // over the river to Covington (~2.2km, beyond the 2km hysteresis)
        mClock.time += 120_000;
        fix(39.0837, -84.5086);

        assertEquals(2, mGateway.pushes.size());
        assertEquals("Bluegrass", mGateway.pushes.get(1));
        assertEquals("Bluegrass|Kentucky", mGateway.announces.get(1));
    }

    @Test
    public void drivingAroundTownNeitherReResolvesNorRePushes()
    {
        fix(39.9612, -82.9988);            // Columbus
        assertEquals(1, mGateway.pushes.size());

        int resolves = mResolver.getResolveCount();

        // short hops around downtown: resolver serves from cache, engine
        // sees the same state and stays quiet
        mClock.time += 120_000;
        fix(39.9640, -82.9990);
        mClock.time += 120_000;
        fix(39.9612, -82.9900);

        assertEquals("no re-resolution within the hysteresis radius",
                     resolves, mResolver.getResolveCount());
        assertEquals("no repeat pushes while staying in Ohio",
                     1, mGateway.pushes.size());
    }

    @Test
    public void alertOnScreenDefersThePushUntilQuiet()
    {
        fix(39.9612, -82.9988);            // Columbus: pushed
        assertEquals(1, mGateway.pushes.size());

        // an alert starts just as we cross into Kentucky
        mGateway.alertQuiet = false;
        mClock.time += 120_000;
        fix(39.0837, -84.5086);            // Covington

        assertEquals("must never push mid-alert", 1, mGateway.pushes.size());
        assertTrue(mEngine.hasPendingPush());

        // alert over: the pending switch fires on the next fix in KY
        mGateway.alertQuiet = true;
        fix(39.0830, -84.5080);

        assertEquals(2, mGateway.pushes.size());
        assertEquals("Bluegrass", mGateway.pushes.get(1));
        assertFalse(mEngine.hasPendingPush());
    }

    @Test
    public void openWaterMatchesNoStateAndPushesNothing()
    {
        fix(42.2000, -81.5000);            // Lake Erie

        assertEquals(0, mGateway.pushes.size());
        assertFalse(mEngine.hasPendingPush());

        // sailing back ashore into Ohio picks the rule up again
        mClock.time += 120_000;
        fix(41.4993, -81.6944);            // Cleveland

        assertEquals(1, mGateway.pushes.size());
        assertEquals("Ohio highway", mGateway.pushes.get(0));
    }

    @Test
    public void stateWithoutARuleIsAnnouncedToNobody()
    {
        // Indianapolis: no IN rule configured
        fix(39.7684, -86.1581);

        assertEquals(0, mGateway.pushes.size());
        assertEquals(0, mGateway.announces.size());
        assertEquals("IN", mEngine.getCurrentState());
    }
}
