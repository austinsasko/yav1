package com.franckyl.yav1.aircraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * [P2-ADSB] Parser for ADS-B "point" query responses.
 *
 * Understands the readsb-style JSON served by both api.adsb.lol
 * (/v2/point/{lat}/{lon}/{radius_nm}) and api.airplanes.live
 * (/v2/point/{lat}/{lon}/{radius_nm}): an "ac" (or "aircraft") array whose
 * entries carry hex / flight / r / t / lat / lon / alt_baro / gs / track.
 * alt_baro may be a number or the string "ground".
 *
 * Tolerant: entries without a position are dropped, missing fields get
 * sentinel values. Pure Gson, unit tested with fixture files.
 */
public final class AdsbParser
{
    private AdsbParser()
    {
    }

    public static List<Aircraft> parse(String json)
    {
        List<Aircraft> out = new ArrayList<Aircraft>();

        if(json == null || json.isEmpty())
            return out;

        JsonElement root;
        try
        {
            root = new JsonParser().parse(json);
        }
        catch(Exception e)
        {
            return out;
        }

        if(!root.isJsonObject())
            return out;

        JsonObject o = root.getAsJsonObject();

        JsonElement list = o.get("ac");
        if(list == null || !list.isJsonArray())
            list = o.get("aircraft");
        if(list == null || !list.isJsonArray())
            return out;

        JsonArray arr = list.getAsJsonArray();

        for(JsonElement e: arr)
        {
            if(!e.isJsonObject())
                continue;

            try
            {
                Aircraft ac = parseOne(e.getAsJsonObject());
                if(ac != null && ac.hasPosition())
                    out.add(ac);
            }
            catch(Exception ignored)
            {
                // one bad entry never kills the batch
            }
        }

        return out;
    }

    private static Aircraft parseOne(JsonObject j)
    {
        Aircraft ac = new Aircraft();

        ac.hex    = str(j, "hex");
        ac.flight = str(j, "flight").trim();
        ac.reg    = str(j, "r").trim();
        ac.type   = str(j, "t").trim();

        ac.lat = dbl(j, "lat");
        ac.lon = dbl(j, "lon");

        // some feeds put the last known position in lastPosition
        if(Double.isNaN(ac.lat) && j.has("lastPosition") && j.get("lastPosition").isJsonObject())
        {
            JsonObject lp = j.getAsJsonObject("lastPosition");
            ac.lat = dbl(lp, "lat");
            ac.lon = dbl(lp, "lon");
        }

        JsonElement alt = j.get("alt_baro");
        if(alt == null)
            alt = j.get("alt_geom");

        if(alt != null && alt.isJsonPrimitive())
        {
            if(alt.getAsJsonPrimitive().isNumber())
                ac.altFt = alt.getAsInt();
            else if("ground".equalsIgnoreCase(alt.getAsString()))
                ac.onGround = true;
        }

        ac.gsKt     = dbl(j, "gs");
        ac.trackDeg = dbl(j, "track");

        return ac;
    }

    private static String str(JsonObject j, String k)
    {
        JsonElement e = j.get(k);
        if(e != null && e.isJsonPrimitive())
            return e.getAsString();
        return "";
    }

    private static double dbl(JsonObject j, String k)
    {
        JsonElement e = j.get(k);
        if(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber())
            return e.getAsDouble();
        return Double.NaN;
    }
}
