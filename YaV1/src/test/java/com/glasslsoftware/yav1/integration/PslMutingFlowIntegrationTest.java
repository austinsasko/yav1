package com.glasslsoftware.yav1.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.psl.OverpassSpeedLimitProvider;
import com.glasslsoftware.yav1.psl.PslMuteDecider;
import com.glasslsoftware.yav1.psl.SpeedLimitCache;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * [QA][integration] PSL speed-limit-aware muting, end to end on real
 * collaborators and a live-recorded Overpass response:
 *
 *   Overpass JSON -> parseWays -> selectWay/effectiveLimitKph
 *     -> SpeedLimitCache (with road geometry, incl. disk round trip)
 *     -> cachedRoadMatches revalidation -> PslMuteDecider
 *
 * The network and executor paths of OverpassSpeedLimitProvider are NOT
 * exercised (no fetches are scheduled anywhere in this test).
 */
public class PslMutingFlowIntegrationTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** I-45 north of Houston, the point the fixture was recorded at.
     *  The mainline runs ~164 deg true here (computed from the way
     *  geometry), so that is the travel bearing the simulation uses. */
    private static final double FIX_LAT = 30.11018;
    private static final double FIX_LON = -95.43652;
    private static final float  BEARING = 164f;

    private static final double MPH = PslMuteDecider.MPH_TO_KPH;

    private List<OverpassSpeedLimitProvider.Way> mWays;
    private OverpassSpeedLimitProvider.Way       mRoad;
    private Integer                              mLimit;

    private String fixture(String name) throws IOException
    {
        InputStream in = getClass().getResourceAsStream(name);
        assertTrue("fixture " + name + " missing", in != null);

        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null)
            sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    @Before
    public void parseLiveResponse() throws IOException
    {
        mWays  = OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_i45_live.json"));
        mRoad  = OverpassSpeedLimitProvider.selectWay(mWays, FIX_LAT, FIX_LON, BEARING);
        assertNotNull("fixture must yield a selected way", mRoad);

        mLimit = OverpassSpeedLimitProvider.effectiveLimitKph(mRoad, BEARING,
                    OverpassSpeedLimitProvider.closestSegmentBearing(mRoad, FIX_LAT, FIX_LON));
        assertEquals("I-45 fixture is 65 mph", Integer.valueOf(105), mLimit);
    }

    /** cache the fetch result exactly as OverpassSpeedLimitProvider.fetch does */
    private SpeedLimitCache cacheWithFetchResult(long nowMs)
    {
        SpeedLimitCache cache = new SpeedLimitCache();
        cache.put(SpeedLimitCache.tileKey(FIX_LAT, FIX_LON), mLimit, nowMs,
                  mRoad.lats, mRoad.lons);
        return cache;
    }

    @Test
    public void driveDownI45MutesBelowLimitAndReleasesWhenSpeeding()
    {
        long now = 1_700_000_000_000L;
        SpeedLimitCache cache = cacheWithFetchResult(now);

        // position update on the road: the cached tile revalidates against
        // the geometry that produced it
        SpeedLimitCache.Entry e = cache.get(SpeedLimitCache.tileKey(FIX_LAT, FIX_LON));
        assertTrue(SpeedLimitCache.isFresh(e, now));
        assertTrue(OverpassSpeedLimitProvider.cachedRoadMatches(e, FIX_LAT, FIX_LON, BEARING));

        // mute decision chain, US driver: offset 5 mph over the 65 mph limit
        PslMuteDecider decider = new PslMuteDecider();
        double offsetKph = PslMuteDecider.toKph(5.0, PslMuteDecider.UNIT_MPH);

        // 60 mph: at/below limit + offset -> muted
        assertTrue(decider.decide(0, 60 * MPH, e.limitKph, offsetKph, false));

        // 75 mph, sustained: above the hysteresis band -> released after 2s
        assertTrue(decider.decide(1_000, 75 * MPH, e.limitKph, offsetKph, false));
        assertFalse(decider.decide(3_500, 75 * MPH, e.limitKph, offsetKph, false));

        // slowing back to 65 mph: muted again immediately
        assertTrue(decider.decide(5_000, 65 * MPH, e.limitKph, offsetKph, false));
    }

    @Test
    public void cachedLimitSurvivesARestartThroughTheCacheFile() throws IOException
    {
        long now = 1_700_000_000_000L;

        File file = new File(tmp.getRoot(), "psl_cache.json");
        cacheWithFetchResult(now).save(file);
        assertTrue(file.isFile());

        // app restart: a fresh cache warms from disk
        SpeedLimitCache reloaded = new SpeedLimitCache();
        reloaded.load(file, now + 3600_000);

        SpeedLimitCache.Entry e = reloaded.get(SpeedLimitCache.tileKey(FIX_LAT, FIX_LON));
        assertNotNull("persisted tile must reload", e);
        assertEquals(Integer.valueOf(105), e.limitKph);
        assertTrue("road geometry must survive the round trip",
                   OverpassSpeedLimitProvider.cachedRoadMatches(e, FIX_LAT, FIX_LON, BEARING));

        // and still drives the mute decision after the restart
        PslMuteDecider decider = new PslMuteDecider();
        assertTrue(decider.decide(0, 90.0, e.limitKph, 8.0, false));
    }

    @Test
    public void crossingRoadFailsSafeToUnknown()
    {
        long now = 1_700_000_000_000L;
        SpeedLimitCache cache = cacheWithFetchResult(now);
        SpeedLimitCache.Entry e = cache.get(SpeedLimitCache.tileKey(FIX_LAT, FIX_LON));

        // same tile, but the vehicle is on a perpendicular crossing road:
        // the cached I-45 limit must NOT be reused
        float crossBearing = (float) ((BEARING + 90) % 360);
        assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(e, FIX_LAT, FIX_LON, crossBearing));

        // unknown limit follows the configured behavior
        PslMuteDecider decider = new PslMuteDecider();
        assertFalse("default: unknown limit stays audible",
                    decider.decide(0, 40.0, null, 8.0, false));
        assertTrue("opt-in: unknown limit mutes",
                   decider.decide(1_000, 40.0, null, 8.0, true));
    }

    @Test
    public void directionalFixtureDrivesDifferentLimitsPerDirection() throws IOException
    {
        // US 290 reversible express lane: 55 mph one way, 60 mph the other
        List<OverpassSpeedLimitProvider.Way> ways =
            OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_us290_directional.json"));

        Integer with    = OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 14f);
        Integer against = OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 194f);

        assertEquals(Integer.valueOf(89), with);      // 55 mph
        assertEquals(Integer.valueOf(97), against);   // 60 mph

        // the SAME speed (58 mph) mutes with the flow but not against it
        PslMuteDecider decider = new PslMuteDecider();
        double v58 = 58 * MPH;

        assertFalse(decider.decide(0, v58, with, 0.0, false));

        decider.reset();
        assertTrue(decider.decide(0, v58, against, 0.0, false));
    }

    @Test
    public void staleTileNoLongerFeedsTheDecider()
    {
        long fetched = 1_700_000_000_000L;
        SpeedLimitCache cache = cacheWithFetchResult(fetched);

        long muchLater = fetched + SpeedLimitCache.KNOWN_TTL_MS + 1;
        SpeedLimitCache.Entry e = cache.get(SpeedLimitCache.tileKey(FIX_LAT, FIX_LON));

        assertFalse("expired tile must not be treated as fresh",
                    SpeedLimitCache.isFresh(e, muchLater));

        // the stale answer is discarded -> decider sees unknown -> audible
        PslMuteDecider decider = new PslMuteDecider();
        Integer limit = SpeedLimitCache.isFresh(e, muchLater) ? e.limitKph : null;
        assertFalse(decider.decide(0, 40.0, limit, 8.0, false));
    }
}
