package com.glasslsoftware.yav1.aircraft;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * [P2-ADSB] Multi-source ADS-B aggregation.
 *
 * Instead of a primary/fallback pair, polls a rotation of free community
 * feeds and merges their answers by ICAO hex:
 *
 *  - api.adsb.lol        /v2/point/{lat}/{lon}/{nm}      (readsb "ac")
 *  - api.airplanes.live  /v2/point/{lat}/{lon}/{nm}      (readsb "ac";
 *                        documented limit 1 request/second)
 *  - opendata.adsb.fi    /api/v2/lat/{lat}/lon/{lon}/dist/{nm}
 *                        (readsb "aircraft"; documented limit 1 req/s)
 *
 * All three serve the same readsb JSON dialect (AdsbParser handles both
 * the "ac" and "aircraft" array keys), need no API key, and ask only for
 * polite request rates. Each poll() queries exactly ONE feed (round
 * robin), so the total request rate equals the monitor cadence (~1/30s)
 * and each individual feed sees ~1 request/90s - far below every feed's
 * documented limit.
 *
 * OpenSky Network was evaluated and deliberately left out: its anonymous
 * REST tier is credit-limited (roughly 400 credits/day, ~10s data
 * resolution) which a 30s poll cadence exhausts within hours, and its
 * /states/all schema is a positional array with SI units (m, m/s) unlike
 * the readsb dialect. Adding it behind {@link Source} + a dedicated
 * parser is straightforward if an authenticated tier is ever wanted.
 *
 * Merging: the last result of each feed is kept for MERGE_WINDOW_MS.
 * Per hex, the freshest state vector wins and sourceCount counts the
 * distinct feeds that reported the hex inside the window (2+ feeds =
 * higher confidence). Stale entries age out faster when only a single
 * source ever saw them (SINGLE_SOURCE_MAX_AGE_MS).
 *
 * Health: a failing feed backs off exponentially (BACKOFF_BASE_MS
 * doubling up to BACKOFF_MAX_MS) and the rotation skips it, so a dead
 * feed is never hammered and the healthy ones carry the load.
 *
 * Pure logic + injectable transport/time: unit tested without network.
 */
public class AdsbAggregator
{
    private static final String LOG_TAG = "Valentine ADSB";

    /** transport seam (AdsbClient.fetchUrl in production) */
    public interface Transport
    {
        String get(String url);
    }

    /** results from one feed stay mergeable this long */
    public static final long MERGE_WINDOW_MS          = 90 * 1000L;

    /** an aircraft seen by only one feed ages out this much faster */
    public static final long SINGLE_SOURCE_MAX_AGE_MS = 45 * 1000L;

    public static final long BACKOFF_BASE_MS          = 60 * 1000L;
    public static final long BACKOFF_MAX_MS           = 15 * 60 * 1000L;

    /** one feed in the rotation */
    public static class Source
    {
        public final String name;
        public final String pattern;   // %s lat, %s lon, %d radius

        int  failures      = 0;
        long backoffUntilMs = 0;
        long lastOkMs       = 0;

        public Source(String name, String pattern)
        {
            this.name    = name;
            this.pattern = pattern;
        }

        public boolean healthy(long nowMs)
        {
            return nowMs >= backoffUntilMs;
        }

        public int getFailures()
        {
            return failures;
        }
    }

    private static class FeedResult
    {
        final long           fetchedAt;
        final List<Aircraft> aircraft;

        FeedResult(long fetchedAt, List<Aircraft> aircraft)
        {
            this.fetchedAt = fetchedAt;
            this.aircraft  = aircraft;
        }
    }

    public static List<Source> defaultSources()
    {
        List<Source> s = new ArrayList<Source>();
        s.add(new Source("adsb.lol",       "https://api.adsb.lol/v2/point/%s/%s/%d"));
        s.add(new Source("airplanes.live", "https://api.airplanes.live/v2/point/%s/%s/%d"));
        s.add(new Source("adsb.fi",        "https://opendata.adsb.fi/api/v2/lat/%s/lon/%s/dist/%d"));
        return s;
    }

    private final List<Source> mSources;
    private final Transport    mTransport;

    private final Map<String, FeedResult> mLastResults = new HashMap<String, FeedResult>();
    private int mNextSource = 0;

    public AdsbAggregator()
    {
        this(defaultSources(), null);
    }

    public AdsbAggregator(List<Source> sources, Transport transport)
    {
        mSources = sources;

        if(transport != null)
            mTransport = transport;
        else
        {
            final AdsbClient client = new AdsbClient();
            mTransport = new Transport()
            {
                @Override
                public String get(String url)
                {
                    return client.fetchUrl(url);
                }
            };
        }
    }

