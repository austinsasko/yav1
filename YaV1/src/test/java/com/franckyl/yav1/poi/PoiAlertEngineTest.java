package com.franckyl.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Scenario: a road running due north, one speed camera on it, the car
 * approaching from the south with bearing 0.
 */
public class PoiAlertEngineTest
{
    private static final double POI_LAT = 28.6000;
    private static final double POI_LON = -81.3800;

    private static final double RADIUS   = 500;
    private static final long   RESET_MS = 3 * 60 * 1000L;

    private Poi           mPoi;
    private PoiGridIndex  mIndex;
    private PoiAlertEngine mEngine;

    @Before
    public void setUp()
    {
        mPoi    = new Poi(POI_LAT, POI_LON, "1", 50, "");
        mIndex  = PoiGridIndex.build(Arrays.asList(mPoi));
        mEngine = new PoiAlertEngine(RADIUS, 60, 3.0, RESET_MS);
    }

    // car at "meters" south of the POI, driving north at 15 m/s
    private List<PoiAlertEngine.Alert> drive(double metersSouth, long nowMs)
    {
        double lat = POI_LAT - metersSouth / 111320.0;
        return mEngine.update(lat, POI_LON, 0, 15.0, nowMs, mIndex);
    }

    @Test
    public void approachAlertsOnceThenEscalatesAtHalfDistance()
    {
        // outside radius: nothing
        assertEquals(0, drive(700, 0).size());

        // first fix inside the radius: APPROACH alert
        List<PoiAlertEngine.Alert> a = drive(445, 1000);
        assertEquals(1, a.size());
        assertEquals(PoiAlertEngine.STAGE_APPROACH, a.get(0).stage);
        assertEquals(445, a.get(0).distanceM, 15);

        // closer but above half distance: silent
        assertEquals(0, drive(334, 2000).size());

        // below half of the first-alert distance: CLOSE alert
        a = drive(210, 3000);
        assertEquals(1, a.size());
        assertEquals(PoiAlertEngine.STAGE_CLOSE, a.get(0).stage);

        // even closer: silent (cooldown for this approach)
        assertEquals(0, drive(100, 4000).size());
        assertEquals(0, drive(30, 5000).size());
    }

    @Test
    public void poiBehindDoesNotAlert()
    {
        // car north of the POI driving further north: POI is behind (bearing 180)
        double lat = POI_LAT + 300 / 111320.0;
        List<PoiAlertEngine.Alert> a = mEngine.update(lat, POI_LON, 0, 15.0, 0, mIndex);
        assertEquals(0, a.size());
    }

    @Test
    public void poiToTheSideOutsideConeDoesNotAlert()
    {
        // POI dead ahead but car heading east (90): angle 90 > 60 cone
        List<PoiAlertEngine.Alert> a = mEngine.update(
                POI_LAT - 300 / 111320.0, POI_LON, 90, 15.0, 0, mIndex);
        assertEquals(0, a.size());
    }

    @Test
    public void notMovingDoesNotAlert()
    {
        double lat = POI_LAT - 300 / 111320.0;
        List<PoiAlertEngine.Alert> a = mEngine.update(lat, POI_LON, 0, 1.0, 0, mIndex);
        assertEquals(0, a.size());

        // starts moving: alert fires now
        a = mEngine.update(lat, POI_LON, 0, 10.0, 1000, mIndex);
        assertEquals(1, a.size());
    }

    @Test
    public void repeatedFixesAtSameSpotAlertOnce()
    {
        assertEquals(1, drive(400, 0).size());
        assertEquals(0, drive(400, 1000).size());
        assertEquals(0, drive(399, 2000).size());
    }

    @Test
    public void reApproachAfterResetAlertsAgain()
    {
        // first approach
        assertEquals(1, drive(445, 0).size());
        assertEquals(1, drive(210, 1000).size());

        // drive far away (updates keep coming from out of range)
        long t = 2000;
        assertEquals(0, drive(5000, t).size());

        // after the reset window the state is dropped
        t += RESET_MS + 1000;
        assertEquals(0, drive(5000, t).size());   // triggers cleanup

        // second approach alerts again
        List<PoiAlertEngine.Alert> a = drive(445, t + 1000);
        assertEquals(1, a.size());
        assertEquals(PoiAlertEngine.STAGE_APPROACH, a.get(0).stage);
    }

    @Test
    public void stateSurvivesShortGapsOutOfConeOrRadius()
    {
        // alert on approach
        assertEquals(1, drive(445, 0).size());

        // just past the POI (in the outer band, out of cone): no new alert
        double lat = POI_LAT + 300 / 111320.0;
        assertEquals(0, mEngine.update(lat, POI_LON, 0, 15.0, 5000, mIndex).size());

        // turn around within the reset window and approach again: still silent
        // (same approach, state alive)
        assertEquals(0, drive(400, 10000).size());
    }

    @Test
    public void multiplePoisAlertIndependently()
    {
        Poi second = new Poi(POI_LAT + 0.01, POI_LON, "3", 0, "");   // ~1.1 km further north
        mIndex = PoiGridIndex.build(Arrays.asList(mPoi, second));

        // approach first POI
        List<PoiAlertEngine.Alert> a = drive(445, 0);
        assertEquals(1, a.size());
        assertTrue(a.get(0).poi == mPoi);

        // now approach the second one (car is between them)
        double lat = second.lat - 445 / 111320.0;
        a = mEngine.update(lat, POI_LON, 0, 15.0, 60000, mIndex);
        assertEquals(1, a.size());
        assertTrue(a.get(0).poi == second);
    }
}
