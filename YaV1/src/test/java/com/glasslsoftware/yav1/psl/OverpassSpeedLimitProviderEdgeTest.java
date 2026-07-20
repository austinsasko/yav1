package com.glasslsoftware.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * [QA] Component tests for OverpassSpeedLimitProvider parsing/selection
 * edges not covered by OverpassSpeedLimitProviderTest: maxspeed corner
 * values, partially malformed response elements, direction boundary of the
 * effective limit, cached-road revalidation thresholds and backoff codes.
 * No network involved.
 */
public class OverpassSpeedLimitProviderEdgeTest
{
    // -- parseMaxspeed corners -------------------------------------------

    @Test
    public void decimalMphAndShoutingUnitsParse()
    {
        assertEquals(Integer.valueOf(89), OverpassSpeedLimitProvider.parseMaxspeed("55.5 MPH"));
        assertEquals(Integer.valueOf(50), OverpassSpeedLimitProvider.parseMaxspeed("50 KM/H"));
        assertEquals(Integer.valueOf(30), OverpassSpeedLimitProvider.parseMaxspeed("  30KPH  "));
    }

    @Test
    public void compositeWithUnusableFirstComponentIsUnknown()
    {
        // only the FIRST component counts, even when a later one is numeric
        assertNull(OverpassSpeedLimitProvider.parseMaxspeed("signals;40"));
        assertNull(OverpassSpeedLimitProvider.parseMaxspeed(";40"));
        assertEquals(Integer.valueOf(40), OverpassSpeedLimitProvider.parseMaxspeed("40 ;signals"));
    }

    @Test
    public void scientificNotationAndUnitsWithoutNumbersRejected()
    {
        assertNull(OverpassSpeedLimitProvider.parseMaxspeed("1e2"));
        assertNull(OverpassSpeedLimitProvider.parseMaxspeed("mph"));
        assertNull(OverpassSpeedLimitProvider.parseMaxspeed("55 mphx"));
    }

    // -- partially malformed responses ------------------------------------

    @Test
    public void wayWithBrokenGeometryPointIsDroppedOthersSurvive()
    {
        String json =
            "{\"elements\":[" +
            "{\"type\":\"way\",\"id\":1," +
             "\"tags\":{\"maxspeed\":\"30 mph\"}," +
             "\"geometry\":[{\"lat\":28.0,\"lon\":-81.001},{\"lat\":28.0}]}," +   // point missing lon
            "{\"type\":\"way\",\"id\":2," +
             "\"tags\":{\"maxspeed\":\"40 mph\"}," +
             "\"geometry\":[{\"lat\":28.1,\"lon\":-81.001},{\"lat\":28.1,\"lon\":-80.999}]}" +
            "]}";

        List<OverpassSpeedLimitProvider.Way> ways = OverpassSpeedLimitProvider.parseWays(json);

        assertEquals(1, ways.size());
        assertEquals(Integer.valueOf(64), ways.get(0).limitKph);   // 40 mph
    }

    @Test
    public void nonObjectElementsAreSkippedNotFatal()
    {
        String json =
            "{\"elements\":[42,\"junk\"," +
            "{\"type\":\"way\",\"id\":2," +
             "\"tags\":{\"maxspeed\":\"80\"}," +
             "\"geometry\":[{\"lat\":28.0,\"lon\":-81.001},{\"lat\":28.0,\"lon\":-80.999}]}" +
            "]}";

        List<OverpassSpeedLimitProvider.Way> ways = OverpassSpeedLimitProvider.parseWays(json);
        assertEquals(1, ways.size());
        assertEquals(Integer.valueOf(80), ways.get(0).limitKph);
    }

    @Test
    public void wayWithoutTagsIsDropped()
    {
        String json =
            "{\"elements\":[{\"type\":\"way\",\"id\":1," +
             "\"geometry\":[{\"lat\":28.0,\"lon\":-81.001},{\"lat\":28.0,\"lon\":-80.999}]}]}";

        assertTrue(OverpassSpeedLimitProvider.parseWays(json).isEmpty());
    }

