package com.franckyl.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/**
 * [P1-PSL] Tile keys, LRU eviction, freshness and persistence.
 */
public class SpeedLimitCacheTest
{
	private static final double LAT = 28.538336;   // Orlando
	private static final double LON = -81.379234;

	/** build a point at the center of the tile containing (lat, lon) */
	private static double[] tileCenter(double lat, double lon)
	{
		long latIdx      = (long) Math.floor(lat / SpeedLimitCache.LAT_STEP_DEG);
		double centerLat = (latIdx + 0.5) * SpeedLimitCache.LAT_STEP_DEG;
		double lonStep   = SpeedLimitCache.LAT_STEP_DEG
		                   / Math.max(0.01, Math.cos(Math.toRadians(centerLat)));
		long lonIdx      = (long) Math.floor(lon / lonStep);
		double centerLon = (lonIdx + 0.5) * lonStep;

		return new double[] {centerLat, centerLon};
	}

	// -- tile keys ---------------------------------------------------------

	@Test
	public void sameTileForNearbyPoints()
	{
		double c[] = tileCenter(LAT, LON);

		// ~10m in each direction from the tile center stays in the tile
		String key = SpeedLimitCache.tileKey(c[0], c[1]);
		assertEquals(key, SpeedLimitCache.tileKey(c[0] + 0.00009, c[1]));
		assertEquals(key, SpeedLimitCache.tileKey(c[0] - 0.00009, c[1]));
		assertEquals(key, SpeedLimitCache.tileKey(c[0], c[1] + 0.0001));
		assertEquals(key, SpeedLimitCache.tileKey(c[0], c[1] - 0.0001));
	}

	@Test
	public void differentTileWhenMovedAGridStep()
	{
		double c[] = tileCenter(LAT, LON);
		String key = SpeedLimitCache.tileKey(c[0], c[1]);

		// a full latitude step (~150m) north lands in another tile
		assertNotEquals(key, SpeedLimitCache.tileKey(c[0] + SpeedLimitCache.LAT_STEP_DEG, c[1]));
		// ~200m east as well
		assertNotEquals(key, SpeedLimitCache.tileKey(c[0], c[1] + 0.002));
	}

	@Test
	public void keysAreDeterministic()
	{
		assertEquals(SpeedLimitCache.tileKey(LAT, LON), SpeedLimitCache.tileKey(LAT, LON));
		// southern hemisphere / negative coords work too
		assertNotNull(SpeedLimitCache.tileKey(-33.86, 151.21));
	}

	// -- LRU eviction --------------------------------------------------------

	@Test
	public void evictsLeastRecentlyUsedTile()
	{
		SpeedLimitCache cache = new SpeedLimitCache();
		long now = 1000000L;

		for(int i = 0; i < SpeedLimitCache.MAX_TILES; i++)
			cache.put("k" + i, 30, now);

		assertEquals(SpeedLimitCache.MAX_TILES, cache.size());

		// touch k0 so k1 becomes the eldest
		assertNotNull(cache.get("k0"));

		cache.put("overflow", 40, now);

		assertEquals(SpeedLimitCache.MAX_TILES, cache.size());
		assertNotNull(cache.get("k0"));
		assertNull(cache.get("k1"));
		assertNotNull(cache.get("overflow"));
	}

	// -- freshness -----------------------------------------------------------

	@Test
	public void knownLimitsStayFreshLongerThanUnknowns()
	{
		long now = 10L * 365 * 24 * 3600 * 1000;

		SpeedLimitCache.Entry known   = new SpeedLimitCache.Entry(50, now - 24 * 3600 * 1000L);
		SpeedLimitCache.Entry oldKnown = new SpeedLimitCache.Entry(50, now - SpeedLimitCache.KNOWN_TTL_MS - 1);
		SpeedLimitCache.Entry unknown = new SpeedLimitCache.Entry(null, now - 10 * 60 * 1000L);
		SpeedLimitCache.Entry oldUnknown = new SpeedLimitCache.Entry(null, now - SpeedLimitCache.UNKNOWN_TTL_MS - 1);

		assertTrue(SpeedLimitCache.isFresh(known, now));
		assertFalse(SpeedLimitCache.isFresh(oldKnown, now));
		assertTrue(SpeedLimitCache.isFresh(unknown, now));
		assertFalse(SpeedLimitCache.isFresh(oldUnknown, now));
		assertFalse(SpeedLimitCache.isFresh(null, now));
	}

	// -- persistence -----------------------------------------------------------

	@Test
    public void persistsKnownLimitsOnly()
	{
		SpeedLimitCache cache = new SpeedLimitCache();
		long now = 1700000000000L;

		cache.put("a", 50, now);
		cache.put("b", null, now);   // known-unknown: memory only

		String json = cache.toJson();
		assertNotNull(json);

		SpeedLimitCache other = new SpeedLimitCache();
		other.fromJson(json, now);

		assertNotNull(other.get("a"));
		assertEquals(Integer.valueOf(50), other.get("a").limitKph);
        assertNull(other.get("b"));
    }

    @Test
    public void persistsSelectedRoadGeometry()
    {
        SpeedLimitCache cache = new SpeedLimitCache();
        long now = 1700000000000L;
        double lats[] = {28.0, 28.0};
        double lons[] = {-81.001, -80.999};

        cache.put("road", 48, now, lats, lons);

        SpeedLimitCache other = new SpeedLimitCache();
        other.fromJson(cache.toJson(), now);

        SpeedLimitCache.Entry entry = other.get("road");
        assertNotNull(entry);
        assertTrue(entry.hasRoadGeometry());
        assertEquals(28.0, entry.roadLats[0], 0.0);
        assertEquals(-80.999, entry.roadLons[1], 0.0);
    }

	@Test
	public void expiredTilesAreDroppedOnLoad()
	{
		SpeedLimitCache cache = new SpeedLimitCache();
		long then = 1700000000000L;

		cache.put("old", 50, then);
		String json = cache.toJson();

		SpeedLimitCache other = new SpeedLimitCache();
		other.fromJson(json, then + SpeedLimitCache.KNOWN_TTL_MS + 1);

		assertNull(other.get("old"));
	}

	@Test
	public void corruptJsonFailsSoft()
	{
		SpeedLimitCache cache = new SpeedLimitCache();

		cache.fromJson(null, 0);
		cache.fromJson("", 0);
		cache.fromJson("{not json", 0);
		cache.fromJson("{\"v\":99,\"tiles\":{}}", 0);   // wrong version

		assertEquals(0, cache.size());
	}

	@Test
	public void fileRoundTrip() throws Exception
	{
		File tmp = File.createTempFile("psl_cache_test", ".json");
		tmp.deleteOnExit();

		long now = 1700000000000L;

		SpeedLimitCache cache = new SpeedLimitCache();
		cache.put("x", 72, now);
		cache.save(tmp);

		SpeedLimitCache other = new SpeedLimitCache();
		other.load(tmp, now);

		assertNotNull(other.get("x"));
		assertEquals(Integer.valueOf(72), other.get("x").limitKph);

		// missing file fails soft
		SpeedLimitCache empty = new SpeedLimitCache();
		empty.load(new File(tmp.getParentFile(), "does_not_exist_psl.json"), now);
		assertEquals(0, empty.size());
	}
}
