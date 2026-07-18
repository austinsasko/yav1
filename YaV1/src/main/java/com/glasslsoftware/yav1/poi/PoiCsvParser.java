package com.glasslsoftware.yav1.poi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [P2-POI] Tolerant CSV parser for camera / POI databases.
 *
 * Accepted format (documented for users):
 *
 *   - Plain text, one POI per line. Blank lines and lines starting with
 *     '#' or ';' are ignored.
 *   - Delimiter: comma, semicolon or TAB (auto-detected per file).
 *   - Optional header line. When the first data line is not numeric in its
 *     first two fields it is treated as a header, and columns are matched by
 *     name (case-insensitive): lat/latitude/y, lon/lng/long/longitude/x,
 *     type/category/kind, speed/spd/limit/maxspeed, name/label/comment/
 *     description.
 *   - Without a header the column order is:  lat,lon[,type][,speed][,name]
 *     IGO / SCDB style files that use X,Y order (lon first) are detected
 *     automatically when any coordinate exceeds 90 in absolute value in the
 *     first column (a longitude). Files whose coordinates never exceed 90
 *     are assumed to be lat,lon.
 *   - type may be a number (IGO convention) or free text; speed is an
 *     integer (typically km/h); everything after the speed column is joined
 *     back together as the name, so names containing the delimiter survive.
 *   - Rows with missing or out-of-range coordinates are skipped and counted,
 *     never fatal.
 */
public class PoiCsvParser
{
    /** Result of parsing one file. */
    public static class Result
    {
        public final List<Poi> pois       = new ArrayList<Poi>();
        public int             skipped    = 0;
        public boolean         hadHeader  = false;
        public boolean         lonLatOrder = false;
    }

    private PoiCsvParser()
    {
    }

