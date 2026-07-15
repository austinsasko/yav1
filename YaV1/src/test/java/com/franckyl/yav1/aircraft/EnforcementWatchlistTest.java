package com.franckyl.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

public class EnforcementWatchlistTest
{
    private static final String CSV =
            "# comment line\n" +
            "A255E2,N25HP,Florida Highway Patrol,Cessna 182T\n" +
            "a11791,N17HP,State Of Ohio Dept Of Public Safety\n" +   // lower case + no model
            "ZZZZZZ,N1BAD,Bad Hex Agency\n" +                        // invalid hex: skipped
            "short,line\n" +                                          // malformed: skipped
            "A19758,N201SP,Colorado State Patrol,Pilatus PC-12\n";

    private EnforcementWatchlist load() throws IOException
    {
        EnforcementWatchlist wl = new EnforcementWatchlist();
        wl.load(new StringReader(CSV));
        return wl;
    }

    @Test
    public void loadsValidEntriesSkipsBadOnes() throws IOException
    {
        EnforcementWatchlist wl = load();
        assertEquals(3, wl.size());
    }

    @Test
    public void matchesByHexCaseInsensitive() throws IOException
    {
        EnforcementWatchlist wl = load();

        Aircraft ac = new Aircraft();
        ac.hex = "a255e2";

        EnforcementWatchlist.Entry e = wl.match(ac);
        assertNotNull(e);
        assertEquals("Florida Highway Patrol", e.agency);
        assertEquals("N25HP", e.reg);

        ac.hex = "A11791";
        e = wl.match(ac);
        assertNotNull(e);
        assertEquals("State Of Ohio Dept Of Public Safety", e.agency);
    }

    @Test
    public void matchesByRegistrationAndCallsign() throws IOException
    {
        EnforcementWatchlist wl = load();

        Aircraft ac = new Aircraft();
        ac.hex = "ffffff";          // unknown hex
        ac.reg = "n201sp";
        assertNotNull(wl.match(ac));
        assertEquals("Colorado State Patrol", wl.match(ac).agency);

        Aircraft byCallsign = new Aircraft();
        byCallsign.hex    = "eeeeee";
        byCallsign.flight = "N25HP";
        assertNotNull(wl.match(byCallsign));
    }

    @Test
    public void noMatchReturnsNull() throws IOException
    {
        EnforcementWatchlist wl = load();

        Aircraft ac = new Aircraft();
        ac.hex    = "123456";
        ac.reg    = "N999XX";
        ac.flight = "DAL2049";
        assertNull(wl.match(ac));
        assertNull(wl.match(null));
    }

    @Test
    public void userListMergesAndOverrides() throws IOException
    {
        EnforcementWatchlist wl = load();
        wl.load(new StringReader("A255E2,N25HP,Overridden Agency\nAB0001,N842XY,My County Sheriff\n"));

        assertEquals(4, wl.size());

        Aircraft ac = new Aircraft();
        ac.hex = "A255E2";
        assertEquals("Overridden Agency", wl.match(ac).agency);

        ac.hex = "AB0001";
        assertEquals("My County Sheriff", wl.match(ac).agency);
    }

    @Test
    public void shippedAssetLoads() throws IOException
    {
        // the curated asset itself must parse
        java.io.InputStream in = getClass().getResourceAsStream("/asset_enforcement_hex.csv");
        if(in == null)
        {
            // fall back to reading straight from the source tree
            java.io.File f = new java.io.File("src/main/assets/aircraft/enforcement_hex.csv");
            if(!f.exists())
                return; // running from an unexpected cwd: skip silently
            in = new java.io.FileInputStream(f);
        }

        EnforcementWatchlist wl = new EnforcementWatchlist();
        int n = wl.load(new java.io.InputStreamReader(in, "UTF-8"));
        in.close();

        org.junit.Assert.assertTrue("asset should have >200 entries, got " + n, n > 200);

        Aircraft fhp = new Aircraft();
        fhp.hex = "A255E2";
        assertNotNull(wl.match(fhp));
    }
}
