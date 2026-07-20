package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * [QA] Component tests for PoiGridIndex boundary behavior beyond
 * PoiGridIndexTest: polar latitude guard, zero radius, duplicate points
 * and cell-boundary points.
 */
public class PoiGridIndexEdgeTest
{
    private static Poi poi(double lat, double lon)
    {
        return new Poi(lat, lon, "1", 0, "");
    }

    @Test
    public void polarLatitudesDoNotBlowUpTheCellSpan()
    {
        PoiGridIndex idx = PoiGridIndex.build(Arrays.asList(poi(89.9, 0.0)));

        List<PoiGridIndex.Hit> hits = idx.queryRadius(89.9, 0.0, 1000);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).distanceM < 1.0);
    }

    @Test
    public void zeroRadiusFindsOnlyTheExactPoint()
    {
        PoiGridIndex idx = PoiGridIndex.build(Arrays.asList(
                poi(28.6000, -81.3800),
                poi(28.6001, -81.3800)));   // ~11m away

        List<PoiGridIndex.Hit> hits = idx.queryRadius(28.6000, -81.3800, 0);
        assertEquals(1, hits.size());
        assertEquals(0.0, hits.get(0).distanceM, 1e-6);
    }

    @Test
    public void duplicatePointsAreAllReturned()
    {
        PoiGridIndex idx = PoiGridIndex.build(Arrays.asList(
                poi(28.6, -81.38), poi(28.6, -81.38), poi(28.6, -81.38)));

        assertEquals(3, idx.size());
        assertEquals(3, idx.queryRadius(28.6, -81.38, 10).size());
    }

    @Test
    public void pointExactlyOnACellBoundaryIsFound()
    {
        // 28.60 / 0.02 = 1430 exactly: the point sits on a cell edge
        PoiGridIndex idx = PoiGridIndex.build(Arrays.asList(poi(28.60, -81.38)));

        assertEquals(1, idx.queryRadius(28.60, -81.38, 50).size());
        // query from just inside the neighboring cell
        assertEquals(1, idx.queryRadius(28.5999, -81.38, 50).size());
    }
}
