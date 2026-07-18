package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GeoMathTest
{
    @Test
    public void distanceOneDegreeLatitude()
    {
        // 1 degree of latitude is ~111.2 km
        double d = GeoMath.distanceMeters(28.0, -81.0, 29.0, -81.0);
        assertEquals(111195, d, 300);
    }

    @Test
    public void distanceZero()
    {
        assertEquals(0.0, GeoMath.distanceMeters(28.5, -81.4, 28.5, -81.4), 1e-6);
    }

    @Test
    public void bearingCardinalDirections()
    {
        // due north
        assertEquals(0.0, GeoMath.initialBearingDeg(28.0, -81.0, 29.0, -81.0), 0.5);
        // due south
        assertEquals(180.0, GeoMath.initialBearingDeg(29.0, -81.0, 28.0, -81.0), 0.5);
        // due east
        assertEquals(90.0, GeoMath.initialBearingDeg(28.0, -81.0, 28.0, -80.0), 0.5);
        // due west
        assertEquals(270.0, GeoMath.initialBearingDeg(28.0, -80.0, 28.0, -81.0), 0.5);
    }

    @Test
    public void angleDiffWrapsCorrectly()
    {
        assertEquals(20.0, GeoMath.angleDiffDeg(350.0, 10.0), 1e-9);
        assertEquals(20.0, GeoMath.angleDiffDeg(10.0, 350.0), 1e-9);
        assertEquals(180.0, GeoMath.angleDiffDeg(0.0, 180.0), 1e-9);
        assertEquals(0.0, GeoMath.angleDiffDeg(90.0, 90.0), 1e-9);
        assertEquals(90.0, GeoMath.angleDiffDeg(45.0, 315.0), 1e-9);
    }
}
