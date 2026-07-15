package com.franckyl.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class PoiCsvParserTest
{
    private PoiCsvParser.Result parse(String s) throws IOException
    {
        return PoiCsvParser.parse(new StringReader(s));
    }

    @Test
    public void nativeOrderWithTypeSpeedName() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6000,-81.3800,1,50,Cam A\n28.7000,-81.4000,3,0,Cam B\n");

        assertEquals(2, r.pois.size());
        assertEquals(0, r.skipped);
        assertFalse(r.hadHeader);
        assertFalse(r.lonLatOrder);

        Poi p = r.pois.get(0);
        assertEquals(28.6000, p.lat, 1e-9);
        assertEquals(-81.3800, p.lon, 1e-9);
        assertEquals("1", p.type);
        assertEquals(50, p.speed);
        assertEquals("Cam A", p.name);
    }

    @Test
    public void igoLonLatOrderDetected() throws IOException
    {
        // IGO/SCDB X,Y convention: longitude first (|lon| > 90 in 2+ rows)
        PoiCsvParser.Result r = parse("151.2100,-33.8700,2\n151.2200,-33.8800,1\n");

        assertTrue(r.lonLatOrder);
        assertEquals(2, r.pois.size());
        assertEquals(-33.8700, r.pois.get(0).lat, 1e-9);
        assertEquals(151.2100, r.pois.get(0).lon, 1e-9);
        assertEquals("2", r.pois.get(0).type);
    }

    @Test
    public void singleOutlierRowDoesNotFlipColumnOrder() throws IOException
    {
        // one bogus row with lat 91.5 must not turn the file into lon,lat
        PoiCsvParser.Result r = parse("28.6,-81.38,1\n91.5,-81.38,1\n28.7,-81.39,2\n");

        assertFalse(r.lonLatOrder);
        assertEquals(2, r.pois.size());
        assertEquals(1, r.skipped);
    }

    @Test
    public void headerColumnsMatchedByName() throws IOException
    {
        PoiCsvParser.Result r = parse("name,lat,lon,type,speed\nFoo,28.6,-81.4,3,60\n");

        assertTrue(r.hadHeader);
        assertEquals(1, r.pois.size());
        Poi p = r.pois.get(0);
        assertEquals(28.6, p.lat, 1e-9);
        assertEquals(-81.4, p.lon, 1e-9);
        assertEquals("3", p.type);
        assertEquals(60, p.speed);
        assertEquals("Foo", p.name);
    }

    @Test
    public void headerXYColumns() throws IOException
    {
        PoiCsvParser.Result r = parse("X,Y,TYPE\n-81.38,28.60,2\n");

        assertTrue(r.hadHeader);
        assertEquals(1, r.pois.size());
        assertEquals(28.60, r.pois.get(0).lat, 1e-9);
        assertEquals(-81.38, r.pois.get(0).lon, 1e-9);
    }

    @Test
    public void semicolonAndTabDelimiters() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6;-81.38;1;60;Cam B\n");
        assertEquals(1, r.pois.size());
        assertEquals(60, r.pois.get(0).speed);
        assertEquals("Cam B", r.pois.get(0).name);

        r = parse("28.6\t-81.38\t1\n");
        assertEquals(1, r.pois.size());
        assertEquals("1", r.pois.get(0).type);
    }

    @Test
    public void malformedRowsSkippedNotFatal() throws IOException
    {
        PoiCsvParser.Result r = parse(
                "28.6,-81.38,1\n" +
                "abc,def,1\n" +          // non numeric
                "91.5,-81.38,1\n" +      // lat out of range
                "28.6\n" +               // too short
                "0,0,1\n" +              // null island
                "28.7,-81.39,2\n");

        assertEquals(2, r.pois.size());
        assertEquals(4, r.skipped);
    }

    @Test
    public void commentsAndBlankLinesIgnored() throws IOException
    {
        PoiCsvParser.Result r = parse(
                "# comment\n" +
                "\n" +
                "// another comment\n" +
                "28.6,-81.38,1\n");

        assertEquals(1, r.pois.size());
        assertEquals(0, r.skipped);
    }

    @Test
    public void bomStripped() throws IOException
    {
        PoiCsvParser.Result r = parse("﻿28.6,-81.38,1\n");
        assertEquals(1, r.pois.size());
    }

    @Test
    public void positionalFileWithoutSpeedUsesFourthColumnAsName() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,1,Main Cam\n");

        assertEquals(1, r.pois.size());
        assertEquals(0, r.pois.get(0).speed);
        assertEquals("Main Cam", r.pois.get(0).name);
    }

    @Test
    public void nameContainingDelimiterSurvives() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,1,50,Main St, exit 4\n");

        assertEquals(1, r.pois.size());
        assertEquals("Main St,exit 4", r.pois.get(0).name);
    }

    @Test
    public void quotedNameStripped() throws IOException
    {
        PoiCsvParser.Result r = parse("28.6,-81.38,1,50,\"Cam A\"\n");
        assertEquals("Cam A", r.pois.get(0).name);
    }

    @Test
    public void emptyFileGivesEmptyResult() throws IOException
    {
        PoiCsvParser.Result r = parse("");
        assertEquals(0, r.pois.size());
        assertEquals(0, r.skipped);
    }

    @Test
    public void typeLabels()
    {
        assertEquals("Speed camera", Poi.typeLabel("1"));
        assertEquals("Mobile camera", Poi.typeLabel("2"));
        assertEquals("Red light camera", Poi.typeLabel("3"));
        assertEquals("Camera", Poi.typeLabel(""));
        assertEquals("Camera", Poi.typeLabel(null));
        assertEquals("Radar trap", Poi.typeLabel("radar trap"));
    }
}
