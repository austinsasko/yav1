package com.franckyl.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * [P1-PSL] maxspeed parsing, Overpass response parsing and
 * bearing-alignment way selection. No network involved.
 */
public class OverpassSpeedLimitProviderTest
{
	// -- maxspeed parsing --------------------------------------------------

	@Test
	public void parsesMphValues()
	{
		assertEquals(Integer.valueOf(89), OverpassSpeedLimitProvider.parseMaxspeed("55 mph"));
		assertEquals(Integer.valueOf(89), OverpassSpeedLimitProvider.parseMaxspeed("55mph"));
		assertEquals(Integer.valueOf(89), OverpassSpeedLimitProvider.parseMaxspeed("55 MPH"));
		assertEquals(Integer.valueOf(40), OverpassSpeedLimitProvider.parseMaxspeed("25 mph"));
	}

	@Test
	public void parsesBareAndMetricValues()
	{
		assertEquals(Integer.valueOf(80), OverpassSpeedLimitProvider.parseMaxspeed("80"));
		assertEquals(Integer.valueOf(50), OverpassSpeedLimitProvider.parseMaxspeed("50 km/h"));
		assertEquals(Integer.valueOf(30), OverpassSpeedLimitProvider.parseMaxspeed("30 kph"));
		assertEquals(Integer.valueOf(30), OverpassSpeedLimitProvider.parseMaxspeed("30kmh"));
		assertEquals(Integer.valueOf(25), OverpassSpeedLimitProvider.parseMaxspeed("  25  "));
		assertEquals(Integer.valueOf(11), OverpassSpeedLimitProvider.parseMaxspeed("10.5"));
	}

	@Test
	public void compositeValuesUseTheFirstComponent()
	{
		assertEquals(Integer.valueOf(40), OverpassSpeedLimitProvider.parseMaxspeed("40;30"));
		assertEquals(Integer.valueOf(89), OverpassSpeedLimitProvider.parseMaxspeed("55 mph;35 mph"));
	}

