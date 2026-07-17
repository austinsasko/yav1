package com.glasslsoftware.yav1.crowd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * [CSA] Waze feed parsing, staleness/dedup pruning, relay response parsing,
 * and the announce-relevance geometry — mirrors the iOS CrowdAlertTests so
 * both platforms behave identically.
 */
public class WazeFeedParserTest
{
    private static final String FEED = "{\"alerts\":["
        + "{\"uuid\":\"abc-1\",\"type\":\"POLICE\",\"subtype\":\"POLICE_VISIBLE\","
        + "\"location\":{\"x\":-104.99,\"y\":39.73},\"pubMillis\":1752750000000,"
        + "\"nThumbsUp\":3},"
        + "{\"uuid\":\"abc-2\",\"type\":\"ACCIDENT\",\"location\":{\"x\":-104.98,\"y\":39.74}},"
        + "{\"uuid\":\"abc-3\",\"type\":\"JAM\",\"location\":{\"x\":-104.97,\"y\":39.75}},"
        + "{\"uuid\":\"abc-4\",\"type\":\"HAZARD\",\"location\":{\"x\":-104.96,\"y\":39.76}}"
        + "],\"users\":[]}";

    @Test
    public void parsesPoliceAccidentHazardOnly()
    {
        List<CrowdAlert> alerts = WazeFeedParser.parse(FEED);

        assertEquals(3, alerts.size()); // JAM dropped
        assertEquals(CrowdAlert.KIND_POLICE, alerts.get(0).kind);
        assertEquals("abc-1", alerts.get(0).id);
        assertEquals(3, alerts.get(0).thumbsUp);
        assertEquals(39.73, alerts.get(0).lat, 1e-9);
        assertEquals(-104.99, alerts.get(0).lon, 1e-9);
        assertEquals(CrowdAlert.KIND_ACCIDENT, alerts.get(1).kind);
        assertEquals(CrowdAlert.KIND_HAZARD, alerts.get(2).kind);
    }

    @Test
    public void unparsableFeedIsEmptyNotFatal()
    {
        assertTrue(WazeFeedParser.parse("not json").isEmpty());
        assertTrue(WazeFeedParser.parse("{}").isEmpty());
    }

    @Test
    public void pruneDropsStaleAndDuplicateReports()
    {
        long now = System.currentTimeMillis();
        List<CrowdAlert> alerts = new ArrayList<CrowdAlert>();
        alerts.add(new CrowdAlert("fresh", CrowdAlert.KIND_POLICE, 0, 0,
                                  now - 10 * 60 * 1000L, 0, "waze"));
        alerts.add(new CrowdAlert("stale", CrowdAlert.KIND_POLICE, 0, 0,
                                  now - 45 * 60 * 1000L, 0, "waze"));
        alerts.add(new CrowdAlert("fresh", CrowdAlert.KIND_POLICE, 1, 1,
                                  now, 0, "yav1")); // duplicate id
        alerts.add(new CrowdAlert("undated", CrowdAlert.KIND_HAZARD, 0, 0,
                                  0, 0, "waze"));

        List<CrowdAlert> kept = WazeFeedParser.prune(alerts, now);

        assertEquals(2, kept.size());
        assertEquals("fresh", kept.get(0).id);
        assertEquals("undated", kept.get(1).id);
    }

    @Test
    public void relayResponseParses()
    {
        String json = "{\"reports\":[{\"id\":\"r1\",\"kind\":\"police\","
                    + "\"lat\":39.7,\"lon\":-104.9,\"at\":1752750000}]}";

        List<CrowdAlert> alerts = CrowdRelayClient.parseRelayResponse(json);

        assertEquals(1, alerts.size());
        assertEquals(CrowdAlert.KIND_POLICE, alerts.get(0).kind);
        assertEquals("yav1", alerts.get(0).source);
        assertEquals(1752750000000L, alerts.get(0).reportedAtMs);
    }

    // ------------------------------------------------------------ relevance

    @Test
    public void nearbyPoliceIsRelevantAnyDirection()
    {
        // ~500 m south of the vehicle, traveling north (away from it)
        assertTrue(CrowdMonitor.isRelevant(40.0, -75.0, 0, 39.9955, -75.0));
    }

    @Test
    public void distantPoliceNeedsTheTravelCone()
    {
        // ~3.3 km north; heading north = inside the cone
        assertTrue(CrowdMonitor.isRelevant(40.0, -75.0, 0, 40.03, -75.0));
        // same report, heading east = outside the cone
        assertFalse(CrowdMonitor.isRelevant(40.0, -75.0, 90, 40.03, -75.0));
        // unknown bearing: distant report stays quiet
        assertFalse(CrowdMonitor.isRelevant(40.0, -75.0, -1, 40.03, -75.0));
    }

    @Test
    public void beyondConeRangeNeverRelevant()
    {
        // ~8 km north, heading straight at it
        assertFalse(CrowdMonitor.isRelevant(40.0, -75.0, 0, 40.072, -75.0));
    }

    @Test
    public void geometryHelpersAreSane()
    {
        // 0.001 deg latitude ~= 111 m
        assertEquals(111.3, CrowdMonitor.distanceM(40.0, -75.0, 40.001, -75.0), 1.0);
        // due north / due east bearings
        assertEquals(0.0, CrowdMonitor.bearingDeg(40.0, -75.0, 40.01, -75.0), 0.5);
        assertEquals(90.0, CrowdMonitor.bearingDeg(40.0, -75.0, 40.0, -74.99), 1.0);
    }
}