    public static Result parse(Reader reader) throws IOException
    {
        BufferedReader br = (reader instanceof BufferedReader
                                ? (BufferedReader) reader : new BufferedReader(reader));

        List<String> lines = new ArrayList<String>();
        String line;

        while((line = br.readLine()) != null)
        {
            String t = line.trim();
            // strip a UTF-8 BOM on the very first line
            if(lines.isEmpty() && t.startsWith("﻿"))
                t = t.substring(1).trim();
            if(t.isEmpty() || t.startsWith("#") || t.startsWith(";;") || t.startsWith("//"))
                continue;
            lines.add(t);
        }

        Result res = new Result();

        if(lines.isEmpty())
            return res;

        char delim = detectDelimiter(lines.get(0));

        // header detection on the first line
        String[] first = split(lines.get(0), delim);
        int start = 0;

        int colLat = -1, colLon = -1, colType = -1, colSpeed = -1, colName = -1;

        if(first.length >= 2 && (!isNumeric(first[0]) || !isNumeric(first[1])))
        {
            // treat as header
            res.hadHeader = true;
            start = 1;
            for(int i = 0; i < first.length; i++)
            {
                String h = first[i].trim().toLowerCase(Locale.US);
                if(h.equals("lat") || h.equals("latitude") || h.equals("y"))
                    colLat = i;
                else if(h.equals("lon") || h.equals("lng") || h.equals("long") || h.equals("longitude") || h.equals("x"))
                    colLon = i;
                else if(h.equals("type") || h.equals("category") || h.equals("kind"))
                    colType = i;
                else if(h.equals("speed") || h.equals("spd") || h.equals("limit") || h.equals("maxspeed"))
                    colSpeed = i;
                else if(h.equals("name") || h.equals("label") || h.equals("comment") || h.equals("description"))
                    colName = i;
            }

            // header did not give us coordinates: fall back to positional
            if(colLat < 0 || colLon < 0)
            {
                colLat = 0;
                colLon = 1;
                if(colType < 0)  colType  = 2;
                if(colSpeed < 0) colSpeed = 3;
                if(colName < 0)  colName  = 4;
            }
        }
        else
        {
            colLat = 0; colLon = 1; colType = 2; colSpeed = 3; colName = 4;

            // detect IGO/SCDB X,Y (lon,lat) column order: rows whose first
            // value exceeds 90 in absolute terms can only be longitudes.
            // Require at least two such rows (and none pointing the other
            // way) so a single malformed row cannot flip the whole file.
            // Files whose longitudes never exceed 90 are ambiguous and are
            // read as lat,lon - use a header (X,Y) for those.
            int lonVotes = 0, latVotes = 0;

            for(int i = start; i < lines.size(); i++)
            {
                String[] f = split(lines.get(i), delim);
                if(f.length < 2 || !isNumeric(f[0]) || !isNumeric(f[1]))
                    continue;
                double a = Double.parseDouble(f[0].trim());
                double b = Double.parseDouble(f[1].trim());
                if(Math.abs(a) > 90 && Math.abs(a) <= 180 && Math.abs(b) <= 90)
                    lonVotes++;
                else if(Math.abs(b) > 90 && Math.abs(b) <= 180 && Math.abs(a) <= 90)
                    latVotes++;
            }

            if(lonVotes >= 2 && latVotes == 0)
            {
                res.lonLatOrder = true;
                colLat = 1;
                colLon = 0;
            }
        }

        for(int i = start; i < lines.size(); i++)
        {
            String[] f = split(lines.get(i), delim);

            if(f.length < 2 || f.length <= Math.max(colLat, colLon)
                    || !isNumeric(f[colLat]) || !isNumeric(f[colLon]))
            {
                res.skipped++;
                continue;
            }

            double lat = Double.parseDouble(f[colLat].trim());
            double lon = Double.parseDouble(f[colLon].trim());

            if(lat < -90 || lat > 90 || lon < -180 || lon > 180 || (lat == 0 && lon == 0))
            {
                res.skipped++;
                continue;
            }

            String type  = "";
            int    speed = 0;
            String name  = "";

            if(colType >= 0 && colType < f.length)
                type = f[colType].trim();

            if(colSpeed >= 0 && colSpeed < f.length)
            {
                String s = f[colSpeed].trim();
                if(isInteger(s))
                    speed = Integer.parseInt(s);
                else if(!res.hadHeader && !s.isEmpty() && colName >= f.length)
                {
                    // positional file without speed column: 4th column is a name
                    name = s;
                }
            }

            if(name.isEmpty() && colName >= 0 && colName < f.length)
            {
                // join the remaining columns so names containing the delimiter survive
                StringBuilder sb = new StringBuilder();
                int end = (res.hadHeader ? colName + 1 : f.length);
                for(int c = colName; c < end; c++)
                {
                    if(sb.length() > 0)
                        sb.append(delim);
                    sb.append(f[c].trim());
                }
                name = sb.toString().trim();
            }

            res.pois.add(new Poi(lat, lon, type, speed, stripQuotes(name)));
        }

        return res;
    }

    // detect comma / semicolon / tab, whichever occurs most in the line

    private static char detectDelimiter(String line)
    {
        int comma = count(line, ','), semi = count(line, ';'), tab = count(line, '\t');

        if(tab >= comma && tab >= semi && tab > 0)
            return '\t';
        if(semi > comma)
            return ';';
        return ',';
    }

    private static int count(String s, char c)
    {
        int n = 0;
        for(int i = 0; i < s.length(); i++)
            if(s.charAt(i) == c)
                n++;
        return n;
    }

    private static String[] split(String line, char delim)
    {
        // -1 keeps trailing empty fields
        return line.split(java.util.regex.Pattern.quote(String.valueOf(delim)), -1);
    }

    private static boolean isNumeric(String s)
    {
        if(s == null)
            return false;
        s = s.trim();
        if(s.isEmpty())
            return false;
        try
        {
            Double.parseDouble(s);
            return true;
        }
        catch(NumberFormatException e)
        {
            return false;
        }
    }

    private static boolean isInteger(String s)
    {
        if(s == null || s.isEmpty())
            return false;
        try
        {
            Integer.parseInt(s.trim());
            return true;
        }
        catch(NumberFormatException e)
        {
            return false;
        }
    }

    private static String stripQuotes(String s)
    {
        s = s.trim();
        if(s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
            s = s.substring(1, s.length() - 1).trim();
        return s;
    }
}
