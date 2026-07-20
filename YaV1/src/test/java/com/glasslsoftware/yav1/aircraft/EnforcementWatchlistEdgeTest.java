package com.glasslsoftware.yav1.aircraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * [QA] Component tests for EnforcementWatchlist input validation beyond
 * EnforcementWatchlistTest: hex format rejection, whitespace tolerance,
 * duplicate registrations and hex-versus-registration match priority.
 */
public class EnforcementWatchlistEdgeTest
{
    private static EnforcementWatchlist load(String csv) throws IOException
    {
        EnforcementWatchlist wl = new EnforcementWatchlist();
        wl.load(new StringReader(csv));
        return wl;
    }

    private static Aircraft byHex(String hex)
    {
        Aircraft ac = new Aircraft();
        ac.hex = hex;
        return ac;
    }

    @Test
    public void malformedHexesAreRejected() throws IOException
    {
        EnforcementWatchlist wl = load(
            "A255E,N1,Too Short\n" +
            "A255E22,N2,Too Long\n" +
            "A255EG,N3,Not Hex\n" +
            ",N4,Empty Hex\n" +
            "A255E2,N25HP,Valid Agency\n");

        assertEquals(1, wl.size());
        assertNotNull(wl.match(byHex("A255E2")));
        assertNull(wl.match(byHex("A255E1")));
    }

    @Test
    public void fieldsAreTrimmedOnLoadAndMatch() throws IOException
    {
        EnforcementWatchlist wl = load("  a255e2 ,  n25hp , Agency X , C182 \n");

        assertEquals(1, wl.size());

        EnforcementWatchlist.Entry e = wl.match(byHex("  A255E2  "));
        assertNotNull(e);
        assertEquals("Agency X", e.agency);
        assertEquals("C182", e.model);
        assertEquals("N25HP", e.reg);
    }

    @Test
    public void duplicateRegistrationLastEntryWinsForRegLookup() throws IOException
    {
        EnforcementWatchlist wl = load(
            "AAA111,N1XY,First Agency\n" +
            "BBB222,N1XY,Second Agency\n");

        assertEquals(2, wl.size());

        // both airframes still match by hex
        assertEquals("First Agency", wl.match(byHex("AAA111")).agency);
        assertEquals("Second Agency", wl.match(byHex("BBB222")).agency);

        // the shared registration resolves to the later entry
        Aircraft byReg = new Aircraft();
        byReg.hex = "CCC333";
        byReg.reg = "N1XY";
        assertEquals("Second Agency", wl.match(byReg).agency);
    }

    @Test
    public void hexMatchBeatsRegistrationMatch() throws IOException
    {
        EnforcementWatchlist wl = load(
            "AAA111,N1AA,Hex Agency\n" +
            "BBB222,N2BB,Reg Agency\n");

        Aircraft ac = new Aircraft();
        ac.hex = "AAA111";
        ac.reg = "N2BB";       // points at the other entry

        assertEquals("Hex Agency", wl.match(ac).agency);
    }

    @Test
    public void aircraftWithAllEmptyIdentifiersNeverMatches() throws IOException
    {
        EnforcementWatchlist wl = load("A255E2,N25HP,Agency\n");

        Aircraft blank = new Aircraft();     // hex/reg/flight all ""
        assertNull(wl.match(blank));
    }

    @Test
    public void loadReturnsTheNumberOfEntriesAdded() throws IOException
    {
        EnforcementWatchlist wl = new EnforcementWatchlist();

        assertEquals(2, wl.load(new StringReader(
            "A255E2,N25HP,Agency A\nA11791,N17HP,Agency B\nbadline\n")));
        assertEquals(1, wl.load(new StringReader("AB0001,N842XY,Agency C\n")));
        assertEquals(3, wl.size());
    }
}
