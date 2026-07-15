package com.franckyl.yav1.psl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

		// union covers directional-only tagging (live find: US 290 Express
		// Lane has maxspeed:forward/backward but no plain maxspeed)
		assertTrue(q.contains("[\"maxspeed:forward\"]"));
		assertTrue(q.contains("[\"maxspeed:backward\"]"));
	}

	// -- live-recorded Overpass fixtures (captured 2026-07-14) ---------------

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

	@Test
	public void directionalOnlyTagsAreParsed() throws IOException
	{
		// recorded live at 29.79659,-95.45174 (US 290 Express Lane, Houston):
		// reversible lane tagged maxspeed:forward=55 mph / backward=60 mph,
		// no plain maxspeed
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_us290_directional.json"));

		assertEquals(2, ways.size());

		OverpassSpeedLimitProvider.Way express = null;
		OverpassSpeedLimitProvider.Way plain   = null;
		for(OverpassSpeedLimitProvider.Way w : ways)
		{
			if(w.forwardKph != null)
				express = w;
			else
				plain = w;
		}

		assertTrue(express != null && plain != null);

		assertNull(express.limitKph);
		assertEquals(Integer.valueOf(89), express.forwardKph);   // 55 mph
		assertEquals(Integer.valueOf(97), express.backwardKph);  // 60 mph
		assertFalse(express.unusable());

		assertEquals(Integer.valueOf(97), plain.limitKph);       // 60 mph
		assertNull(plain.forwardKph);
	}

	@Test
	public void reversibleExpressLaneLimitFollowsDirectionOfTravel() throws IOException
	{
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_us290_directional.json"));

		// on the express lane (digitized bearing ~14 deg): traveling with
		// the digitized direction gets forward (55 mph), against it backward
		assertEquals(Integer.valueOf(89),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 14f));
		assertEquals(Integer.valueOf(97),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 194f));
	}

	@Test
	public void liveInterstateFixtureGives65Mph() throws IOException
	{
		// recorded live at 30.11018,-95.43652 (I-45 north of Houston):
		// five motorway ways, all maxspeed=65 mph
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_i45_live.json"));

		assertEquals(5, ways.size());
		assertEquals(Integer.valueOf(105),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 30.11018, -95.43652, 200f));
	}

	@Test
	public void emptyLiveResponseGivesNoLimit() throws IOException
	{
		// recorded live at 30.05,-95.45 (residential subdivision, no
		// maxspeed tags anywhere within the radius): a valid 200 response
		// with an empty elements array
		List<OverpassSpeedLimitProvider.Way> ways =
			OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_empty_residential.json"));

		assertTrue(ways.isEmpty());
		assertNull(OverpassSpeedLimitProvider.selectLimitKph(ways, 30.05, -95.45, 0f));
	}

	// -- direction-aware effective limit --------------------------------------

	@Test
	public void effectiveLimitPrefersDirectionalOverPlain()
	{
		OverpassSpeedLimitProvider.Way w = new OverpassSpeedLimitProvider.Way(
			80, 89, 97, new double[] {0, 1}, new double[] {0, 0});

		// segment digitized northbound (0 deg)
		assertEquals(Integer.valueOf(89),
			OverpassSpeedLimitProvider.effectiveLimitKph(w, 10, 0));   // along
		assertEquals(Integer.valueOf(97),
			OverpassSpeedLimitProvider.effectiveLimitKph(w, 170, 0));  // against
	}

	@Test
	public void effectiveLimitFallsBackToPlainWhenDirectionalMissing()
	{
		OverpassSpeedLimitProvider.Way w = new OverpassSpeedLimitProvider.Way(
			80, null, 97, new double[] {0, 1}, new double[] {0, 0});

		assertEquals(Integer.valueOf(80),
			OverpassSpeedLimitProvider.effectiveLimitKph(w, 0, 0));    // no forward -> plain
		assertEquals(Integer.valueOf(97),
			OverpassSpeedLimitProvider.effectiveLimitKph(w, 180, 0));
	}

	@Test
	public void directionalOnlyWayIsSkippedWhenTraveledTheUntaggedWay()
	{
		List<OverpassSpeedLimitProvider.Way> ways = new ArrayList<OverpassSpeedLimitProvider.Way>();

		// forward-only tagging, digitized eastbound
		ways.add(new OverpassSpeedLimitProvider.Way(null, 89, null,
			new double[] {28.0, 28.0}, new double[] {-81.001, -80.999}));

		assertEquals(Integer.valueOf(89),
			OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 90f));
		assertNull(OverpassSpeedLimitProvider.selectLimitKph(ways, 28.0, -81.0, 270f));
	}
}
