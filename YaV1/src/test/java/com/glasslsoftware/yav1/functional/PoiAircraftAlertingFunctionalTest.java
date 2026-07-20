package com.glasslsoftware.yav1.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.aircraft.Aircraft;
import com.glasslsoftware.yav1.aircraft.AdsbParser;
import com.glasslsoftware.yav1.aircraft.AircraftTracker;
import com.glasslsoftware.yav1.aircraft.EnforcementWatchlist;
import com.glasslsoftware.yav1.poi.Poi;
import com.glasslsoftware.yav1.poi.PoiAlertEngine;
import com.glasslsoftware.yav1.poi.PoiCsvParser;
import com.glasslsoftware.yav1.poi.PoiGridIndex;
import com.glasslsoftware.yav1.poi.PoiPhrases;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

/**
 * [QA-FUNC] POI (camera) and aircraft alerting, fixture data in ->
 * announcement decisions out:
 *
 *  - a camera CSV import driven through parser -> grid index -> approach
 *    state machine -> spoken phrase, on a simulated drive-by,
 *  - the shipped FAA enforcement watchlist (assets/aircraft/
 *    enforcement_hex.csv) and a recorded ADS-B point payload driven through
 *    parser -> watchlist match -> behavior assessment -> alert throttling.
 */
public class PoiAircraftAlertingFunctionalTest
{
    // ------------------------------------------------------------------ POI

    /** camera 1.11 km north of the start point, on the driving line */
    private static final String CSV =
        "28.6100,-81.3800,1,50,I-4 speed camera\n" +
        "28.6100,-81.4300,3,0,\n";              // red light cam far to the west

    private static PoiGridIndex index() throws IOException
    {
        PoiCsvParser.Result r = PoiCsvParser.parse(new StringReader(CSV));
        assertEquals(2, r.pois.size());
        return PoiGridIndex.build(r.pois);
    }

    @Test
    public void driveByAlertsApproachThenCloseThenGoesQuiet() throws IOException
    {
        PoiGridIndex idx = index();
        // 800 m radius, 60 degree cone, alert only when moving >= 5 m/s
        PoiAlertEngine engine = new PoiAlertEngine(800, 60, 5.0, 60000);

        long   now = 0;
        double lat = 28.6000;   // ~1.1 km south of the camera
        double lon = -81.3800;

        List<PoiAlertEngine.Alert> out;
        PoiAlertEngine.Alert approach = null;
        PoiAlertEngine.Alert close    = null;

        // drive north at ~25 m/s, one fix per second
        for(int i = 0; i < 40; i++)
        {
            out = engine.update(lat, lon, 0.0, 25.0, now, idx);

            for(PoiAlertEngine.Alert a : out)
            {
                if(a.stage == PoiAlertEngine.STAGE_APPROACH)
                {
                    assertNull("approach must fire exactly once", approach);
                    approach = a;
                }
                else if(a.stage == PoiAlertEngine.STAGE_CLOSE)
                {
                    assertNull("close must fire exactly once", close);
                    assertNotNull("close only after approach", approach);
                    close = a;
                }
            }

            lat += 0.000225;    // ~25 m north
            now += 1000;
        }

        assertNotNull("driving straight at the camera must alert", approach);
        assertEquals("I-4 speed camera", approach.poi.name);
        assertTrue("first alert inside the radius", approach.distanceM <= 800);

        assertNotNull("the escalation alert must fire", close);
        assertTrue("escalates at half the first-alert distance",
                   close.distanceM <= approach.distanceM / 2.0);

        // the red light camera to the west never alerted (outside the cone)
        // - implicitly verified by the exactly-once assertions above
    }

    @Test
    public void alertPhrasesAreSpeakableInBothUnitSystems() throws IOException
    {
        PoiCsvParser.Result r = PoiCsvParser.parse(new StringReader(CSV));
        Poi cam = r.pois.get(0);

        assertEquals("I-4 speed camera ahead, 750 meters",
                     PoiPhrases.alertPhrase(cam, 760, PoiAlertEngine.STAGE_APPROACH,
                                            PoiPhrases.UNIT_METRIC));
        assertEquals("I-4 speed camera, 400 feet",
                     PoiPhrases.alertPhrase(cam, 120, PoiAlertEngine.STAGE_CLOSE,
                                            PoiPhrases.UNIT_IMPERIAL));

        // a nameless POI falls back to a speakable type label
        Poi redLight = r.pois.get(1);
        assertTrue(redLight.name.isEmpty());
        assertEquals("Red light camera",
                     PoiPhrases.alertPhrase(redLight, 500, PoiAlertEngine.STAGE_APPROACH,
                                            PoiPhrases.UNIT_METRIC).split(" ahead")[0]);
    }

    @Test
    public void stationaryVehicleNeverGetsPoiAlerts() throws IOException
    {
        PoiAlertEngine engine = new PoiAlertEngine(800, 60, 5.0, 60000);

        // parked 300 m south of the camera, engine idling
        for(int i = 0; i < 10; i++)
            assertTrue(engine.update(28.6073, -81.3800, 0.0, 0.0, i * 1000L, index()).isEmpty());
    }

    // ------------------------------------------------------------- aircraft

