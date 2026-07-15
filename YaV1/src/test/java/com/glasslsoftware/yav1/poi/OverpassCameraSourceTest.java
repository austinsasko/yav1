package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * [P2-POI] OverpassCameraSource against responses recorded LIVE from
 * overpass-api.de on 2026-07-14:
 *
 *  - overpass_cameras_nyc.json     5km around midtown Manhattan:
 *      8 highway=speed_camera nodes, 1 enforcement=traffic_signals
 *      relation, 2 enforcement=maxspeed relations (maxspeed "30 mph"),
 *      219 ALPR surveillance nodes
 *  - overpass_cameras_houston.json 8km around downtown Houston:
 *      516 ALPR nodes (Flock Safety - DeFlock-contributed tagging),
 *      1 speed camera
 */
public class OverpassCameraSourceTest
{
    private final OverpassCameraSource mSource = new OverpassCameraSource();

    private String fixture(String name) throws IOException
    {
        InputStream in = getClass().getResourceAsStream(name);
        assertTrue("fixture " + name + " missing", in != null);

        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null)
            sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    @Test
    public void queryContainsAllWantedKinds()
    {
        String q = mSource.buildQuery(29.7604, -95.3698, 8000, true, true);

        assertTrue(q.contains("(around:8000,29.760400,-95.369800)"));
        assertTrue(q.contains("[\"highway\"=\"speed_camera\"]"));
        assertTrue(q.contains("[\"type\"=\"enforcement\"]"));
        assertTrue(q.contains("[\"surveillance:type\"~\"ALPR\",i]"));
        assertTrue(q.contains("out tags center"));

        // toggles prune the union
        String noAlpr = mSource.buildQuery(29.76, -95.37, 8000, true, false);
        assertTrue(!noAlpr.contains("surveillance"));

        String noCams = mSource.buildQuery(29.76, -95.37, 8000, false, true);
        assertTrue(!noCams.contains("speed_camera"));
        assertTrue(!noCams.contains("enforcement"));
    }

    @Test
    public void nycFixtureClassifiesAllCategories() throws IOException
    {
        List<Poi> pois = mSource.parse(fixture("/poi/overpass_cameras_nyc.json"), true, true);

        int speed = 0, red = 0, alpr = 0;
        for(Poi p : pois)
        {
            assertTrue(p.lat != 0 && p.lon != 0);
            assertTrue(p.source.startsWith("osm:"));

            if("speed_camera".equals(p.type)) speed++;
            else if("redlight".equals(p.type)) red++;
            else if("alpr".equals(p.type)) alpr++;
        }

        // 8 speed_camera nodes + 2 enforcement=maxspeed relations
        assertEquals(10, speed);
        // 1 enforcement=traffic_signals relation
        assertEquals(1, red);
        assertEquals(219, alpr);
        assertEquals(230, pois.size());
    }

    @Test
    public void enforcementRelationsUseCenterAndMaxspeed() throws IOException
    {
        List<Poi> pois = mSource.parse(fixture("/poi/overpass_cameras_nyc.json"), true, false);

        // ALPR filtered out by the toggle
        assertEquals(11, pois.size());

        int with30 = 0;
        for(Poi p : pois)
        {
            if(p.speed == 48)      // "30 mph" -> 48 km/h
                with30++;
        }
        assertEquals(2, with30);   // the two enforcement=maxspeed relations
    }

    @Test
    public void alprToggleAloneYieldsOnlyPlateReaders() throws IOException
    {
        List<Poi> pois = mSource.parse(fixture("/poi/overpass_cameras_nyc.json"), false, true);

        assertEquals(219, pois.size());
        for(Poi p : pois)
        {
            assertEquals("alpr", p.type);
            assertEquals("osm:alpr", p.source);
        }
    }

    @Test
    public void houstonAlprVolumeParses() throws IOException
    {
        List<Poi> pois = mSource.parse(fixture("/poi/overpass_cameras_houston.json"), true, true);

        assertEquals(517, pois.size());

        // Flock operator/manufacturer becomes a speakable label
        int flock = 0;
        for(Poi p : pois)
        {
            if(p.name.contains("Flock"))
            {
                assertTrue(p.name.startsWith("License plate reader ("));
                flock++;
            }
        }
        assertTrue("expected many Flock-labeled entries, got " + flock, flock > 400);
    }

    @Test
    public void alprLabelIsSpeakable()
    {
        assertEquals("License plate reader", Poi.typeLabel("alpr"));
    }

    @Test
    public void malformedBodiesFailSoft()
    {
        assertTrue(mSource.parse("", true, true).isEmpty());
        assertTrue(mSource.parse("<html>rate limited</html>", true, true).isEmpty());
        assertTrue(mSource.parse("{}", true, true).isEmpty());
        assertTrue(mSource.parse("{\"elements\":[]}", true, true).isEmpty());
        // element without tags or position is skipped, not fatal
        assertTrue(mSource.parse("{\"elements\":[{\"type\":\"node\",\"id\":1}]}", true, true).isEmpty());
    }
}
