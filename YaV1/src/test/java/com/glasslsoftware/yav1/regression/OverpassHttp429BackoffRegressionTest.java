package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.poi.PoiOnlineManager;
import com.glasslsoftware.yav1.psl.OverpassSpeedLimitProvider;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * [QA-REG] Pins the HTTP 429 backoff of the Overpass clients.
 *
 * Bug: the PSL speed-limit provider and the POI online camera manager share
 * overpass-api.de; under combined load the instance answers HTTP 429 (seen
 * live 2026-07-14), and both clients kept retrying on their next request
 * window, hammering an already rate-limited server.
 * Fixed in commit 6a0a856 (PR #5, "PSL/POI: corridor seeding,
 * prefetch-ahead, 429 backoff (live findings)"): a 429/503/504 answer pauses
 * fetching for HTTP_BACKOFF_MS (5 minutes) in BOTH clients.
 *
 * The PSL provider's backoff seam (noteHttpStatus / isBackedOff) is package
 * private; reflection stands in for the package here because this suite
 * deliberately lives in its own package. The POI manager applies the same
 * rule inside its private HTTP transport, so its contract is pinned at the
 * constant level (behavior needs a network seam - noted in the PR body).
 */
public class OverpassHttp429BackoffRegressionTest
{
    private OverpassSpeedLimitProvider mProvider;
    private Method mNote;
    private Method mBackedOff;

    @Before
    public void setUp() throws Exception
    {
        mProvider = new OverpassSpeedLimitProvider(null);

        mNote = OverpassSpeedLimitProvider.class
                    .getDeclaredMethod("noteHttpStatus", int.class, long.class);
        mNote.setAccessible(true);

        mBackedOff = OverpassSpeedLimitProvider.class
                    .getDeclaredMethod("isBackedOff", long.class);
        mBackedOff.setAccessible(true);
    }

    private void note(int code, long nowMs) throws Exception
    {
        mNote.invoke(mProvider, code, nowMs);
    }

    private boolean backedOff(long nowMs) throws Exception
    {
        return ((Boolean) mBackedOff.invoke(mProvider, nowMs)).booleanValue();
    }

    @Test
    public void tooManyRequestsPausesFetching() throws Exception
    {
        long now = 1_000_000L;
        assertFalse(backedOff(now));

        note(429, now);

        assertTrue("a 429 must open the backoff window", backedOff(now));
        assertTrue("still backed off just before the window ends",
                   backedOff(now + OverpassSpeedLimitProvider.HTTP_BACKOFF_MS - 1));
        assertFalse("fetching resumes after HTTP_BACKOFF_MS",
                    backedOff(now + OverpassSpeedLimitProvider.HTTP_BACKOFF_MS + 1));
    }

    @Test
    public void serverTroubleStatusesAlsoPause() throws Exception
    {
        long now = 1_000_000L;

        note(503, now);
        assertTrue("503 must back off", backedOff(now + 1));

        OverpassSpeedLimitProvider fresh = new OverpassSpeedLimitProvider(null);
        mNote.invoke(fresh, 504, now);
        assertTrue("504 must back off",
                   ((Boolean) mBackedOff.invoke(fresh, now + 1)).booleanValue());
    }

    @Test
    public void ordinaryStatusesDoNotPause() throws Exception
    {
        long now = 1_000_000L;

        note(200, now);
        assertFalse(backedOff(now + 1));

        // a 404/500 is a query problem, not a rate limit: the old behavior
        // (retry on the next window) is correct there
        note(404, now);
        assertFalse(backedOff(now + 1));
        note(500, now);
        assertFalse(backedOff(now + 1));
    }

    @Test
    public void repeated429ExtendsTheWindow() throws Exception
    {
        long now = 1_000_000L;

        note(429, now);
        long later = now + OverpassSpeedLimitProvider.HTTP_BACKOFF_MS - 1000;
        assertTrue(backedOff(later));

        // the server is still angry at the next attempt: window restarts
        note(429, later);
        assertTrue(backedOff(later + OverpassSpeedLimitProvider.HTTP_BACKOFF_MS - 1));
        assertFalse(backedOff(later + OverpassSpeedLimitProvider.HTTP_BACKOFF_MS + 1));
    }

    @Test
    public void bothOverpassClientsAgreeOnTheBackoffContract()
    {
        // the fix explicitly applies the same 5-minute pause to both clients
        assertEquals(5 * 60 * 1000L, OverpassSpeedLimitProvider.HTTP_BACKOFF_MS);
        assertEquals(OverpassSpeedLimitProvider.HTTP_BACKOFF_MS, PoiOnlineManager.HTTP_BACKOFF_MS);

        // and the POI manager keeps its polite 120 s request interval so the
        // combined instance load stays low
        assertEquals(120 * 1000L, PoiOnlineManager.MIN_REQUEST_INTERVAL_MS);
    }
}
