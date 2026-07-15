package com.franckyl.yav1.poi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [P2-POI] Spatial grid index for fast POI proximity lookups while driving.
 *
 * POIs are bucketed into fixed cells of {@link #CELL_DEG} degrees. A radius
 * query only touches the cells overlapping the query circle's bounding box
 * and then does an exact haversine check, so lookups stay O(pois nearby)
 * even with hundreds of thousands of imported points.
 * Immutable after {@link #build}; rebuilt when files are (un)loaded.
 */
public class PoiGridIndex
{
    /** ~2.2 km of latitude per cell. */
    public static final double CELL_DEG = 0.02;

    /** One query result: the POI and its exact distance. */
    public static class Hit
    {
        public final Poi    poi;
        public final double distanceM;

        Hit(Poi poi, double distanceM)
        {
            this.poi       = poi;
            this.distanceM = distanceM;
        }
    }

    private final Map<Long, List<Poi>> mCells = new HashMap<Long, List<Poi>>();
    private int mSize = 0;

    public static PoiGridIndex build(List<Poi> pois)
    {
        PoiGridIndex idx = new PoiGridIndex();

        for(Poi p: pois)
        {
            Long key = keyFor(cellOf(p.lat), cellOf(p.lon));
            List<Poi> l = idx.mCells.get(key);
            if(l == null)
            {
                l = new ArrayList<Poi>(4);
                idx.mCells.put(key, l);
            }
            l.add(p);
            idx.mSize++;
        }

        return idx;
    }

    public int size()
    {
        return mSize;
    }

    /**
     * All POIs within radiusM meters of (lat, lon), with exact distances.
     */
    public List<Hit> queryRadius(double lat, double lon, double radiusM)
    {
        List<Hit> hits = new ArrayList<Hit>();

        if(mSize == 0)
            return hits;

        // cell span needed to cover the radius
        double metersPerCellLat = CELL_DEG * 111320.0;
        int spanLat = (int) Math.ceil(radiusM / metersPerCellLat) + 1;

        double cosLat = Math.cos(Math.toRadians(lat));
        if(cosLat < 0.05)
            cosLat = 0.05; // polar guard
        int spanLon = (int) Math.ceil(radiusM / (metersPerCellLat * cosLat)) + 1;

        int cLat = cellOf(lat);
        int cLon = cellOf(lon);

        for(int i = cLat - spanLat; i <= cLat + spanLat; i++)
        {
            for(int j = cLon - spanLon; j <= cLon + spanLon; j++)
            {
                List<Poi> cell = mCells.get(keyFor(i, j));
                if(cell == null)
                    continue;

                for(Poi p: cell)
                {
                    double d = GeoMath.distanceMeters(lat, lon, p.lat, p.lon);
                    if(d <= radiusM)
                        hits.add(new Hit(p, d));
                }
            }
        }

        return hits;
    }

    private static int cellOf(double deg)
    {
        return (int) Math.floor(deg / CELL_DEG);
    }

    private static Long keyFor(int cLat, int cLon)
    {
        // cLat within +-4500, cLon within +-9000 for valid coordinates
        return Long.valueOf(((long) cLat << 32) | (cLon & 0xffffffffL));
    }
}
