package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * [QA] Component tests for PoiCsvParser covering malformed-input and
 * boundary cases beyond PoiCsvParserTest: header-only files, headers
 * without coordinate columns, ambiguous column-order votes, delimiter
 * precedence, coordinate range edges and empty-field rows.
 */
public class PoiCsvParserEdgeTest
{
    private PoiCsvParser.Result parse(String s) throws IOException
    {
        return PoiCsvParser.parse(new StringReader(s));
    }

    // -- headers -----------------------------------------------------------

    @Test
    public void headerOnlyFileGivesEmptyResult() throws IOException
    {
        PoiCsvParser.Result r = parse("lat,lon,type,speed,name\n");

        assertTrue(r.hadHeader);
        assertEquals(0, r.pois.size());
        assertEquals(0, r.skipped);
    }

    @Test
    public void headerWithoutCoordinateColumnsFallsBackToPositional() throws IOException
    {
        // "foo,bar" is a header (non numeric) but names no lat/lon column:
        // positions 0/1 are used, the matched "type" column is honored
        PoiCsvParser.Result r = parse("foo,bar,type\n28.6,-81.38,2\n");

        assertTrue(r.hadHeader);
        assertEquals(1, r.pois.size());
        assertEquals(28.6, r.pois.get(0).lat, 1e-9);
        assertEquals(-81.38, r.pois.get(0).lon, 1e-9);
        assertEquals("2", r.pois.get(0).type);
    }

    // -- column order voting ----------------------------------------------

    @Test
    public void conflictingOrderVotesKeepLatLon() throws IOException
    {
        // one row votes lon-first, one votes lat-first: no flip happens,
        // the out-of-range row is simply skipped
        PoiCsvParser.Result r = parse(
                "100,45,1\n" +      // |first| > 90: a lon,lat-style row
                "45,100,1\n" +      // |second| > 90: a lat,lon-style row
                "28.6,-81.38,1\n");

        assertFalse(r.lonLatOrder);
        assertEquals(2, r.pois.size());     // 45,100 is a valid lat/lon pair
        assertEquals(1, r.skipped);         // 100,45 has lat out of range
    }

    // -- delimiters --------------------------------------------------------

    @Test
    public void semicolonFileKeepsCommasInsideNames() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6;-81.38;1;50;Main St, near I-4\n");

        assertEquals(1, r.pois.size());
        assertEquals("Main St, near I-4", r.pois.get(0).name);
        assertEquals(50, r.pois.get(0).speed);
    }

    @Test
    public void tabFileKeepsCommasInsideNames() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6\t-81.38\t1\t50\tMain St, near I-4\n");

        assertEquals(1, r.pois.size());
        assertEquals("Main St, near I-4", r.pois.get(0).name);
    }

    @Test
    public void mostFrequentDelimiterWins() throws IOException
    {
        // two semicolons vs one comma: semicolon is the delimiter
        PoiCsvParser.Result r = parse("28.6;-81.38;1,extra\n");

        assertEquals(1, r.pois.size());
        assertEquals("1,extra", r.pois.get(0).type);
    }

    // -- coordinate range edges -------------------------------------------

    @Test
    public void inclusiveCoordinateBoundsAccepted() throws IOException
    {
        PoiCsvParser.Result r = parse("90,180,1\n-90,-180,2\n");

        assertEquals(2, r.pois.size());
        assertEquals(0, r.skipped);
    }

    @Test
    public void justOutOfRangeCoordinatesSkipped() throws IOException
    {
        PoiCsvParser.Result r = parse("90.0001,0,1\n0,180.0001,1\n28.6,-81.38,1\n");

        assertEquals(1, r.pois.size());
        assertEquals(2, r.skipped);
    }

    // -- odd rows ----------------------------------------------------------

    @Test
    public void nonIntegerSpeedInPositionalFileGivesZeroSpeed() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,1,50.5,Cam\n");

        assertEquals(1, r.pois.size());
        assertEquals(0, r.pois.get(0).speed);
        assertEquals("Cam", r.pois.get(0).name);
    }

    @Test
    public void trailingEmptyFieldsGiveEmptyDefaults() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,,,\n");

        assertEquals(1, r.pois.size());
        Poi p = r.pois.get(0);
        assertEquals("", p.type);
        assertEquals(0, p.speed);
        assertEquals("", p.name);
    }

    @Test
    public void crlfLineEndingsHandled() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,1\r\n28.7,-81.4,2\r\n");

        assertEquals(2, r.pois.size());
        assertEquals("1", r.pois.get(0).type);
    }

    @Test
    public void singleSemicolonLineIsDataNotComment() throws IOException
    {
        // only ";;" starts a comment; a lone ";" line is (unparseable) data
        PoiCsvParser.Result r = parse("28.6,-81.38,1\n;28.6,-81.38\n");

        assertEquals(1, r.pois.size());
        assertEquals(1, r.skipped);
    }
}
