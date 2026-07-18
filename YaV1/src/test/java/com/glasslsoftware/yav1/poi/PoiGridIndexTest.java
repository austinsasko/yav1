package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PoiGridIndexTest
{
    private static Poi poi(double lat, double lon)
    {
        return new Poi(lat, lon, "1", 0, "");
    }

    @Test
    public void findsOnlyPoisWithinRadius()
    {
        List<Poi> pois = new ArrayList<Poi>();
        pois.add(poi(28.6000, -81.3800));   // center
        pois.add(poi(28.6040, -81.3800));   // ~445 m north
        pois.add(poi(28.7000, -81.3800));   // ~11 km north

        PoiGridIndex idx = PoiGridIndex.build(pois);
        assertEquals(3, idx.size());

        List<PoiGridIndex.Hit> hits = idx.queryRadius(28.6000, -81.3800, 500);
        assertEquals(2, hits.size());

        for(PoiGridIndex.Hit h: hits)
            assertTrue(h.distanceM <= 500);
    }

    @Test
    public void exactDistancesReturned()
    {
        PoiGridIndex idx = PoiGridIndex.build(java.util.Arrays.asList(poi(28.6040, -81.3800)));

        List<PoiGridIndex.Hit> hits = idx.queryRadius(28.6000, -81.3800, 1000);
        assertEquals(1, hits.size());
        assertEquals(445, hits.get(0).distanceM, 10);
    }

    @Test
    public void worksAcrossCellBoundaries()
    {
        // 0.02 deg cells: 28.5999 and 28.6001 are in different cells
        PoiGridIndex idx = PoiGridIndex.build(java.util.Arrays.asList(poi(28.6001, -81.3801)));

        List<PoiGridIndex.Hit> hits = idx.queryRadius(28.5999, -81.3799, 100);
        assertEquals(1, hits.size());
    }

    @Test
    public void negativeCoordinatesAndLargeRadius()
    {
        PoiGridIndex idx = PoiGridIndex.build(java.util.Arrays.asList(
                poi(-33.8700, 151.2100),        // Sydney
                poi(-33.9000, 151.2100)));      // ~3.3 km south

        List<PoiGridIndex.Hit> hits = idx.queryRadius(-33.8700, 151.2100, 5000);
        assertEquals(2, hits.size());

        hits = idx.queryRadius(-33.8700, 151.2100, 1000);
        assertEquals(1, hits.size());
    }

    @Test
    public void emptyIndex()
    {
        PoiGridIndex idx = PoiGridIndex.build(new ArrayList<Poi>());
        assertEquals(0, idx.size());
        assertTrue(idx.queryRadius(28.6, -81.38, 100000).isEmpty());
    }
}
