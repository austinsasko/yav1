package com.franckyl.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class AdsbParserTest
{
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
    public void parsesPointResponseFixture() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsb_point_sample.json"));

        // the entry without any position is dropped
        assertEquals(3, list.size());

        Aircraft fhp = list.get(0);
        assertEquals("a255e2", fhp.hex);
        assertEquals("N25HP", fhp.reg);
        assertEquals("FHP25", fhp.flight);
        assertEquals("C182", fhp.type);
        assertEquals(1800, fhp.altFt);
        assertFalse(fhp.onGround);
        assertEquals(95.3, fhp.gsKt, 1e-9);
        assertEquals(140.0, fhp.trackDeg, 1e-9);
        assertEquals(28.55, fhp.lat, 1e-9);
        assertEquals(-81.36, fhp.lon, 1e-9);
    }

    @Test
    public void groundStringParsed() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsb_point_sample.json"));

        Aircraft gr = list.get(1);
        assertEquals("a00002", gr.hex);
        assertTrue(gr.onGround);
        assertEquals(Integer.MIN_VALUE, gr.altFt);
    }

    @Test
    public void missingFieldsGetSentinels() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsb_point_sample.json"));

        Aircraft np = list.get(2);
        assertEquals(2200, np.altFt);              // alt_geom fallback
        assertTrue(Double.isNaN(np.gsKt));
        assertTrue(Double.isNaN(np.trackDeg));
        assertEquals("", np.flight);
    }

    @Test
    public void acceptsAircraftArrayKey()
    {
        String json = "{\"aircraft\":[{\"hex\":\"abc123\",\"lat\":1.0,\"lon\":2.0}]}";
        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertEquals("abc123", list.get(0).hex);
    }

    @Test
    public void garbageInputsGiveEmptyList()
    {
        assertTrue(AdsbParser.parse(null).isEmpty());
        assertTrue(AdsbParser.parse("").isEmpty());
        assertTrue(AdsbParser.parse("not json at all {{{").isEmpty());
        assertTrue(AdsbParser.parse("{\"msg\":\"no ac key\"}").isEmpty());
        assertTrue(AdsbParser.parse("[1,2,3]").isEmpty());
    }

    @Test
    public void bestIdentPrefersRegistration()
    {
        Aircraft ac = new Aircraft();
        ac.hex = "abc123";
        assertEquals("abc123", ac.bestIdent());

        ac.flight = "FHP25";
        assertEquals("FHP25", ac.bestIdent());

        ac.reg = "N25HP";
        assertEquals("N25HP", ac.bestIdent());
    }
}
