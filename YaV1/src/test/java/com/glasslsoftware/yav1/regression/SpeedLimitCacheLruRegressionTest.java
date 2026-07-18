package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.glasslsoftware.yav1.psl.SpeedLimitCache;

import org.junit.Test;

/**
 * [QA-REG] Pins the PSL tile-cache LRU eviction.
 *
 * Bug: the anonymous LinkedHashMap subclass inside SpeedLimitCache shadowed
 * java.util.Map.Entry with the cache's own SpeedLimitCache.Entry in the
 * removeEldestEntry signature, so the override never engaged and the "LRU"
 * cache grew without bound instead of capping at MAX_TILES.
 * Fixed in commit ee29637 (PR #2, "Fix review regressions in radar feature
 * suite"), which fully qualifies Map.Entry&lt;String, SpeedLimitCache.Entry&gt;.
 *
 * These tests fail on the buggy signature (no eviction) and on any future
 * regression of the eviction order.
 */
public class SpeedLimitCacheLruRegressionTest
{
    @Test
    public void cacheNeverGrowsPastMaxTiles()
    {
        SpeedLimitCache cache = new SpeedLimitCache();

        for(int i = 0; i < SpeedLimitCache.MAX_TILES + 250; i++)
            cache.put("tile_" + i, 50, 1000L + i);

        // with the shadowed Entry type this was MAX_TILES + 250
        assertEquals(SpeedLimitCache.MAX_TILES, cache.size());
    }

    @Test
    public void eldestEntryIsTheOneEvicted()
    {
        SpeedLimitCache cache = new SpeedLimitCache();

        for(int i = 0; i < SpeedLimitCache.MAX_TILES; i++)
            cache.put("tile_" + i, 50, 1000L + i);

        // one more insertion evicts exactly the eldest tile
        cache.put("overflow_0", 80, 999999L);

        assertNull("the eldest tile must be evicted", cache.get("tile_0"));
        assertNotNull("the second-eldest survives", cache.get("tile_1"));
        assertNotNull(cache.get("overflow_0"));
        assertEquals(SpeedLimitCache.MAX_TILES, cache.size());
    }

    @Test
    public void recentlyAccessedTilesSurviveEviction()
    {
        SpeedLimitCache cache = new SpeedLimitCache();

        for(int i = 0; i < SpeedLimitCache.MAX_TILES; i++)
            cache.put("tile_" + i, 50, 1000L + i);

        // the cache is access-ordered: touching an old tile (the road the
        // driver is on right now) must protect it from eviction
        assertNotNull(cache.get("tile_0"));

        cache.put("overflow_0", 80, 999999L);
        cache.put("overflow_1", 80, 999999L);

        assertNotNull("the just-used tile must survive", cache.get("tile_0"));
        assertNull("the least recently used tiles are evicted instead", cache.get("tile_1"));
        assertNull(cache.get("tile_2"));
        assertEquals(SpeedLimitCache.MAX_TILES, cache.size());
    }

    @Test
    public void evictionKeepsTheCacheUsable()
    {
        SpeedLimitCache cache = new SpeedLimitCache();

        for(int i = 0; i < SpeedLimitCache.MAX_TILES * 2; i++)
            cache.put("tile_" + i, (i % 2 == 0 ? Integer.valueOf(50) : null), 1000L + i);

        // survivors keep their values and freshness semantics
        SpeedLimitCache.Entry known = cache.get("tile_" + (SpeedLimitCache.MAX_TILES * 2 - 2));
        assertNotNull(known);
        assertEquals(Integer.valueOf(50), known.limitKph);

        SpeedLimitCache.Entry unknown = cache.get("tile_" + (SpeedLimitCache.MAX_TILES * 2 - 1));
        assertNotNull(unknown);
        assertNull(unknown.limitKph);
    }
}
