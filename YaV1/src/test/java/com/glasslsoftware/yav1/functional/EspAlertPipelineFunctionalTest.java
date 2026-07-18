package com.glasslsoftware.yav1.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1lib.YaV1Alert;
import com.valentine.esp.constants.PacketId;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [QA-FUNC] ESP packet -> alert pipeline, end to end, on the real recorded
 * demo data shipped with the app (assets/demo/demo.dat, a Valentine One with
 * checksum on the ESP bus).
 *
 * Every hex line goes through ESPPacket.makeFromBuffer (framing, escaping,
 * both checksums, V1-type gating), demo routing (DemoData) and the alert
 * table assembly used by a live connection (GetAlertData), and the assertions
 * are user-visible outcomes: which bands alert, which alert is the priority
 * alert, how many bogeys show at once and what the bogey counter displays.
 */
public class EspAlertPipelineFunctionalTest
{
    private static DemoReplay sReplay;

    @BeforeClass
    public static void replayDemoFile() throws IOException
    {
        sReplay = DemoReplay.replay(RepoFile.find("src/main/assets/demo/demo.dat"));
    }

    @Test
    public void everyRecordedFrameDecodes()
    {
        assertTrue("demo.dat lines failed to decode: " + sReplay.unparsedLines,
                   sReplay.unparsedLines.isEmpty());
    }

    @Test
    public void fileCarriesDisplayAndAlertTraffic()
    {
        assertTrue(sReplay.countOf(PacketId.infDisplayData) > 100);
        assertTrue(sReplay.countOf(PacketId.respAlertData) > 100);
        assertEquals(1, sReplay.countOf(PacketId.respVersion) >= 1 ? 1 : 0);
    }

    @Test
    public void gen1DemoUnitIsNotRecognizedAsGen2()
    {
        // demo.dat reports V3.8930
        assertFalse(sReplay.gen2AfterReplay);
    }

    @Test
    public void alertCyclesAreAssembled()
    {
        // recorded session: long quiet stretches plus X, K and Ka encounters
        assertTrue("expected >= 150 non-empty alert cycles, got "
                   + sReplay.nonEmptyCycles().size(),
                   sReplay.nonEmptyCycles().size() >= 150);
        assertTrue("expected some empty (all-clear) cycles",
                   sReplay.alertCycles.size() > sReplay.nonEmptyCycles().size());
    }

    @Test
    public void bandClassificationMatchesTheRecordedEncounters()
    {
        Set<Integer> bands = new HashSet<Integer>();
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
            for(YaV1Alert a : cycle)
                bands.add(a.getBand());

        Set<Integer> expected = new HashSet<Integer>();
        expected.add(YaV1Alert.BAND_KA);
        expected.add(YaV1Alert.BAND_K);
        expected.add(YaV1Alert.BAND_X);

        // laser alerts reach the app through infDisplayData, not the alert
        // table, so exactly Ka / K / X must appear here
        assertEquals(expected, bands);
    }

    @Test
    public void kBandEncounterIsAt24Point171GHz()
    {
        boolean sawK = false;
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
            for(YaV1Alert a : cycle)
                if(a.getBand() == YaV1Alert.BAND_K && a.getFrequency() == 24171)
                    sawK = true;

        assertTrue("the recorded K-band door opener (24.171 GHz) must survive the pipeline", sawK);
    }

    @Test
    public void everyCycleHasAPriorityAlert()
    {
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
        {
            int prio = 0;
            for(YaV1Alert a : cycle)
                if((a.getProperty() & YaV1Alert.PROP_PRIORITY) > 0)
                    prio++;

            assertTrue("a non-empty cycle must carry a priority alert", prio >= 1);
            // the recorded data has two frames where laser overlaps the
            // radar priority alert; never more than two
            assertTrue("too many priority alerts in one cycle: " + prio, prio <= 2);
        }
    }

    @Test
    public void bogeyCounterShowsUpToFiveBogeys()
    {
        int maxBogeys = 0;
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
            maxBogeys = Math.max(maxBogeys, cycle.size());

        // the busiest recorded moment shows 5 simultaneous bogeys
        assertEquals(5, maxBogeys);

        // and the seven-segment bogey counter must have displayed 1..5
        for(String digit : new String[] {"1", "2", "3", "4", "5"})
            assertTrue("bogey counter never displayed " + digit + " (saw " + sReplay.bogeyLetters + ")",
                       sReplay.bogeyLetters.contains(digit));
    }

    @Test
    public void quietPeriodsShowTheAllBogeysModeLetter()
    {
        // with no alerts the counter displays the mode letter; the demo unit
        // runs in All Bogeys mode ("A")
        assertTrue("expected the All-Bogeys mode letter A on the bogey display",
                   sReplay.bogeyLetters.contains("A"));
    }

    @Test
    public void directionAndSignalAreWithinTheDisplayRange()
    {
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
            for(YaV1Alert a : cycle)
            {
                assertTrue(a.getArrowDir() == YaV1Alert.ALERT_FRONT
                        || a.getArrowDir() == YaV1Alert.ALERT_REAR
                        || a.getArrowDir() == YaV1Alert.ALERT_SIDE);
                assertTrue("signal LEDs out of range: " + a.getSignal(),
                           a.getSignal() >= 0 && a.getSignal() <= 8);
            }
    }

    @Test
    public void gen1DataNeverCarriesTheGen2JunkFlag()
    {
        for(List<YaV1Alert> cycle : sReplay.nonEmptyCycles())
            for(YaV1Alert a : cycle)
                assertEquals("a V3.x V1 cannot flag junk alerts",
                             0, a.getProperty() & YaV1Alert.PROP_JUNK);
    }
}
