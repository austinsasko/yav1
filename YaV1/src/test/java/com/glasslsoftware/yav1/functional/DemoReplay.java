package com.glasslsoftware.yav1.functional;

import com.glasslsoftware.yav1lib.YaV1Alert;
import com.glasslsoftware.yav1lib.YaV1AlertList;
import com.valentine.esp.PacketQueue;
import com.valentine.esp.constants.Devices;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.data.InfDisplayInfoData;
import com.valentine.esp.demo.DemoData;
import com.valentine.esp.packets.ESPPacket;
import com.valentine.esp.packets.response.ResponseAlertData;
import com.valentine.esp.statemachines.GetAlertData;
import com.valentine.esp.utilities.V1VersionSettingLookup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [QA-FUNC] Replays a shipped demo file (assets/demo/*.dat) through the REAL
 * decode and alert pipeline, the way demo mode does:
 *
 *   hex line -> ESPPacket.makeFromBuffer (PACK framing, checksums, V1-type
 *   gating) -> DemoData.handleDemoPacket (demo routing, Gen2 recognition)
 *   -> GetAlertData.getAlertDataCallback for respAlertData (alert-table
 *   assembly, band/direction/signal classification into YaV1Alert)
 *
 * and collects everything a test needs to assert user-visible outcomes:
 * completed alert cycles, decoded bogey-counter letters, per-packet-id counts
 * and the Gen2 recognition state.
 */
public final class DemoReplay
{
    /** Completed alert tables, as delivered to the app's alert callback. */
    public final List<List<YaV1Alert>> alertCycles = new ArrayList<List<YaV1Alert>>();

    /** Bogey-counter letters decoded from every infDisplayData frame. */
    public final Set<String> bogeyLetters = new LinkedHashSet<String>();

    /** How many packets of each id the file contained. */
    public final Map<PacketId, Integer> packetCounts = new HashMap<PacketId, Integer>();

    /** Hex lines that failed to decode into a packet. */
    public final List<String> unparsedLines = new ArrayList<String>();

    /** V1VersionSettingLookup.isGen2() after the whole file was replayed. */
    public boolean gen2AfterReplay;

    /** Receives the assembled alert tables from GetAlertData (public for reflection). */
    public static class Collector
    {
        final List<List<YaV1Alert>> cycles;

        Collector(List<List<YaV1Alert>> cycles)
        {
            this.cycles = cycles;
        }

        public void onAlerts(YaV1AlertList list)
        {
            // GetAlertData reuses its internal list; copy the elements
            cycles.add(new ArrayList<YaV1Alert>(list));
        }
    }

    private DemoReplay()
    {
    }

    /**
     * Replay a demo file. Resets and cleans up all static decoder state so
     * tests stay isolated from each other.
     */
    public static DemoReplay replay(File demoFile) throws IOException
    {
        DemoReplay result = new DemoReplay();

        // what ValentineESP.startDemo does before playing a file
        ESPPacket.resetDecoderState();
        ESPPacket.presetV1Type(Devices.VALENTINE1_WITH_CHECKSUM);
        V1VersionSettingLookup.resetV1Version();
        PacketQueue.initInputQueue(true);

        DemoData     demoData  = new DemoData();
        GetAlertData alertData = new GetAlertData(null, new Collector(result.alertCycles), "onAlerts");

        // the mockable android.jar's SparseArray is a returnDefaultValues
        // no-op; give the real GetAlertData a working table to assemble into
        try
        {
            java.lang.reflect.Field store = GetAlertData.class.getDeclaredField("m_store");
            store.setAccessible(true);
            store.set(alertData, new WorkingSparseArray<Object>());
        }
        catch(Exception e)
        {
            throw new IllegalStateException("unable to inject a working SparseArray", e);
        }

        try
        {
            for(String line : RepoFile.demoHexLines(demoFile))
            {
                ArrayList<Byte> buffer = new ArrayList<Byte>();
                for(String tok : line.split("\\s+"))
                    buffer.add((byte) Integer.parseInt(tok, 16));

                ESPPacket p = ESPPacket.makeFromBuffer(buffer);
                if(p == null)
                {
                    result.unparsedLines.add(line);
                    continue;
                }

                PacketId id = p.getPacketIdentifier();
                Integer  c  = result.packetCounts.get(id);
                result.packetCounts.put(id, c == null ? 1 : c + 1);

                // the demo routing the library applies to every demo packet
                demoData.handleDemoPacket(p);

                if(id == PacketId.respAlertData)
                {
                    // the same path a live connection uses to assemble tables
                    alertData.getAlertDataCallback((ResponseAlertData) p);
                }
                else if(id == PacketId.infDisplayData)
                {
                    InfDisplayInfoData d = (InfDisplayInfoData) p.getResponseData();
                    result.bogeyLetters.add(d.getBogeyCounterData1().convertToLetter());
                }
            }

            result.gen2AfterReplay = V1VersionSettingLookup.isGen2();
        }
        finally
        {
            // DemoData pushed display/alert packets onto the static input
            // queue; drop them so no state leaks into other tests
            PacketQueue.initInputQueue(true);
            ESPPacket.resetDecoderState();
            V1VersionSettingLookup.resetV1Version();
        }

        return result;
    }

    /** Non-empty alert cycles. */
    public List<List<YaV1Alert>> nonEmptyCycles()
    {
        List<List<YaV1Alert>> out = new ArrayList<List<YaV1Alert>>();
        for(List<YaV1Alert> c : alertCycles)
            if(!c.isEmpty())
                out.add(c);
        return out;
    }

    public int countOf(PacketId id)
    {
        Integer c = packetCounts.get(id);
        return c == null ? 0 : c;
    }
}
