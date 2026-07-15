package com.glasslsoftware.yav1.aircraft;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * [P2-ADSB] Behavior assessment and alert throttling for observed aircraft.
 *
 * Two independent signals:
 *  - watchlist match (known law-enforcement airframe): reliable
 *  - behavior HEURISTIC: airborne below {@link #MAX_ALT_FT} ft, ground speed
 *    between {@link #MIN_GS_KT} and {@link #MAX_GS_KT} kt, and "loitering" -
 *    the summed heading change across the recent polls exceeds
 *    {@link #LOITER_TURN_DEG} degrees (orbiting / figure-eight patterns
 *    typical of aerial speed enforcement). Clearly labeled as a guess:
 *    traffic-watch, flight training and survey flights look identical.
 *
 * Alerts are throttled to once per aircraft per {@link #ALERT_COOLDOWN_MS}.
 * Pure logic, time injected, unit tested.
 */
public class AircraftTracker
{
    // heuristic gates
    public static final int  MAX_ALT_FT        = 5500;
    public static final int  MIN_GS_KT         = 30;
    public static final int  MAX_GS_KT         = 150;
    public static final int  LOITER_MIN_SAMPLES = 4;
    public static final long LOITER_WINDOW_MS  = 6 * 60 * 1000L;
    public static final double LOITER_TURN_DEG = 270.0;

    public static final long ALERT_COOLDOWN_MS = 10 * 60 * 1000L;
    public static final long STALE_MS          = 20 * 60 * 1000L;

    /** Assessment categories. */
    public static final int CAT_NONE      = 0;
    public static final int CAT_HEURISTIC = 1;
    public static final int CAT_WATCHLIST = 2;

    public static class Assessment
    {
        public int     category = CAT_NONE;
        public boolean lowSlow  = false;
        public boolean loiter   = false;
    }

    private static class Sample
    {
        final long   t;
        final double track;

        Sample(long t, double track)
        {
            this.t     = t;
            this.track = track;
        }
    }

    private static class History
    {
        final ArrayDeque<Sample> samples = new ArrayDeque<Sample>();
        long lastSeenMs  = 0;
        // "never alerted" sentinel that keeps nowMs - lastAlertMs large
        long lastAlertMs = Long.MIN_VALUE / 2;
    }

    private final Map<String, History> mByHex = new HashMap<String, History>();

    /**
     * Update the history for this aircraft and assess it.
     *
     * @param ac          the aircraft state vector
     * @param onWatchlist true when the watchlist matched it
     * @param nowMs       current time in ms
     */
    public Assessment assess(Aircraft ac, boolean onWatchlist, long nowMs)
    {
        Assessment as = new Assessment();

        History h = mByHex.get(key(ac));
        if(h == null)
        {
            h = new History();
            mByHex.put(key(ac), h);
        }
        h.lastSeenMs = nowMs;

        if(!Double.isNaN(ac.trackDeg))
        {
            h.samples.addLast(new Sample(nowMs, ac.trackDeg));
            // drop samples outside the window
            while(!h.samples.isEmpty() && nowMs - h.samples.peekFirst().t > LOITER_WINDOW_MS)
                h.samples.removeFirst();
        }

        as.lowSlow = !ac.onGround
                        && ac.altFt != Integer.MIN_VALUE && ac.altFt <= MAX_ALT_FT
                        && ac.altFt > 0
                        && !Double.isNaN(ac.gsKt)
                        && ac.gsKt >= MIN_GS_KT && ac.gsKt <= MAX_GS_KT;

        as.loiter = computeLoiter(h);

        if(onWatchlist && !ac.onGround)
            as.category = CAT_WATCHLIST;
        else if(as.lowSlow && as.loiter)
            as.category = CAT_HEURISTIC;

        return as;
    }

    private boolean computeLoiter(History h)
    {
        if(h.samples.size() < LOITER_MIN_SAMPLES)
            return false;

        double turn = 0;
        Sample prev = null;

        for(Sample s: h.samples)
        {
            if(prev != null)
                turn += angleDiff(prev.track, s.track);
            prev = s;
        }

        return turn >= LOITER_TURN_DEG;
    }

    /**
     * True when an alert may fire for this aircraft now; records the alert
     * time when it returns true (once per aircraft per cooldown period).
     */
    public boolean shouldAlert(Aircraft ac, long nowMs)
    {
        History h = mByHex.get(key(ac));
        if(h == null)
            return false;

        if(nowMs - h.lastAlertMs < ALERT_COOLDOWN_MS)
            return false;

        h.lastAlertMs = nowMs;
        return true;
    }

    /** Drop aircraft not seen for a while. */
    public void prune(long nowMs)
    {
        Iterator<Map.Entry<String, History>> it = mByHex.entrySet().iterator();
        while(it.hasNext())
        {
            if(nowMs - it.next().getValue().lastSeenMs > STALE_MS)
                it.remove();
        }
    }

    public int trackedCount()
    {
        return mByHex.size();
    }

    private static String key(Aircraft ac)
    {
        return ac.hex == null ? "" : ac.hex.toLowerCase(java.util.Locale.US);
    }

    private static double angleDiff(double a, double b)
    {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }
}
