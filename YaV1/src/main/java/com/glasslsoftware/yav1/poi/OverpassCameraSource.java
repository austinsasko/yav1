package com.glasslsoftware.yav1.poi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [P2-POI] Camera / enforcement POIs from OpenStreetMap via Overpass.
 *
 * One union query fetches (live-validated 2026-07-14, Houston + NYC):
 *  - node/way  highway=speed_camera                  -> type "speed_camera"
 *  - relation  type=enforcement, enforcement=maxspeed        -> "speed_camera"
 *  - relation  type=enforcement, enforcement=traffic_signals -> "redlight"
 *  - node      man_made=surveillance + surveillance:type=ALPR -> "alpr"
 *    (Flock Safety etc. - the tagging DeFlock.me contributes)
 *
 * Ways and relations are resolved to a point via Overpass "out center".
 * The maxspeed tag, when parseable, fills Poi.speed (km/h). Provenance
 * goes to Poi.source as "osm:<kind>". Pure org.json logic, unit tested
 * with recorded fixtures.
 */
public class OverpassCameraSource implements PoiOnlineSource
{
    public static final String NAME = "osm";

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public String prefKey()
    {
        return "poi_online_osm";
    }

    @Override
    public String buildQuery(double lat, double lon, int radiusM,
                             boolean wantCams, boolean wantAlpr)
    {
        String around = String.format(Locale.US, "(around:%d,%.6f,%.6f)", radiusM, lat, lon);
        StringBuilder q = new StringBuilder("[out:json][timeout:15];(");

        if(wantCams)
        {
            q.append("node").append(around).append("[\"highway\"=\"speed_camera\"];");
            q.append("way").append(around).append("[\"highway\"=\"speed_camera\"];");
            q.append("relation").append(around).append("[\"type\"=\"enforcement\"];");
        }
        if(wantAlpr)
        {
            q.append("node").append(around)
             .append("[\"man_made\"=\"surveillance\"][\"surveillance:type\"~\"ALPR\",i];");
        }

        return q.append(");out tags center;").toString();
    }

    @Override
    public List<Poi> parse(String body, boolean wantCams, boolean wantAlpr)
    {
        List<Poi> out = new ArrayList<Poi>();

        try
        {
            JSONObject root = new JSONObject(body);
            JSONArray  els  = root.optJSONArray("elements");
            if(els == null)
                return out;

            for(int i = 0; i < els.length(); i++)
            {
                JSONObject el = els.optJSONObject(i);
                if(el == null)
                    continue;

                Poi p = parseElement(el, wantCams, wantAlpr);
                if(p != null)
                    out.add(p);
            }
        }
        catch(Exception exc)
        {
            // malformed body: no POIs
        }

        return out;
    }

    /** one Overpass element -> Poi, or null when not a wanted camera */
    static Poi parseElement(JSONObject el, boolean wantCams, boolean wantAlpr)
    {
        JSONObject tags = el.optJSONObject("tags");
        if(tags == null)
            return null;

        // position: node lat/lon, or way/relation "center"
        double lat = el.optDouble("lat", Double.NaN);
        double lon = el.optDouble("lon", Double.NaN);

        if(Double.isNaN(lat) || Double.isNaN(lon))
        {
            JSONObject c = el.optJSONObject("center");
            if(c == null)
                return null;
            lat = c.optDouble("lat", Double.NaN);
            lon = c.optDouble("lon", Double.NaN);
        }

        if(Double.isNaN(lat) || Double.isNaN(lon))
            return null;

        String kind = null;
        String type = null;

        if("speed_camera".equals(tags.optString("highway")))
        {
            kind = "speed_camera";
            type = "speed_camera";
        }
        else if("enforcement".equals(tags.optString("type")))
        {
            String enf = tags.optString("enforcement");
            if("maxspeed".equals(enf))
            {
                kind = "enforcement";
                type = "speed_camera";
            }
            else if("traffic_signals".equals(enf))
            {
                kind = "enforcement";
                type = "redlight";
            }
            else
                return null;   // check_date, mindistance, ... not alert-worthy
        }
        else if(tags.optString("surveillance:type").toUpperCase(Locale.US).contains("ALPR"))
        {
            kind = "alpr";
            type = "alpr";
        }

        if(kind == null)
            return null;
        if(("alpr".equals(kind) && !wantAlpr) || (!"alpr".equals(kind) && !wantCams))
            return null;

        int speed = 0;
        Integer kph = com.glasslsoftware.yav1.psl.OverpassSpeedLimitProvider
                          .parseMaxspeed(tags.optString("maxspeed", null));
        if(kph != null)
            speed = kph.intValue();

        // best available human label: name > operator > manufacturer
        String label = tags.optString("name", "");
        if(label.isEmpty())
            label = tags.optString("operator", "");
        if(label.isEmpty())
            label = tags.optString("manufacturer", "");

        // for ALPR keep the label speakable ("Flock Safety" alone is cryptic)
        if("alpr".equals(kind) && !label.isEmpty())
            label = "License plate reader (" + label + ")";

        return new Poi(lat, lon, type, speed, label, NAME + ":" + kind);
    }
}
