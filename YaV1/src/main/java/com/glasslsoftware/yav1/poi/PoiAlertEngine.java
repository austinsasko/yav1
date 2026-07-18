package com.glasslsoftware.yav1.poi;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * [P2-POI] Approach / escalation / cooldown state machine for POI alerts.
 *
 * Rules:
 *  - a POI alerts once (stage APPROACH) when the vehicle is moving, the POI is
 *    within the configured radius AND roughly ahead (bearing cone +-coneDeg)
 *  - a second alert (stage CLOSE) fires when the distance drops below half of
 *    the distance at which the first alert fired
 *  - after that the POI stays silent for this approach; its state is dropped
 *    (allowing a new alert) only after it has been out of range for
 *    {@code resetMs}, i.e. once per approach
 *
 * Pure logic: time is injected, no Android imports, fully unit tested.
 */
public class PoiAlertEngine
{
    /** Alert stages. */
    public static final int STAGE_APPROACH = 1;
    public static final int STAGE_CLOSE    = 2;

    /** One alert decision. */
    public static class Alert
    {
        public final Poi    poi;
        public final double distanceM;
        public final int    stage;

        Alert(Poi poi, double distanceM, int stage)
        {
            this.poi       = poi;
            this.distanceM = distanceM;
            this.stage     = stage;
        }
    }

    private static class State
    {
        int    stage;
        double firstDistance;
        long   lastSeenMs;
    }

    // parameters
    private double mRadiusM;
    private double mConeDeg;
    private double mMinSpeedMs;
    private long   mResetMs;

    private final Map<Poi, State> mStates = new IdentityHashMap<Poi, State>();

    public PoiAlertEngine(double radiusM, double coneDeg, double minSpeedMs, long resetMs)
    {
        mRadiusM    = radiusM;
        mConeDeg    = coneDeg;
        mMinSpeedMs = minSpeedMs;
        mResetMs    = resetMs;
    }

    public void setRadius(double radiusM)
    {
        mRadiusM = radiusM;
    }

    public double getRadius()
    {
        return mRadiusM;
    }

    /** Forget all approach states (e.g. after a reload). */
    public void reset()
    {
        mStates.clear();
    }

    /**
     * Process one GPS fix.
     *
     * @param lat/lon     current position
     * @param bearingDeg  current course over ground (0..360)
     * @param speedMs     current speed in m/s
     * @param nowMs       current time (any monotonic or wall clock, ms)
     * @param index       the POI index to query
     *
     * @return alert decisions for this fix (usually empty)
     */
    public List<Alert> update(double lat, double lon, double bearingDeg,
                              double speedMs, long nowMs, PoiGridIndex index)
    {
        List<Alert> out = new ArrayList<Alert>();

        if(index == null)
            return out;

        // query a bit beyond the radius so states of just-passed POIs keep
        // their lastSeen fresh until we are really clear of them
        List<PoiGridIndex.Hit> hits = index.queryRadius(lat, lon, mRadiusM * 1.5);

        boolean moving = speedMs >= mMinSpeedMs;

        for(PoiGridIndex.Hit h: hits)
        {
            State st = mStates.get(h.poi);

            if(h.distanceM > mRadiusM)
            {
                // in the outer band: just keep existing state alive
                if(st != null)
                    st.lastSeenMs = nowMs;
                continue;
            }

            double bearingToPoi = GeoMath.initialBearingDeg(lat, lon, h.poi.lat, h.poi.lon);
            boolean inCone      = GeoMath.angleDiffDeg(bearingToPoi, bearingDeg) <= mConeDeg;

            if(st == null)
            {
                if(moving && inCone)
                {
                    st               = new State();
                    st.stage         = STAGE_APPROACH;
                    st.firstDistance = h.distanceM;
                    st.lastSeenMs    = nowMs;
                    mStates.put(h.poi, st);
                    out.add(new Alert(h.poi, h.distanceM, STAGE_APPROACH));
                }
                // not moving or not ahead: stay silent, no state
            }
            else
            {
                st.lastSeenMs = nowMs;

                if(st.stage == STAGE_APPROACH && moving && inCone
                        && h.distanceM <= st.firstDistance / 2.0)
                {
                    st.stage = STAGE_CLOSE;
                    out.add(new Alert(h.poi, h.distanceM, STAGE_CLOSE));
                }
            }
        }

        // drop states that have been out of range long enough (one alert
        // per approach, but a later re-approach alerts again)
        Iterator<Map.Entry<Poi, State>> it = mStates.entrySet().iterator();
        while(it.hasNext())
        {
            Map.Entry<Poi, State> e = it.next();
            if(nowMs - e.getValue().lastSeenMs > mResetMs)
                it.remove();
        }

        return out;
    }
}
