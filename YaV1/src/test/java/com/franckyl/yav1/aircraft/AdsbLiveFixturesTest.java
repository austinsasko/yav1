package com.franckyl.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * [P2-ADSB] Parser and tracker against payloads recorded LIVE from
 * api.adsb.lol on 2026-07-14:
 *
 *  - adsblol_atl_live.json    /v2/point/33.6400/-84.4300/10 (Atlanta, 28 ac)
 *  - adsblol_dfw100_live.json /v2/point/32.9000/-97.0400/100 (DFW, 155 ac)
 *  - adsblol_ocean_live.json  /v2/point/30.0000/-140.0000/10 (empty)
 *
 * Real-world shapes covered: alt_baro="ground" on parked airliners,
 * missing track/gs on ground targets, TIS-B synthetic hex ("~1708bc")
 * without registration / type / flight, and volume.
 */
public class AdsbLiveFixturesTest
{
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
    public void atlantaLivePayloadParses() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsblol_atl_live.json"));

        // every entry in the recorded payload has a position
        assertEquals(28, list.size());

        int ground = 0;
        int noTrack = 0;
        Aircraft tisb = null;

        for(Aircraft ac: list)
        {
            assertTrue(ac.hasPosition());
            assertFalse(ac.hex.isEmpty());

            if(ac.onGround)
                ground++;
            if(Double.isNaN(ac.trackDeg))
                noTrack++;
            if(ac.hex.startsWith("~"))
                tisb = ac;
        }

        assertEquals(20, ground);
        assertEquals(18, noTrack);

        // TIS-B target: synthetic hex, no registration / type / callsign
        assertTrue(tisb != null);
        assertEquals("~1708bc", tisb.hex);
        assertEquals("", tisb.reg);
        assertEquals("", tisb.type);
        assertEquals("", tisb.flight);
        assertEquals(1600, tisb.altFt);
        assertFalse(Double.isNaN(tisb.gsKt));
    }

    @Test
    public void dfw100nmVolumePayloadParses() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsblol_dfw100_live.json"));

        assertEquals(155, list.size());
    }

    @Test
    public void trackerAbsorbsAVolumePayload() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsblol_dfw100_live.json"));

        AircraftTracker tracker = new AircraftTracker();
        long now = 1000000L;

        // three polls of the same busy airspace
        for(int poll = 0; poll < 3; poll++)
        {
            for(Aircraft ac: list)
                tracker.assess(ac, false, now + poll * 30000L);
            tracker.prune(now + poll * 30000L);
        }

        assertEquals(155, tracker.trackedCount());

        // everything ages out after the stale window
        tracker.prune(now + 2 * 30000L + AircraftTracker.STALE_MS + 1);
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    public void emptyOceanPayloadGivesEmptyList() throws IOException
    {
        List<Aircraft> list = AdsbParser.parse(fixture("/aircraft/adsblol_ocean_live.json"));
        assertTrue(list.isEmpty());
    }
}
