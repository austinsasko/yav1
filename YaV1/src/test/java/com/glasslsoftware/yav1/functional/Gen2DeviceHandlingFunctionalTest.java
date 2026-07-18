package com.glasslsoftware.yav1.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.YaV1BsmFilter;
import com.glasslsoftware.yav1lib.YaV1Alert;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.utilities.V1VersionSettingLookup;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * [QA-FUNC] V1 Gen2 handling, end to end, on the shipped Gen2 demo recording
 * (assets/demo/demo_gen2.dat):
 *
 *  - demo-mode Gen2 recognition (the V4.1035 version response at the top of
 *    the file must flip V1VersionSettingLookup.isGen2 through the real
 *    DemoData path, exactly like a live connection),
 *  - the Gen2 junk/BSM aux bit surviving all the way into YaV1Alert
 *    properties, and the app-side BSM filter honoring it,
 *  - sweep/profile-push degradation: the Gen2 demo recording carries no
 *    custom-sweep traffic at all (a Gen2 has no custom sweeps), while the
 *    Gen1 recording of the same session does.
 */
public class Gen2DeviceHandlingFunctionalTest
{
    private static DemoReplay sGen2;
    private static DemoReplay sGen1;

    @BeforeClass
    public static void replayBothRecordings() throws IOException
    {
        sGen2 = DemoReplay.replay(RepoFile.find("src/main/assets/demo/demo_gen2.dat"));
        sGen1 = DemoReplay.replay(RepoFile.find("src/main/assets/demo/demo.dat"));
    }

    @After
    public void tearDown()
    {
        TestSeams.setBsmFilterEnabled(false);
        V1VersionSettingLookup.resetV1Version();
    }

    @Test
    public void everyGen2FrameDecodes()
    {
        assertTrue("demo_gen2.dat lines failed to decode: " + sGen2.unparsedLines,
                   sGen2.unparsedLines.isEmpty());
    }

    @Test
    public void demoModeRecognizesTheGen2()
    {
        assertTrue("the V4.1035 version response must drive Gen2 recognition in demo mode",
                   sGen2.gen2AfterReplay);
        assertFalse("the Gen1 recording must not", sGen1.gen2AfterReplay);
    }

    @Test
    public void gen2RecognitionDoesNotLeakAcrossConnections()
    {
        // DemoReplay resets the version state after each replay, the same
        // reset a new connection applies; a fresh session must be Gen1 again
        assertFalse(V1VersionSettingLookup.isGen2());
        assertFalse(V1VersionSettingLookup.isJunkAlertReported());
    }

    @Test
    public void kBandAlertsCarryTheJunkFlagOnGen2Only()
    {
        int gen2JunkK = 0;

        for(List<YaV1Alert> cycle : sGen2.nonEmptyCycles())
            for(YaV1Alert a : cycle)
            {
                if(a.getBand() == YaV1Alert.BAND_K)
                {
                    assertTrue("every K alert of the Gen2 recording is a flagged BSM false",
                               (a.getProperty() & YaV1Alert.PROP_JUNK) > 0);
                    gen2JunkK++;
                }
                else
                {
                    assertEquals("only K alerts are junk-flagged in this recording",
                                 0, a.getProperty() & YaV1Alert.PROP_JUNK);
                }
            }

        assertTrue("the Gen2 recording contains junk-flagged K alerts", gen2JunkK > 0);

        for(List<YaV1Alert> cycle : sGen1.nonEmptyCycles())
            for(YaV1Alert a : cycle)
                assertEquals(0, a.getProperty() & YaV1Alert.PROP_JUNK);
    }

    @Test
    public void bsmFilterHoldsAJunkFlaggedAlertForItsWholeLife()
    {
        TestSeams.setBsmFilterEnabled(true);

        // find a junk-flagged K alert from the recording
        YaV1Alert junkK = null;
        for(List<YaV1Alert> cycle : sGen2.nonEmptyCycles())
            for(YaV1Alert a : cycle)
                if(a.getBand() == YaV1Alert.BAND_K && (a.getProperty() & YaV1Alert.PROP_JUNK) > 0)
                    junkK = a;
        assertTrue(junkK != null);

        boolean junk = (junkK.getProperty() & YaV1Alert.PROP_JUNK) > 0;

        // a new K alert enters the filter, and the junk flag keeps it held
        // far beyond every timing window
        assertTrue(YaV1BsmFilter.shouldHoldNew(junkK.getBand()));
        assertTrue(YaV1BsmFilter.shouldStayHeld(0, junkK.getSignal(), junkK.getSignal(), junk));
        assertTrue(YaV1BsmFilter.shouldStayHeld(60000, junkK.getSignal(), 8, junk));
    }

    @Test
    public void alertContentMatchesTheGen1RecordingOtherwise()
    {
        // demo_gen2.dat is the same drive re-recorded for a Gen2: same number
        // of alert cycles, same busiest moment
        assertEquals(sGen1.nonEmptyCycles().size(), sGen2.nonEmptyCycles().size());

        int max1 = 0, max2 = 0;
        for(List<YaV1Alert> c : sGen1.nonEmptyCycles()) max1 = Math.max(max1, c.size());
        for(List<YaV1Alert> c : sGen2.nonEmptyCycles()) max2 = Math.max(max2, c.size());
        assertEquals(max1, max2);
    }

    @Test
    public void gen2RecordingHasNoCustomSweepTraffic()
    {
        // sweep/profile-push degradation: a V1 Gen2 has no custom sweeps, so
        // the Gen2 demo data must carry none of the custom sweep dialogue
        assertEquals(0, sGen2.countOf(PacketId.respSweepDefinition));
        assertEquals(0, sGen2.countOf(PacketId.respSweepSections));
        assertEquals(0, sGen2.countOf(PacketId.respMaxSweepIndex));

        // while the Gen1 recording of the same session negotiates all of it
        assertTrue(sGen1.countOf(PacketId.respSweepDefinition) >= 6);
        assertTrue(sGen1.countOf(PacketId.respSweepSections) >= 1);
        assertTrue(sGen1.countOf(PacketId.respMaxSweepIndex) >= 1);
    }

    @Test
    public void gen2RecordingCarriesTheGen2VolumeResponse()
    {
        // the Gen2-only current-volume packet is present in the Gen2 file
        // and absent from the Gen1 file
        assertTrue(sGen2.countOf(PacketId.respCurrentVolume) >= 1);
        assertEquals(0, sGen1.countOf(PacketId.respCurrentVolume));
    }

    @Test
    public void gen2VersionBoundariesAreExact()
    {
        V1VersionSettingLookup lookup = new V1VersionSettingLookup();

        lookup.setV1Version("V3.8952");
        assertFalse(V1VersionSettingLookup.isGen2());

        lookup.setV1Version("V4.1000");   // first Gen2 firmware
        assertTrue(V1VersionSettingLookup.isGen2());
        assertFalse("junk reporting starts at V4.1032, not V4.1000",
                    V1VersionSettingLookup.isJunkAlertReported());

        lookup.setV1Version("V4.1032");   // first junk-reporting firmware
        assertTrue(V1VersionSettingLookup.isJunkAlertReported());

        V1VersionSettingLookup.resetV1Version();
        assertFalse(V1VersionSettingLookup.isGen2());
    }
}