    private static EnforcementWatchlist loadShippedWatchlist() throws IOException
    {
        EnforcementWatchlist wl = new EnforcementWatchlist();
        BufferedReader br = new BufferedReader(new FileReader(
            RepoFile.find("src/main/assets/aircraft/enforcement_hex.csv")));
        try
        {
            wl.load(br);
        }
        finally
        {
            br.close();
        }
        return wl;
    }

    private static String fixture(String name) throws IOException
    {
        InputStream in = PoiAircraftAlertingFunctionalTest.class.getResourceAsStream(name);
        assertNotNull("fixture " + name + " missing", in);

        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null)
            sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    @Test
    public void shippedWatchlistLoads() throws IOException
    {
        EnforcementWatchlist wl = loadShippedWatchlist();
        assertTrue("expected the enriched 330-entry watchlist, got " + wl.size(),
                   wl.size() >= 300);
    }

    @Test
    public void recordedPayloadFlagsTheHighwayPatrolAircraft() throws IOException
    {
        EnforcementWatchlist wl      = loadShippedWatchlist();
        AircraftTracker      tracker = new AircraftTracker();
        long                 now     = 1_000_000L;

        List<Aircraft> acs = AdsbParser.parse(fixture("/aircraft/adsb_point_sample.json"));
        assertTrue(acs.size() >= 2);

        boolean alerted = false;

        for(Aircraft ac : acs)
        {
            EnforcementWatchlist.Entry hit = wl.match(ac);
            AircraftTracker.Assessment as  = tracker.assess(ac, hit != null, now);

            if("a255e2".equalsIgnoreCase(ac.hex))
            {
                // N25HP, Florida Highway Patrol Cessna 182T, airborne at
                // 1800 ft / 95 kt: a confident watchlist alert
                assertNotNull("FHP airframe must match the shipped watchlist", hit);
                assertEquals("Florida Highway Patrol", hit.agency);
                assertFalse("FAA-sourced entry is high confidence", hit.lowConfidence());
                assertEquals(AircraftTracker.CAT_WATCHLIST, as.category);
                assertTrue(tracker.shouldAlert(ac, now));
                alerted = true;
            }
            else if(ac.onGround)
            {
                // taxiing aircraft never alert, watchlisted or not
                assertEquals(AircraftTracker.CAT_NONE, as.category);
            }
        }

        assertTrue(alerted);
    }

    @Test
    public void watchlistAlertsAreThrottledPerAircraft() throws IOException
    {
        AircraftTracker tracker = new AircraftTracker();
        long            now     = 1_000_000L;

        Aircraft ac = new Aircraft();
        ac.hex   = "a255e2";
        ac.lat   = 28.55;
        ac.lon   = -81.36;
        ac.altFt = 1800;
        ac.gsKt  = 95;

        tracker.assess(ac, true, now);
        assertTrue(tracker.shouldAlert(ac, now));

        // seen again on the next polls: still tracked, but silent
        tracker.assess(ac, true, now + 30_000);
        assertFalse(tracker.shouldAlert(ac, now + 30_000));

        // after the cooldown it may alert again
        long later = now + AircraftTracker.ALERT_COOLDOWN_MS + 1;
        tracker.assess(ac, true, later);
        assertTrue(tracker.shouldAlert(ac, later));
    }

    @Test
    public void orbitingLowSlowUnknownAircraftTripsTheHeuristic()
    {
        AircraftTracker tracker = new AircraftTracker();
        long            now     = 0;

        Aircraft ac = new Aircraft();
        ac.hex   = "abcdef";        // not on any watchlist
        ac.lat   = 28.5;
        ac.lon   = -81.3;
        ac.altFt = 1500;
        ac.gsKt  = 80;

        // straight-line flight first: low and slow but no loitering
        double[] straight = {10, 11, 12, 13};
        AircraftTracker.Assessment as = null;
        for(int i = 0; i < straight.length; i++)
        {
            ac.trackDeg = straight[i];
            as = tracker.assess(ac, false, now += 30_000);
        }
        assertTrue(as.lowSlow);
        assertFalse(as.loiter);
        assertEquals(AircraftTracker.CAT_NONE, as.category);

        // then it starts orbiting: heading sweeps through a full circle
        double[] orbit = {90, 170, 250, 330, 50};
        for(int i = 0; i < orbit.length; i++)
        {
            ac.trackDeg = orbit[i];
            as = tracker.assess(ac, false, now += 30_000);
        }
        assertTrue(as.loiter);
        assertEquals(AircraftTracker.CAT_HEURISTIC, as.category);
        assertTrue(tracker.shouldAlert(ac, now));
    }

    @Test
    public void airlinerOverheadNeverTripsTheHeuristic()
    {
        AircraftTracker tracker = new AircraftTracker();
        long            now     = 0;

        Aircraft ac = new Aircraft();
        ac.hex   = "a11111";
        ac.lat   = 28.5;
        ac.lon   = -81.3;
        ac.altFt = 35000;           // way above the ceiling
        ac.gsKt  = 450;             // way above the speed gate

        double[] orbit = {0, 90, 180, 270, 0};
        AircraftTracker.Assessment as = null;
        for(int i = 0; i < orbit.length; i++)
        {
            ac.trackDeg = orbit[i];
            as = tracker.assess(ac, false, now += 30_000);
        }

        assertFalse(as.lowSlow);
        assertEquals(AircraftTracker.CAT_NONE, as.category);
    }
}
