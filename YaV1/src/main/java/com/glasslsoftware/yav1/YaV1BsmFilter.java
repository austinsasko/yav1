package com.glasslsoftware.yav1;

import android.content.SharedPreferences;

import com.glasslsoftware.yav1lib.YaV1Alert;

/**
 * Heuristic filter for K band blind-spot-monitor (BSM) falses.
 *
 * BSM radars show up as short lived K band hits with a rapid signal strength ramp
 * and no persistence. When the filter is enabled (preference "bsm_filter", default
 * off) every new K band alert is held back (muted, flagged with PROP_BSM) for a
 * short interval. Alerts that die during the hold were falses and are never heard;
 * alerts that persist are released and behave normally. A signal that ramps up
 * unusually fast while young is held a little longer, since that pattern matches a
 * BSM equipped car passing by.
 *
 * Alerts the V1 Gen2 itself flags as junk (PROP_JUNK) stay held for their entire
 * life while the filter is on.
 *
 * The decision logic is pure (no Android dependencies) so it can be unit tested.
 */
public class YaV1BsmFilter
{
    /** Filter master switch, from preference "bsm_filter" (default off). */
    private static volatile boolean sEnabled    = false;

    /** How long a brand new K band alert is held back, in ms. */
    public  static int      sHoldMs     = 1500;

    /** Extended hold applied when the signal ramps quickly while young, in ms. */
    public  static int      sRampHoldMs = 3500;

    /** Signal (LED) increase over the initial strength considered a rapid ramp. */
    public  static int      sRampLeds   = 3;

    private YaV1BsmFilter()
    {
    }

    /** Read the preference state. */
    public static void init(SharedPreferences sh)
    {
        sEnabled = sh.getBoolean("bsm_filter", false);
    }

    public static boolean isEnabled()
    {
        return sEnabled;
    }

    /** For unit tests. */
    static void setEnabled(boolean enabled)
    {
        sEnabled = enabled;
    }

    /**
     * Should a brand new alert enter the filter (be held back)?
     *
     * @param band the YaV1Alert.BAND_* value of the alert
     *
     * @return true when the filter is on and the alert is K band.
     */
    public static boolean shouldHoldNew(int band)
    {
        return sEnabled && band == YaV1Alert.BAND_K;
    }

    /**
     * Decide whether an alert already in the filter must stay held.
     *
     * @param ageMs         how long the alert has existed, in ms
     * @param baseSignal    the signal strength (LEDs) when the alert first appeared
     * @param maxSignal     the strongest signal seen so far
     * @param junkFlagged   true when the V1 Gen2 flagged the alert as junk
     *
     * @return true to keep the alert muted, false to release it.
     */
    public static boolean shouldStayHeld(long ageMs, int baseSignal, int maxSignal, boolean junkFlagged)
    {
        if(!sEnabled)
            return false;

        if(junkFlagged)
        {
            // The detector itself says this is junk, trust it while the flag lasts.
            return true;
        }

        if(ageMs < sHoldMs)
        {
            // Still inside the initial persistence window.
            return true;
        }

        if(ageMs < sRampHoldMs && (maxSignal - baseSignal) >= sRampLeds)
        {
            // Young alert whose strength jumped quickly: typical of a BSM equipped
            // car passing by. Hold a little longer before believing it.
            return true;
        }

        return false;
    }
}
