package com.franckyl.yav1.poi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PoiPhrasesTest
{
    @Test
    public void metricDistances()
    {
        assertEquals("400 meters", PoiPhrases.distancePhrase(420, PoiPhrases.UNIT_METRIC));
        assertEquals("450 meters", PoiPhrases.distancePhrase(430, PoiPhrases.UNIT_METRIC));
        assertEquals("1.2 kilometers", PoiPhrases.distancePhrase(1230, PoiPhrases.UNIT_METRIC));
    }

    @Test
    public void imperialDistances()
    {
        // 200 m = 656 ft -> rounds to 700 feet
        assertEquals("700 feet", PoiPhrases.distancePhrase(200, PoiPhrases.UNIT_IMPERIAL));
        // 800 m = 2625 ft -> 0.5 miles
        assertEquals("0.5 miles", PoiPhrases.distancePhrase(800, PoiPhrases.UNIT_IMPERIAL));
    }

    @Test
    public void alertPhrases()
    {
        Poi cam = new Poi(28.6, -81.38, "1", 50, "");

        assertEquals("Speed camera ahead, 450 meters",
                PoiPhrases.alertPhrase(cam, 450, PoiAlertEngine.STAGE_APPROACH, PoiPhrases.UNIT_METRIC));
        assertEquals("Speed camera, 200 meters",
                PoiPhrases.alertPhrase(cam, 200, PoiAlertEngine.STAGE_CLOSE, PoiPhrases.UNIT_METRIC));

        Poi named = new Poi(28.6, -81.38, "3", 0, "I4 exit 82");
        assertEquals("I4 exit 82 ahead, 450 meters",
                PoiPhrases.alertPhrase(named, 450, PoiAlertEngine.STAGE_APPROACH, PoiPhrases.UNIT_METRIC));
    }
}