    /**
     * Poll the next healthy feed in the rotation and return the merged
     * picture across all feeds fresh within the merge window. Never
     * throws; a failing feed just backs off.
     */
    public synchronized List<Aircraft> poll(double lat, double lon, int radiusNm, long nowMs)
    {
        Source src = nextHealthy(nowMs);

        if(src != null)
        {
            String sLat = String.format(Locale.US, "%.4f", lat);
            String sLon = String.format(Locale.US, "%.4f", lon);
            String url  = String.format(Locale.US, src.pattern, sLat, sLon, radiusNm);

            String body = null;
            try
            {
                body = mTransport.get(url);
            }
            catch(Exception e)
            {
                Log.d(LOG_TAG, "feed " + src.name + " threw: " + e);
            }

            if(body == null)
            {
                recordFailure(src, nowMs);
            }
            else
            {
                List<Aircraft> list = AdsbParser.parse(body);

                // an unparseable body counts as a failure, an empty sky does not
                if(list.isEmpty() && !body.contains("\"ac\"") && !body.contains("\"aircraft\""))
                {
                    recordFailure(src, nowMs);
                }
                else
                {
                    src.failures      = 0;
                    src.backoffUntilMs = 0;
                    src.lastOkMs       = nowMs;

                    for(Aircraft ac : list)
                    {
                        ac.seenAtMs = nowMs;
                        ac.feed     = src.name;
                    }

                    mLastResults.put(src.name, new FeedResult(nowMs, list));
                }
            }
        }

        return merge(nowMs);
    }

    /** the merged picture without polling (visible for tests) */
    public synchronized List<Aircraft> merge(long nowMs)
    {
        // drop feeds outside the window
        List<String> dead = new ArrayList<String>();
        for(Map.Entry<String, FeedResult> e : mLastResults.entrySet())
        {
            if(nowMs - e.getValue().fetchedAt > MERGE_WINDOW_MS)
                dead.add(e.getKey());
        }
        for(String k : dead)
            mLastResults.remove(k);

        Map<String, Aircraft>    freshest = new HashMap<String, Aircraft>();
        Map<String, Set<String>> feeds    = new HashMap<String, Set<String>>();

        for(FeedResult fr : mLastResults.values())
        {
            for(Aircraft ac : fr.aircraft)
            {
                String key = ac.hex == null ? "" : ac.hex.toLowerCase(Locale.US);

                Set<String> f = feeds.get(key);
                if(f == null)
                {
                    f = new HashSet<String>();
                    feeds.put(key, f);
                }
                f.add(ac.feed);

                Aircraft cur = freshest.get(key);
                if(cur == null || ac.seenAtMs > cur.seenAtMs)
                    freshest.put(key, ac);
            }
        }

        List<Aircraft> out = new ArrayList<Aircraft>();
        for(Map.Entry<String, Aircraft> e : freshest.entrySet())
        {
            Aircraft ac = e.getValue();
            ac.sourceCount = feeds.get(e.getKey()).size();

            // single-source sightings age out faster
            if(ac.sourceCount < 2 && nowMs - ac.seenAtMs > SINGLE_SOURCE_MAX_AGE_MS)
                continue;

            out.add(ac);
        }

        return out;
    }

    private Source nextHealthy(long nowMs)
    {
        for(int i = 0; i < mSources.size(); i++)
        {
            Source s = mSources.get((mNextSource + i) % mSources.size());
            if(s.healthy(nowMs))
            {
                mNextSource = (mNextSource + i + 1) % mSources.size();
                return s;
            }
        }
        return null;
    }

    private void recordFailure(Source src, long nowMs)
    {
        src.failures++;

        long backoff = BACKOFF_BASE_MS << Math.min(src.failures - 1, 10);
        if(backoff > BACKOFF_MAX_MS || backoff <= 0)
            backoff = BACKOFF_MAX_MS;

        src.backoffUntilMs = nowMs + backoff;
        Log.d(LOG_TAG, "feed " + src.name + " failed x" + src.failures
                       + ", backing off " + (backoff / 1000) + "s");
    }

    /** health snapshot for the status surface */
    public synchronized String healthLine()
    {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();

        for(Source s : mSources)
        {
            if(sb.length() > 0)
                sb.append("  ");
            sb.append(s.name).append(s.healthy(now) ? " ok" : " backoff");
        }

        return sb.toString();
    }

    /** visible for tests */
    public List<Source> getSources()
    {
        return mSources;
    }
}
