package com.glasslsoftware.yav1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * [QA] Component tests for YaV1BsmFilter beyond YaV1BsmFilterTest: the
 * preference wiring of init() (SharedPreferences is an interface, so a
 * plain fake works on the JVM) and the exact ramp-threshold boundary.
 */
public class YaV1BsmFilterEdgeTest
{
    /** minimal SharedPreferences fake backed by a map */
    private static class FakePrefs implements SharedPreferences
    {
        final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public boolean getBoolean(String key, boolean defValue)
        {
            Object v = values.get(key);
            return v instanceof Boolean ? ((Boolean) v).booleanValue() : defValue;
        }

        @Override public Map<String, ?> getAll() { return values; }
        @Override public String getString(String key, String defValue) { return defValue; }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) { return defValue; }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    @Before
    public void setUp()
    {
        YaV1BsmFilter.sHoldMs     = 1500;
        YaV1BsmFilter.sRampHoldMs = 3500;
        YaV1BsmFilter.sRampLeds   = 3;
    }

    @After
    public void tearDown()
    {
        YaV1BsmFilter.setEnabled(false);
    }

    @Test
    public void initReadsTheBsmFilterPreference()
    {
        FakePrefs prefs = new FakePrefs();

        prefs.values.put("bsm_filter", Boolean.TRUE);
        YaV1BsmFilter.init(prefs);
        assertTrue(YaV1BsmFilter.isEnabled());

        prefs.values.put("bsm_filter", Boolean.FALSE);
        YaV1BsmFilter.init(prefs);
        assertFalse(YaV1BsmFilter.isEnabled());
    }

    @Test
    public void missingPreferenceDefaultsToOff()
    {
        YaV1BsmFilter.setEnabled(true);
        YaV1BsmFilter.init(new FakePrefs());     // key absent -> default false
        assertFalse(YaV1BsmFilter.isEnabled());
    }

    @Test
    public void rampBoundaryIsInclusive()
    {
        YaV1BsmFilter.setEnabled(true);

        // exactly sRampLeds of growth extends the hold
        assertTrue(YaV1BsmFilter.shouldStayHeld(2000, 2, 5, false));
        // one LED less does not
        assertFalse(YaV1BsmFilter.shouldStayHeld(2000, 2, 4, false));
    }

    @Test
    public void rampHoldEndsExactlyAtRampHoldMs()
    {
        YaV1BsmFilter.setEnabled(true);

        assertTrue(YaV1BsmFilter.shouldStayHeld(YaV1BsmFilter.sRampHoldMs - 1, 1, 6, false));
        assertFalse(YaV1BsmFilter.shouldStayHeld(YaV1BsmFilter.sRampHoldMs, 1, 6, false));
    }
}
