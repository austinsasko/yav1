package com.glasslsoftware.yav1.crowd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [CSA] Parser for the Waze live-map georss JSON ("alerts" array). Pure
 * org.json, unit tested. Only report types that matter to a drive are kept
 * (POLICE / ACCIDENT / HAZARD / ROAD_CLOSED); jams and chit-chat are dropped.
 */
public class WazeFeedParser
{
    /** reports older than this are noise */
    public static final long MAX_REPORT_AGE_MS = 30 * 60 * 1000L;

    private WazeFeedParser()
    {
    }

    public static List<CrowdAlert> parse(String json)
    {
        List<CrowdAlert> result = new ArrayList<CrowdAlert>();

        try
        {
            JSONObject root   = new JSONObject(json);
            JSONArray  alerts = root.optJSONArray("alerts");

            if(alerts == null)
                return result;

            for(int i = 0; i < alerts.length(); i++)
            {
                JSONObject item = alerts.optJSONObject(i);
                if(item == null)
                    continue;

                String uuid = item.optString("uuid", "");
                String type = item.optString("type", "").toUpperCase();
                JSONObject location = item.optJSONObject("location");

                if(uuid.isEmpty() || location == null)
                    continue;

                int kind;
                if(type.equals("POLICE"))
                    kind = CrowdAlert.KIND_POLICE;
                else if(type.equals("ACCIDENT"))
                    kind = CrowdAlert.KIND_ACCIDENT;
                else if(type.equals("HAZARD") || type.equals("ROAD_CLOSED"))
                    kind = CrowdAlert.KIND_HAZARD;
                else
                    continue;

                result.add(new CrowdAlert(uuid, kind,
                                          location.optDouble("y", 0),
                                          location.optDouble("x", 0),
                                          item.optLong("pubMillis", 0),
                                          item.optInt("nThumbsUp", 0),
                                          "waze"));
            }
        }
        catch(Exception e)
        {
            // soft failure: an unparsable feed just means no crowd data
        }

        return result;
    }

    /** Drop stale reports and duplicate ids (first occurrence wins). */
    public static List<CrowdAlert> prune(List<CrowdAlert> alerts, long nowMs)
    {
        List<CrowdAlert> kept = new ArrayList<CrowdAlert>();
        Set<String>      seen = new HashSet<String>();

        for(CrowdAlert alert: alerts)
        {
            if(alert.reportedAtMs > 0 && nowMs - alert.reportedAtMs > MAX_REPORT_AGE_MS)
                continue;
            if(!seen.add(alert.id))
                continue;
            kept.add(alert);
        }
        return kept;
    }
}
