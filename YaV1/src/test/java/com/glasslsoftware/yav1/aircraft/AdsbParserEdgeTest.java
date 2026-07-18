package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * [QA] Component tests for AdsbParser field-level edge cases beyond
 * AdsbParserTest: lastPosition fallback, malformed altitude strings,
 * string-typed numerics and mixed-garbage arrays.
 */
public class AdsbParserEdgeTest
{
    @Test
    public void lastPositionFallbackSuppliesCoordinates()
    {
        String json = "{\"ac\":[{\"hex\":\"aaa111\"," +
                      "\"lastPosition\":{\"lat\":30.5,\"lon\":-95.5},\"alt_baro\":1200}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertEquals(30.5, list.get(0).lat, 1e-9);
        assertEquals(-95.5, list.get(0).lon, 1e-9);
        assertEquals(1200, list.get(0).altFt);
    }

    @Test
    public void incompleteLastPositionStillDropsTheEntry()
    {
        String json = "{\"ac\":[{\"hex\":\"aaa111\"," +
                      "\"lastPosition\":{\"lat\":30.5}}]}";

        assertTrue(AdsbParser.parse(json).isEmpty());
    }

    @Test
    public void unknownAltitudeStringIsNotGround()
    {
        String json = "{\"ac\":[{\"hex\":\"aaa111\",\"lat\":30.0,\"lon\":-95.0," +
                      "\"alt_baro\":\"n/a\"}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertEquals(Integer.MIN_VALUE, list.get(0).altFt);
        assertFalse(list.get(0).onGround);
    }

    @Test
    public void groundStringIsCaseInsensitive()
    {
        String json = "{\"ac\":[{\"hex\":\"aaa111\",\"lat\":30.0,\"lon\":-95.0," +
                      "\"alt_baro\":\"GROUND\"}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertTrue(list.get(0).onGround);
    }

    @Test
    public void stringTypedSpeedAndTrackBecomeUnknown()
    {
        String json = "{\"ac\":[{\"hex\":\"aaa111\",\"lat\":30.0,\"lon\":-95.0," +
                      "\"gs\":\"100\",\"track\":\"90\"}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertTrue(Double.isNaN(list.get(0).gsKt));
        assertTrue(Double.isNaN(list.get(0).trackDeg));
    }

    @Test
    public void nonObjectArrayEntriesAreSkippedSiblingsSurvive()
    {
        String json = "{\"ac\":[42,\"junk\",null," +
                      "{\"hex\":\"bbb222\",\"lat\":30.0,\"lon\":-95.0}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertEquals("bbb222", list.get(0).hex);
    }

    @Test
    public void missingIdentFieldsDefaultToEmptyAndTrim()
    {
        String json = "{\"ac\":[{\"lat\":30.0,\"lon\":-95.0,\"flight\":\" FHP25  \"}]}";

        List<Aircraft> list = AdsbParser.parse(json);
        assertEquals(1, list.size());
        assertEquals("", list.get(0).hex);
        assertEquals("", list.get(0).reg);
        assertEquals("FHP25", list.get(0).flight);
        assertEquals("FHP25", list.get(0).bestIdent());
    }
}
