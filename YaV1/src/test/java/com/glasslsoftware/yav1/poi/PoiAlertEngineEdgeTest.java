package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * [QA] Component tests for PoiAlertEngine edge cases beyond
 * PoiAlertEngineTest: null index, reset semantics, escalation gating while
 * stationary, passed-POI silence and the exact bearing-cone boundary.
 */
public class PoiAlertEngineEdgeTest
{
    private static final double POI_LAT = 28.6000;
    private static final double POI_LON = -81.3800;

    private static final double RADIUS   = 500;
    private static final long   RESET_MS = 3 * 60 * 1000L;

    private Poi            mPoi;
    private PoiGridIndex   mIndex;
    private PoiAlertEngine mEngine;

    @Before
    public void setUp()
    {
        mPoi    = new Poi(POI_LAT, POI_LON, "1", 50, "");
        mIndex  = PoiGridIndex.build(Arrays.asList(mPoi));
        mEngine = new PoiAlertEngine(RADIUS, 60, 3.0, RESET_MS);
    }

    private List<PoiAlertEngine.Alert> drive(double metersSouth, double speedMs, long nowMs)
    {
        double lat = POI_LAT - metersSouth / 111320.0;
        return mEngine.update(lat, POI_LON, 0, speedMs, nowMs, mIndex);
    }

    @Test
    public void nullIndexYieldsNoAlertsAndNoCrash()
    {
        assertEquals(0, mEngine.update(POI_LAT, POI_LON, 0, 15.0, 0, null).size());
    }

    @Test
    public void resetForgetsApproachesAndReAlertsImmediately()
    {
        assertEquals(1, drive(445, 15.0, 0).size());
        assertEquals(0, drive(400, 15.0, 1000).size());

        mEngine.reset();

        List<PoiAlertEngine.Alert> a = drive(400, 15.0, 2000);
        assertEquals(1, a.size());
        assertEquals(PoiAlertEngine.STAGE_APPROACH, a.get(0).stage);
    }

    @Test
    public void noCloseEscalationWhileStopped()
    {
        assertEquals(1, drive(445, 15.0, 0).size());

        // rolled to a stop below half distance: CLOSE must wait
        assertEquals(0, drive(200, 1.0, 5000).size());
        assertEquals(0, drive(200, 0.0, 10000).size());

        // moving again: CLOSE fires now
        List<PoiAlertEngine.Alert> a = drive(200, 10.0, 15000);
        assertEquals(1, a.size());
        assertEquals(PoiAlertEngine.STAGE_CLOSE, a.get(0).stage);
    }

    @Test
    public void passedPoiNeverEscalatesFromBehind()
    {
        assertEquals(1, drive(445, 15.0, 0).size());

        // 100m PAST the camera, still heading away: within half the first
        // distance but far out of the forward cone -> silent
        double lat = POI_LAT + 100 / 111320.0;
        assertEquals(0, mEngine.update(lat, POI_LON, 0, 15.0, 5000, mIndex).size());
        assertEquals(0, mEngine.update(lat + 100 / 111320.0, POI_LON, 0, 15.0, 10000, mIndex).size());
    }

    @Test
    public void coneBoundaryIsInclusive()
    {
        // POI dead ahead (bearing 0), car heading exactly coneDeg (60): alert
        List<PoiAlertEngine.Alert> a = mEngine.update(
            POI_LAT - 300 / 111320.0, POI_LON, 60, 15.0, 0, mIndex);
        assertEquals(1, a.size());

        // fresh engine, heading 61: outside the cone
        PoiAlertEngine other = new PoiAlertEngine(RADIUS, 60, 3.0, RESET_MS);
        assertEquals(0, other.update(
            POI_LAT - 300 / 111320.0, POI_LON, 61, 15.0, 0, mIndex).size());
    }

    @Test
    public void shrinkingTheRadiusMidDriveSilencesFartherPois()
    {
        mEngine.setRadius(100);
        assertEquals(100.0, mEngine.getRadius(), 0.0);

        // 300m out is beyond the shrunken radius
        assertEquals(0, drive(300, 15.0, 0).size());
        // inside it: alert
        assertEquals(1, drive(80, 15.0, 1000).size());
    }
}