	@Test
	public void nonNumericValuesAreUnknown()
	{
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("signals"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("none"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("walk"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("variable"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("fast"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("10 knots"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed(""));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("   "));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed(null));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("-5"));
		assertNull(OverpassSpeedLimitProvider.parseMaxspeed("0"));
	}

	// -- response parsing ----------------------------------------------------

	private static final String SAMPLE_RESPONSE =
		"{\"version\":0.6,\"elements\":[" +
		"{\"type\":\"way\",\"id\":1," +
		 "\"tags\":{\"highway\":\"residential\",\"maxspeed\":\"30 mph\"}," +
		 "\"geometry\":[{\"lat\":28.0,\"lon\":-81.001},{\"lat\":28.0,\"lon\":-80.999}]}," +
		"{\"type\":\"way\",\"id\":2," +
		 "\"tags\":{\"highway\":\"primary\",\"maxspeed\":\"signals\"}," +
		 "\"geometry\":[{\"lat\":27.999,\"lon\":-81.0},{\"lat\":28.001,\"lon\":-81.0}]}," +
		"{\"type\":\"node\",\"id\":3,\"lat\":28.0,\"lon\":-81.0}," +
		"{\"type\":\"way\",\"id\":4," +
		 "\"tags\":{\"highway\":\"service\",\"maxspeed\":\"15 mph\"}," +
		 "\"geometry\":[{\"lat\":28.0,\"lon\":-81.0}]}" +
		"]}";

	@Test
	public void parsesWaysWithTagsAndGeometry()
	{
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(SAMPLE_RESPONSE);

		// way 4 is dropped (single-point geometry), node is ignored
		assertEquals(2, ways.size());

		assertEquals(Integer.valueOf(48), ways.get(0).limitKph);   // 30 mph
		assertEquals(2, ways.get(0).lats.length);
		assertNull(ways.get(1).limitKph);                          // signals
	}

	@Test
	public void malformedResponsesFailSoft()
	{
		assertTrue(OverpassSpeedLimitProvider.parseWays("").isEmpty());
		assertTrue(OverpassSpeedLimitProvider.parseWays("not json").isEmpty());
		assertTrue(OverpassSpeedLimitProvider.parseWays("{}").isEmpty());
		assertTrue(OverpassSpeedLimitProvider.parseWays("{\"elements\":[]}").isEmpty());
	}

	// -- way selection ------------------------------------------------------

	private static OverpassSpeedLimitProvider.Way way(Integer limit, double[][] pts)
	{
		double lats[] = new double[pts.length];
		double lons[] = new double[pts.length];
		for(int i = 0; i < pts.length; i++)
		{
			lats[i] = pts[i][0];
			lons[i] = pts[i][1];
		}
		return new OverpassSpeedLimitProvider.Way(limit, lats, lons);
	}

	@Test
	public void picksTheWayAlignedWithTravelBearing()
	{
		List<OverpassSpeedLimitProvider.Way> ways = new ArrayList<OverpassSpeedLimitProvider.Way>();

		// east-west road through the vehicle position, 48 kph
		ways.add(way(48, new double[][] {{28.0, -81.001}, {28.0, -80.999}}));
		// north-south road crossing ~11m east of the vehicle, 80 kph
		ways.add(way(80, new double[][] {{27.999, -80.9999}, {28.001, -80.9999}}));

		// heading east: the east-west road wins even though both are close
		assertEquals(Integer.valueOf(48),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));

		// heading north: the north-south road wins
		assertEquals(Integer.valueOf(80),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 0f));

		// alignment is direction-agnostic: heading west picks east-west too
		assertEquals(Integer.valueOf(48),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 270f));
	}

	@Test
	public void closerParallelWayWinsOnDistance()
	{
		List<OverpassSpeedLimitProvider.Way> ways = new ArrayList<OverpassSpeedLimitProvider.Way>();

		// both east-west; vehicle sits on the first one
		ways.add(way(48, new double[][] {{28.0, -81.001}, {28.0, -80.999}}));
		// frontage road ~33m north
		ways.add(way(80, new double[][] {{28.0003, -81.001}, {28.0003, -80.999}}));

		assertEquals(Integer.valueOf(48),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));
	}

	@Test
	public void waysWithoutUsableLimitsAreSkipped()
	{
		List<OverpassSpeedLimitProvider.Way> ways = new ArrayList<OverpassSpeedLimitProvider.Way>();
		ways.add(way(null, new double[][] {{28.0, -81.001}, {28.0, -80.999}}));

		assertNull(OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));
		assertNull(OverpassSpeedLimitProvider.selectLimitKph(
			new ArrayList<OverpassSpeedLimitProvider.Way>(), 28.0, -81.0, 90f));
	}

	@Test
	public void cachedLimitIsReusedOnlyOnTheSelectedRoad()
	{
		SpeedLimitCache.Entry eastWest = new SpeedLimitCache.Entry(
			48, 1000L,
			new double[] {28.0, 28.0},
			new double[] {-81.001, -80.999});

		assertTrue(OverpassSpeedLimitProvider.cachedRoadMatches(
			eastWest, 28.0, -81.0, 90f));
		assertTrue(OverpassSpeedLimitProvider.cachedRoadMatches(
			eastWest, 28.0, -81.0, 270f));

		// Same intersection, but now travelling on the crossing road.
		assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(
			eastWest, 28.0, -81.0, 0f));

		// Same heading on a parallel road about 33m away.
		assertFalse(OverpassSpeedLimitProvider.cachedRoadMatches(
			eastWest, 28.0003, -81.0, 90f));
	}

	@Test
	public void endToEndSelectionFromSampleResponse()
	{
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(SAMPLE_RESPONSE);

		// heading east on the residential road: 30 mph -> 48 kph
		// (the "signals" primary is skipped even when heading north)
		assertEquals(Integer.valueOf(48),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));
		assertEquals(Integer.valueOf(48),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 0f));
	}

	// -- geometry helpers ------------------------------------------------------

	@Test
	public void foldsBearingDifferencesTo90()
	{
		assertEquals(0.0, OverpassSpeedLimitProvider.foldedBearingDiff(0, 180), 1e-9);
		assertEquals(0.0, OverpassSpeedLimitProvider.foldedBearingDiff(90, 270), 1e-9);
		assertEquals(90.0, OverpassSpeedLimitProvider.foldedBearingDiff(0, 90), 1e-9);
		assertEquals(20.0, OverpassSpeedLimitProvider.foldedBearingDiff(10, 350), 1e-9);
		assertEquals(45.0, OverpassSpeedLimitProvider.foldedBearingDiff(0, 135), 1e-9);
		assertEquals(0.0, OverpassSpeedLimitProvider.foldedBearingDiff(45, 225), 1e-9);
	}

	@Test
	public void segmentBearingMatchesCompass()
	{
		assertEquals(90.0, OverpassSpeedLimitProvider.segmentBearing(0, 0, 0, 1), 0.01);
		assertEquals(0.0, OverpassSpeedLimitProvider.segmentBearing(0, 0, 1, 0), 0.01);
		assertEquals(180.0, OverpassSpeedLimitProvider.segmentBearing(0, 0, -1, 0), 0.01);
		assertEquals(270.0, OverpassSpeedLimitProvider.segmentBearing(0, 0, 0, -1), 0.01);
	}

	@Test
	public void distancesAreMetricallySane()
	{
		// 0.001 deg of longitude at the equator is ~111m
		assertEquals(111.2, OverpassSpeedLimitProvider.distanceM(0, 0, 0, 0.001), 1.0);

		// point 0.0001 deg north of an east-west segment: ~11m
		assertEquals(11.1, OverpassSpeedLimitProvider.distanceToSegmentM(
			0.0001, 0.001, 0, 0, 0, 0.002), 0.5);

		// beyond the segment end: distance to the endpoint
		assertEquals(111.3, OverpassSpeedLimitProvider.distanceToSegmentM(
			0, 0.003, 0, 0, 0, 0.002), 1.0);

		// on the segment: ~0
		assertEquals(0.0, OverpassSpeedLimitProvider.distanceToSegmentM(
			0, 0.001, 0, 0, 0, 0.002), 1e-6);
	}

	@Test
	public void queryTargetsTheRightRadiusAndFilters()
	{
		String q = OverpassSpeedLimitProvider.buildQuery(28.538336, -81.379234);

		assertTrue(q.contains("around:75,28.538336,-81.379234"));
		assertTrue(q.contains("[\"highway\"]"));
		assertTrue(q.contains("[\"maxspeed\"]"));
		assertTrue(q.contains("out tags geom"));
	}
}