    // -- direction boundary ------------------------------------------------

    @Test
    public void ninetyDegreesOffStillCountsAsForward()
    {
        OverpassSpeedLimitProvider.Way w = new OverpassSpeedLimitProvider.Way(
            80, 89, 97, new double[] {0, 1}, new double[] {0, 0});

        // segment digitized northbound; exactly 90 deg off is "along"
        assertEquals(Integer.valueOf(89),
            OverpassSpeedLimitProvider.effectiveLimitKph(w, 90, 0));
        // just past 90: against
        assertEquals(Integer.valueOf(97),
            OverpassSpeedLimitProvider.effectiveLimitKph(w, 90.5, 0));
    }

    // -- cached-road revalidation thresholds -------------------------------

    private static SpeedLimitCache.Entry eastWestEntry()
    {
        return new SpeedLimitCache.Entry(48, 1000L,
            new double[] {28.0, 28.0}, new double[] {-81.001, -80.999});
    }

    @Test
    public void entryWithoutGeometryNeverMatches()
    {
        SpeedLimitCache.Entry bare = new SpeedLimitCache.Entry(48, 1000L);
        assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(bare, 28.0, -81.0, 90f));
        assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(null, 28.0, -81.0, 90f));
    }

    @Test
    public void bearingToleranceEndsAtThirtyDegrees()
    {
        // east-west road (great-circle bearing ~89.9997 at lat 28):
        // ~29.5 deg off still matches, ~31 deg off does not
        assertTrue(OverpassSpeedLimitProvider.cachedRoadMatches(eastWestEntry(), 28.0, -81.0, 119.5f));
        assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(eastWestEntry(), 28.0, -81.0, 121f));
    }

    @Test
    public void distanceToleranceEndsAtTwentyFiveMeters()
    {
        // ~24m north of the road: inside; ~30m: outside
        double deg24 = 24.0 / 111320.0;
        double deg30 = 30.0 / 111320.0;
        assertTrue(OverpassSpeedLimitProvider.cachedRoadMatches(eastWestEntry(), 28.0 + deg24, -81.0, 90f));
        assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(eastWestEntry(), 28.0 + deg30, -81.0, 90f));
    }

    // -- geometry degenerate cases ----------------------------------------

    @Test
    public void zeroLengthSegmentMeasuresDistanceToThePoint()
    {
        assertEquals(111.3, OverpassSpeedLimitProvider.distanceToSegmentM(
            0, 0.001, 0, 0, 0, 0), 1.0);
    }

    // -- HTTP status handling ----------------------------------------------

    @Test
    public void serverTroubleCodesBackOffOthersDoNot()
    {
        long now = 1000L;

        OverpassSpeedLimitProvider p = new OverpassSpeedLimitProvider(null);
        p.noteHttpStatus(503, now);
        assertTrue(p.isBackedOff(now + 1));

        p = new OverpassSpeedLimitProvider(null);
        p.noteHttpStatus(504, now);
        assertTrue(p.isBackedOff(now + 1));

        p = new OverpassSpeedLimitProvider(null);
        p.noteHttpStatus(500, now);
        assertFalse(p.isBackedOff(now + 1));

        p = new OverpassSpeedLimitProvider(null);
        p.noteHttpStatus(404, now);
        assertFalse(p.isBackedOff(now + 1));
    }

    // -- selection determinism ---------------------------------------------

    @Test
    public void identicalCandidatesKeepTheFirstListedWay()
    {
        double lats[] = {28.0, 28.0};
        double lons[] = {-81.001, -80.999};

        List<OverpassSpeedLimitProvider.Way> ways = new ArrayList<OverpassSpeedLimitProvider.Way>();
        ways.add(new OverpassSpeedLimitProvider.Way(48, lats, lons));
        ways.add(new OverpassSpeedLimitProvider.Way(80, lats, lons));

        // equal geometry: strict-less scoring keeps the first
        assertEquals(Integer.valueOf(48),
            OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));
    }
}
