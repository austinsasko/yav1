package com.glasslsoftware.yav1.poi;

/**
 * [P2-POI] Small geodesy helpers (haversine distance, initial bearing,
 * angular difference). Pure static functions, unit tested.
 */
public final class GeoMath
{
    public static final double EARTH_RADIUS_M = 6371000.0;

    private GeoMath()
    {
    }

    /** Great-circle distance in meters between two WGS84 points. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2)
    {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Initial great-circle bearing from point 1 to point 2, degrees 0..360. */
    public static double initialBearingDeg(double lat1, double lon1, double lat2, double lon2)
    {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dL = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dL) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dL);

        double b = Math.toDegrees(Math.atan2(y, x));
        return (b + 360.0) % 360.0;
    }

    /** Smallest absolute angle between two bearings, degrees 0..180. */
    public static double angleDiffDeg(double a, double b)
    {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }
}
