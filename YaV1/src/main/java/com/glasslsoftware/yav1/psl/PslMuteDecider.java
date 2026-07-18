package com.glasslsoftware.yav1.psl;

/**
 * [P1-PSL] Pure-logic mute decision for posted-speed-limit muting.
 *
 * Mute when traveling at or below (limit + offset). To avoid flapping at
 * the threshold, an active mute is only released when the speed exceeds
 * (limit + offset + HYSTERESIS_KPH) continuously for more than
 * HYSTERESIS_MS milliseconds.
 *
 * All speeds handled by this class are in km/h. Use {@link #toKph} to
 * convert values expressed in the user's display unit.
 *
 * No Android dependencies: time is passed in by the caller so the logic
 * is fully unit-testable.
 */
public class PslMuteDecider
{
    /** extra speed above (limit + offset) required before unmuting */
    public static final double HYSTERESIS_KPH = 2.0;

    /** time the speed must stay above the hysteresis band before unmuting */
    public static final long   HYSTERESIS_MS  = 2000;

    public static final double MPH_TO_KPH = 1.609344;

    /** display unit indexes, matching YaV1.sCurrUnit / YaV1.sUnits */
    public static final int UNIT_KPH = 0;
    public static final int UNIT_MPH = 1;

    // current state

    private boolean mMuted     = false;
    private long    mOverSince = -1;

    /**
     * Convert a value in the given display unit (UNIT_KPH / UNIT_MPH)
     * to km/h. Unknown units are treated as km/h.
     */
    public static double toKph(double value, int unit)
    {
        return unit == UNIT_MPH ? value * MPH_TO_KPH : value;
    }

    /**
     * Evaluate the mute decision.
     *
     * @param nowMs           monotonic time in ms (SystemClock.elapsedRealtime())
     * @param speedKph        current speed in km/h (negative = unknown)
     * @param limitKph        posted limit in km/h, or null when unknown
     * @param offsetKph       user offset over the limit in km/h
     * @param muteWhenUnknown behavior when the limit is unknown
     * @return true when alerts should be muted
     */
    public synchronized boolean decide(long nowMs, double speedKph, Integer limitKph,
                                       double offsetKph, boolean muteWhenUnknown)
    {
        // no usable speed: never mute, drop any previous state

        if(speedKph < 0)
        {
            mMuted     = false;
            mOverSince = -1;
            return false;
        }

        // unknown limit: apply the configured behavior directly (no hysteresis,
        // there is no threshold to flap around)

        if(limitKph == null)
        {
            mMuted     = muteWhenUnknown;
            mOverSince = -1;
            return mMuted;
        }

        double threshold = limitKph.intValue() + offsetKph;

        if(!mMuted)
        {
            // entering the mute is immediate
            if(speedKph <= threshold)
            {
                mMuted     = true;
                mOverSince = -1;
            }
        }
        else
        {
            if(speedKph > threshold + HYSTERESIS_KPH)
            {
                // above the hysteresis band: unmute only when sustained
                if(mOverSince < 0)
                    mOverSince = nowMs;
                else if(nowMs - mOverSince > HYSTERESIS_MS)
                {
                    mMuted     = false;
                    mOverSince = -1;
                }
            }
            else
            {
                // back inside the band: restart the exit timer
                mOverSince = -1;
            }
        }

        return mMuted;
    }

    /** current state without re-evaluating */
    public synchronized boolean isMuted()
    {
        return mMuted;
    }

    /** drop all state (feature toggled off, GPS lost, ...) */
    public synchronized void reset()
    {
        mMuted     = false;
        mOverSince = -1;
    }
}
