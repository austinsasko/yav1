package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.psl.OverpassSpeedLimitProvider;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * [QA-REG] Pins directional maxspeed handling in the Overpass PSL provider.
 *
 * Bug: real OSM ways exist that carry ONLY maxspeed:forward /
 * maxspeed:backward and no plain maxspeed (live find 2026-07-14: the US 290
 * Express Lane in Houston, oneway=reversible, 55/60 mph split). The original
 * query filtered on ["maxspeed"] alone, so such ways were never fetched and
 * the driver was scored against a neighboring road's limit.
 * Fixed in commit 6a0a856 (PR #5, "PSL: fetch and honor directional maxspeed
 * tags"): the query unions the three tag filters, Way carries
 * forward/backward limits and selectLimitKph picks the limit for the actual
 * direction of travel.
 *
 * Fixture: src/test/resources/psl/overpass_us290_directional.json, recorded
 * from the live Overpass API at the coordinates in the original bug report.
 */
public class OverpassDirectionalMaxspeedRegressionTest
{
    private static String fixture(String name) throws IOException
    {
        InputStream in = OverpassDirectionalMaxspeedRegressionTest.class.getResourceAsStream(name);
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
    public void queryFetchesDirectionalOnlyWays()
    {
        // the root cause: a ["maxspeed"]-only query never returns
        // directional-only ways at all
        String q = OverpassSpeedLimitProvider.buildQuery(29.79659, -95.45174);

        assertTrue(q.contains("[\"maxspeed\"]"));
        assertTrue("query must union maxspeed:forward", q.contains("[\"maxspeed:forward\"]"));
        assertTrue("query must union maxspeed:backward", q.contains("[\"maxspeed:backward\"]"));
    }

    @Test
    public void directionalOnlyWaySurvivesParsing() throws IOException
    {
        List<OverpassSpeedLimitProvider.Way> ways =
            OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_us290_directional.json"));

        OverpassSpeedLimitProvider.Way express = null;
        for(OverpassSpeedLimitProvider.Way w : ways)
            if(w.forwardKph != null)
                express = w;

        assertTrue("the reversible express lane must be parsed", express != null);
        assertNull("it carries no plain maxspeed", express.limitKph);
        assertEquals(Integer.valueOf(89), express.forwardKph);    // 55 mph
        assertEquals(Integer.valueOf(97), express.backwardKph);   // 60 mph
        assertTrue("a directional-only way is usable", !express.unusable());
    }

    @Test
    public void limitFollowsTheDirectionOfTravel() throws IOException
    {
        List<OverpassSpeedLimitProvider.Way> ways =
            OverpassSpeedLimitProvider.parseWays(fixture("/psl/overpass_us290_directional.json"));

        // traveling with the digitized direction (bearing ~14 deg): forward 55 mph
        assertEquals(Integer.valueOf(89),
            OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 14f));

        // traveling against it: backward 60 mph
        assertEquals(Integer.valueOf(97),
            OverpassSpeedLimitProvider.selectLimitKph(ways, 29.79659, -95.45174, 194f));
    }

    @Test
    public void directionalTagBeatsPlainAndFallsBackWhenAbsent()
    {
        double[] lats = {0, 1};
        double[] lons = {0, 0};   // digitized due north

        // both directional tags present: plain 80 is never used
        OverpassSpeedLimitProvider.Way full =
            new OverpassSpeedLimitProvider.Way(80, 89, 97, lats, lons);
        assertEquals(Integer.valueOf(89),
                     OverpassSpeedLimitProvider.effectiveLimitKph(full, 5, 0));
        assertEquals(Integer.valueOf(97),
                     OverpassSpeedLimitProvider.effectiveLimitKph(full, 175, 0));

        // forward missing: travel along the way falls back to plain
        OverpassSpeedLimitProvider.Way partial =
            new OverpassSpeedLimitProvider.Way(80, null, 97, lats, lons);
        assertEquals(Integer.valueOf(80),
                     OverpassSpeedLimitProvider.effectiveLimitKph(partial, 0, 0));
    }
}
