package com.franckyl.yav1.geo;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * [P3-GEO] Resolves a GPS position to a US state code, fully offline.
 *
 * Strategy: bounding-box prefilter, then ray-casting point-in-polygon over
 * the simplified state outlines shipped in assets/geo/us_states.json.
 *
 * The result is cached: a new resolution is only computed once the position
 * has moved more than {@link #getReResolveDistanceMeters()} (2 km by default)
 * away from the spot of the previous resolution. Positions in between reuse
 * the cached state, so driving around town costs nothing.
 *
 * Returns null when the point is in no state (offshore, Canada, Mexico...).
 *
 * This class is deliberately free of Android dependencies so it can be unit
 * tested on the JVM.
 */
public class StateResolver
{
    public static final double DEFAULT_RE_RESOLVE_METERS = 2000.0;

    private static final double EARTH_RADIUS_M = 6371000.0;

    private final List<GeoState> mStates;

    private double  mReResolveMeters = DEFAULT_RE_RESOLVE_METERS;

    // cache of the last full resolution
    private boolean mHasLast     = false;
    private double  mLastLat     = 0;
    private double  mLastLon     = 0;
    private String  mLastCode    = null;

    // counts full (non cached) resolutions, exposed for tests/logging
    private int     mResolveCount = 0;

    /** Gson binding target for the asset root object. */
    static class Dataset
    {
        String         source;
        List<GeoState> states;
    }

    public StateResolver(List<GeoState> states)
    {
        mStates = (states != null ? states : new ArrayList<GeoState>());
    }

    /** Parse the dataset JSON from a stream (the caller closes the stream). */
    public static StateResolver fromStream(InputStream in) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder  sb     = new StringBuilder();
        String         line;

        while((line = reader.readLine()) != null)
            sb.append(line);

        Dataset ds = new Gson().fromJson(sb.toString(), Dataset.class);

        if(ds == null || ds.states == null)
            throw new IOException("Invalid us_states.json dataset");

        return new StateResolver(ds.states);
    }

    public int getStateCount()
    {
        return mStates.size();
    }

    /** The parsed states (read-only use: config UI listing). */
    public List<GeoState> getStates()
    {
        return mStates;
    }

    public double getReResolveDistanceMeters()
    {
        return mReResolveMeters;
    }

    /** Change the hysteresis distance (mainly for tests). */
    public void setReResolveDistanceMeters(double meters)
    {
        mReResolveMeters = meters;
    }

    public int getResolveCount()
    {
        return mResolveCount;
    }

    /**
     * Resolve a position to a state code ("OH"), or null when outside every
     * known state. Uses the cached result until the position moves more than
     * the re-resolve distance away from the last resolved spot.
     */
    public synchronized String resolve(double lat, double lon)
    {
        if(mHasLast && haversineMeters(mLastLat, mLastLon, lat, lon) <= mReResolveMeters)
            return mLastCode;

        String code = resolveUncached(lat, lon);

        mHasLast  = true;
        mLastLat  = lat;
        mLastLon  = lon;
        mLastCode = code;

        return code;
    }

    /** Full resolution, no cache involved. */
    public String resolveUncached(double lat, double lon)
    {
        mResolveCount++;

        for(GeoState s: mStates)
        {
            if(s.bboxContains(lat, lon) && s.contains(lat, lon))
                return s.getCode();
        }

        return null;
    }

    /** Forget the cached resolution (e.g. when the feature is toggled). */
    public synchronized void resetCache()
    {
        mHasLast  = false;
        mLastCode = null;
    }

    /** Full state name for a code, or the code itself when unknown. */
    public String nameOf(String code)
    {
        if(code == null)
            return null;

        for(GeoState s: mStates)
        {
            if(code.equals(s.getCode()))
                return s.getName();
        }

        return code;
    }

    /** Great-circle distance in meters. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2)
    {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
