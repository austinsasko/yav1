package com.glasslsoftware.yav1.geo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * [P3-GEO] Serialization of the state code to profile name mapping.
 *
 * The mapping is persisted as a JSON object string (e.g.
 * {"OH":"X band on","KY":"K off"}) in the app SharedPreferences under
 * {@link GeoProfileManager#PREF_RULES}. Pure static helpers, JVM testable.
 */
public class GeoRuleStore
{
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, String>>(){}.getType();

    private GeoRuleStore()
    {
    }

    /** Parse the persisted JSON; returns an empty map on garbage input. */
    public static Map<String, String> fromJson(String json)
    {
        if(json != null && json.length() > 0)
        {
            try
            {
                Map<String, String> m = new Gson().fromJson(json, MAP_TYPE);

                if(m != null)
                    return m;
            }
            catch(Exception e)
            {
                // corrupted preference: fall through to empty
            }
        }

        return new HashMap<String, String>();
    }

    /** Serialize a mapping for persistence. */
    public static String toJson(Map<String, String> rules)
    {
        return new Gson().toJson(rules != null ? rules : new HashMap<String, String>(), MAP_TYPE);
    }
}
