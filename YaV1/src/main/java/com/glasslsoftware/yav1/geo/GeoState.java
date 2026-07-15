package com.glasslsoftware.yav1.geo;

/**
 * [P3-GEO] One US state (or DC / Puerto Rico) from assets/geo/us_states.json.
 *
 * The JSON uses short keys to keep the asset small:
 *   "c" - USPS state code ("OH")
 *   "n" - full name ("Ohio")
 *   "b" - bounding box [minLon, minLat, maxLon, maxLat]
 *   "p" - list of outer rings, each ring a list of [lon, lat] points
 *
 * Dataset source: US Census Bureau 2010 cartographic boundary file at
 * 1:5,000,000 scale (gz_2010_us_040_00_5m, public domain), simplified with
 * Douglas-Peucker (0.02 degree tolerance), coordinates rounded to 4 decimals,
 * islands smaller than ~0.004 square degrees dropped. See the "source" field
 * of the asset itself.
 *
 * Field names intentionally match the JSON keys so Gson can bind them
 * without annotations (the app never enables minification).
 */
public class GeoState
{
    public String       c;
    public String       n;
    public double[]     b;
    public double[][][] p;

    public String getCode()
    {
        return c;
    }

    public String getName()
    {
        return n;
    }

    /** Cheap prefilter: is the point inside the bounding box? */
    public boolean bboxContains(double lat, double lon)
    {
        return b != null && b.length == 4
                && lon >= b[0] && lon <= b[2]
                && lat >= b[1] && lat <= b[3];
    }

    /** Full test: is the point inside any of the state's outer rings? */
    public boolean contains(double lat, double lon)
    {
        if(p == null)
            return false;

        for(double[][] ring: p)
        {
            if(pointInRing(lat, lon, ring))
                return true;
        }

        return false;
    }

    /**
     * Ray casting point-in-polygon. The ring is an open list of [lon, lat]
     * points (last point connects back to the first). Points exactly on an
     * edge may resolve to either side; with a simplified dataset the border
     * itself is approximate anyway.
     */
    public static boolean pointInRing(double lat, double lon, double[][] ring)
    {
        if(ring == null || ring.length < 3)
            return false;

        boolean inside = false;
        int     n      = ring.length;
        int     j      = n - 1;

        for(int i = 0; i < n; i++)
        {
            double xi = ring[i][0], yi = ring[i][1];
            double xj = ring[j][0], yj = ring[j][1];

            if(((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi))
                inside = !inside;

            j = i;
        }

        return inside;
    }
}
