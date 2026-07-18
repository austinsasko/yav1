package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.functional.DemoReplay;
import com.glasslsoftware.yav1.functional.RepoFile;

import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.SweepDefinition;
import com.valentine.esp.utilities.Range;
import com.valentine.esp.utilities.V1VersionSettingLookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;

/**
 * [QA-REG] Pins the sweep / profile-push degradation on V1 Gen2 devices.
 *
 * Bug: the V1 Gen2 keeps the ESP device id of a V1 with checksum but has no
 * custom sweeps ("custom frequencies" live on the detector instead). The app
 * used to query and push sweeps anyway, leaving state machines waiting
 * forever on responses a Gen2 never sends, and the demo chooser had no Gen2
 * data at all.
 * Fixed in commit ee29637 (PR #2, sub-commit "P0: Gen2 demo simulation,
 * sweep degradation, BLE stress tests"): version-based Gen2 detection gates
 * every sweep path (YaV1.getSweeps / YaV1.customPossible /
 * ValentineClient.setCustomSweeps / setSweepsToDefault) and the shipped
 * demo_gen2.dat exercises the degradation end to end.
 *
 * The ValentineClient-level early returns need an Android Context to
 * construct the client, so what is pinned here is the decision input every
 * one of those gates shares (V1VersionSettingLookup.isGen2) plus the shipped
 * Gen2 demo data those gates were validated against.
 */
public class Gen2SweepDegradationRegressionTest
{
    @Before
    public void setUp()
    {
        V1VersionSettingLookup.resetV1Version();
    }

    @After
    public void tearDown()
    {
        V1VersionSettingLookup.resetV1Version();
    }

    @Test
    public void gen2DetectionBoundaryIsExactlyV4_1000()
    {
        V1VersionSettingLookup lookup = new V1VersionSettingLookup();

        // the newest Gen1 firmware stays Gen1
        lookup.setV1Version("V3.8952");
        assertFalse(V1VersionSettingLookup.isGen2());

        // the Gen2 baseline flips every sweep gate
        lookup.setV1Version("V4.1000");
        assertTrue(V1VersionSettingLookup.isGen2());
    }

    @Test
    public void malformedVersionStringsFallBackToGen1Behavior()
    {
        V1VersionSettingLookup lookup = new V1VersionSettingLookup();

        // never lock a Gen1 out of its sweeps because of a garbled version
        lookup.setV1Version("V4.1035");
        assertTrue(V1VersionSettingLookup.isGen2());

        lookup.setV1Version("Vgarbage");
        assertFalse("unparseable version must fall back to the default (Gen1)",
                    V1VersionSettingLookup.isGen2());

        lookup.setV1Version(null);
        assertFalse(V1VersionSettingLookup.isGen2());
    }

    @Test
    public void gen2StateDoesNotSurviveAReconnect()
    {
        // reconnect hardening: a Gen2 flag leaking onto a later Gen1
        // connection would silently disable that unit's custom sweeps
        new V1VersionSettingLookup().setV1Version("V4.1035");
        assertTrue(V1VersionSettingLookup.isGen2());

        V1VersionSettingLookup.resetV1Version();
        assertFalse(V1VersionSettingLookup.isGen2());
    }

    @Test
    public void shippedGen2DemoDataCarriesNoSweepDialogue() throws IOException
    {
        DemoReplay gen2 = DemoReplay.replay(RepoFile.find("src/main/assets/demo/demo_gen2.dat"));

        assertTrue("demo_gen2.dat must identify a Gen2", gen2.gen2AfterReplay);

        // degradation contract: no custom sweep traffic for a Gen2
        assertEquals(0, gen2.countOf(PacketId.respSweepDefinition));
        assertEquals(0, gen2.countOf(PacketId.respSweepSections));
        assertEquals(0, gen2.countOf(PacketId.respMaxSweepIndex));
        assertEquals(0, gen2.countOf(PacketId.reqAllSweepDefinitions));
        assertEquals(0, gen2.countOf(PacketId.reqSweepSections));
    }

    @Test
    public void gen1DemoDataStillNegotiatesSweeps() throws IOException
    {
        // the degradation must not have leaked into the Gen1 path
        DemoReplay gen1 = DemoReplay.replay(RepoFile.find("src/main/assets/demo/demo.dat"));

        assertFalse(gen1.gen2AfterReplay);
        assertTrue(gen1.countOf(PacketId.respSweepDefinition) >= 6);
        assertTrue(gen1.countOf(PacketId.respSweepSections) >= 1);
        assertTrue(gen1.countOf(PacketId.respMaxSweepIndex) >= 1);
    }

    @Test
    public void defaultSweepFallbacksStayAvailableForTheUi()
    {
        // even with a Gen2 connected, the sweep screens fall back to the
        // factory defaults instead of crashing on empty data
        new V1VersionSettingLookup().setV1Version("V4.1035");
        V1VersionSettingLookup lookup = new V1VersionSettingLookup();

        Range[] defaults = lookup.getV1DefaultCustomSweeps();
        assertEquals(6, defaults.length);
        assertEquals(33900, defaults[0].LoFreq);
        assertEquals(34106, defaults[0].HiFreq);

        ArrayList<SweepDefinition> asList = lookup.getV1DefaultCustomSweepsAsList();
        assertEquals(6, asList.size());
        assertEquals(5, lookup.getV1DefaultMaxSweepIndex());
    }
}
