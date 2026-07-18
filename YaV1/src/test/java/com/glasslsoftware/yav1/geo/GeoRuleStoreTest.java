package com.glasslsoftware.yav1.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * [P3-GEO] Round-trip and robustness tests for the persisted
 * state code to profile name mapping.
 */
public class GeoRuleStoreTest
{
    @Test
    public void roundTrip()
    {
        Map<String, String> rules = new HashMap<String, String>();
        rules.put("OH", "X band on");
        rules.put("KY", "K off");
        rules.put("NM", "profile with \"quotes\" & unicode é");

        Map<String, String> back = GeoRuleStore.fromJson(GeoRuleStore.toJson(rules));

        assertEquals(rules, back);
    }

    @Test
    public void emptyAndNullInputsGiveEmptyMap()
    {
        assertTrue(GeoRuleStore.fromJson(null).isEmpty());
        assertTrue(GeoRuleStore.fromJson("").isEmpty());
        assertTrue(GeoRuleStore.fromJson("{}").isEmpty());
    }

    @Test
    public void garbageInputGivesEmptyMap()
    {
        assertTrue(GeoRuleStore.fromJson("not json at all").isEmpty());
        assertTrue(GeoRuleStore.fromJson("[1,2,3]").isEmpty());
    }

    @Test
    public void nullMapSerializesToEmptyObject()
    {
        assertEquals("{}", GeoRuleStore.toJson(null));
    }
}
