package com.franckyl.yav1.poi;

import java.util.Locale;

/**
 * [P2-POI] Spoken-phrase helpers for POI alerts. Pure functions, unit tested.
 */
public final class PoiPhrases
{
    /** Metric units (meters / kilometers). */
    public static final int UNIT_METRIC   = 0;
    /** Imperial units (feet / miles), matches YaV1 speed_unit == 1 (MPH). */
    public static final int UNIT_IMPERIAL = 1;

    private PoiPhrases()
    {
    }

    /**
     * Speakable distance, rounded to friendly steps.
     * Metric: "400 meters" / "1.2 kilometers".
     * Imperial: "500 feet" / "0.3 miles".
     */
    public static String distancePhrase(double meters, int unit)
    {
        if(unit == UNIT_IMPERIAL)
        {
            double feet = meters * 3.28084;
            if(feet < 1000)
                return ((int) (Math.round(feet / 100.0) * 100)) + " feet";

            double miles = meters / 1609.344;
            return String.format(Locale.US, "%.1f miles", Math.round(miles * 10) / 10.0);
        }

        if(meters < 1000)
            return ((int) (Math.round(meters / 50.0) * 50)) + " meters";

        return String.format(Locale.US, "%.1f kilometers", Math.round(meters / 100.0) / 10.0);
    }

    /** Full phrase for an alert stage. */
    public static String alertPhrase(Poi poi, double distanceM, int stage, int unit)
    {
        String what = poi.label();
        String dist = distancePhrase(distanceM, unit);

        if(stage == PoiAlertEngine.STAGE_CLOSE)
            return what + ", " + dist;

        return what + " ahead, " + dist;
    }
}
