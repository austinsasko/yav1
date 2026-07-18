package com.glasslsoftware.yav1.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.poi.OverpassCameraSource;
import com.glasslsoftware.yav1.poi.Poi;
import com.glasslsoftware.yav1.poi.PoiAlertEngine;
import com.glasslsoftware.yav1.poi.PoiFile;
import com.glasslsoftware.yav1.poi.PoiGridIndex;
import com.glasslsoftware.yav1.poi.PoiOnlineCache;
import com.glasslsoftware.yav1.poi.PoiPhrases;
import com.glasslsoftware.yav1.poi.PoiStore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * [QA][integration] POI/camera alert pipeline on real collaborators:
 *
 *   CSV text -> PoiStore import (disk) -> enabledPois -> PoiGridIndex
 *     -> PoiAlertEngine (GPS fixes) -> PoiPhrases announcement text
 *
 * plus the online branch:
 *
 *   live Overpass fixture -> OverpassCameraSource.parse -> PoiOnlineCache
 *     -> PoiStore.putGenerated -> index -> engine -> phrase
 */
public class PoiAlertPipelineIntegrationTest
{
    private static final double CAM_LAT = 28.6000;
    private static final double CAM_LON = -81.3800;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File mDir;

    @Before
    public void setUp() throws IOException
    {
        mDir = tmp.newFolder("poi");
    }

    private PoiStore storeWithCsv(String csv) throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        assertNotNull(store.importCsv(
            new ByteArrayInputStream(csv.getBytes("UTF-8")), "cams.csv", "test"));
        return store;
    }

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
    public void csvToSpokenAlertBothStages() throws IOException
    {
        PoiStore store = storeWithCsv(CAM_LAT + "," + CAM_LON + ",1,50,\n");

        PoiGridIndex   index  = PoiGridIndex.build(store.enabledPois());
        PoiAlertEngine engine = new PoiAlertEngine(500, 60, 3.0, 3 * 60 * 1000L);

        // northbound at 15 m/s, 445m south of the camera
        double lat = CAM_LAT - 445 / 111320.0;
        List<PoiAlertEngine.Alert> alerts = engine.update(lat, CAM_LON, 0, 15.0, 0, index);

        assertEquals(1, alerts.size());
        assertEquals(PoiAlertEngine.STAGE_APPROACH, alerts.get(0).stage);
        assertEquals("Speed camera ahead, 450 meters",
            PoiPhrases.alertPhrase(alerts.get(0).poi, alerts.get(0).distanceM,
                                   alerts.get(0).stage, PoiPhrases.UNIT_METRIC));

        // closing to 210m: CLOSE stage with its shorter phrase
        lat = CAM_LAT - 210 / 111320.0;
        alerts = engine.update(lat, CAM_LON, 0, 15.0, 20_000, index);

        assertEquals(1, alerts.size());
        assertEquals(PoiAlertEngine.STAGE_CLOSE, alerts.get(0).stage);
        assertEquals("Speed camera, 200 meters",
            PoiPhrases.alertPhrase(alerts.get(0).poi, alerts.get(0).distanceM,
                                   alerts.get(0).stage, PoiPhrases.UNIT_METRIC));
    }

    @Test
    public void imperialDriverHearsMilesAndFeet() throws IOException
    {
        PoiStore store = storeWithCsv(CAM_LAT + "," + CAM_LON + ",3,0,\n");

        PoiGridIndex   index  = PoiGridIndex.build(store.enabledPois());
        PoiAlertEngine engine = new PoiAlertEngine(500, 60, 3.0, 3 * 60 * 1000L);

        double lat = CAM_LAT - 445 / 111320.0;
        List<PoiAlertEngine.Alert> alerts = engine.update(lat, CAM_LON, 0, 15.0, 0, index);

        assertEquals(1, alerts.size());
        assertEquals("Red light camera ahead, 0.3 miles",
            PoiPhrases.alertPhrase(alerts.get(0).poi, alerts.get(0).distanceM,
                                   alerts.get(0).stage, PoiPhrases.UNIT_IMPERIAL));
    }

    @Test
    public void storeReloadFromDiskFeedsTheSamePipeline() throws IOException
    {
        storeWithCsv(CAM_LAT + "," + CAM_LON + ",1,50,I-4 median cam\n");

        // app restart: fresh store, same directory
        PoiStore reloaded = new PoiStore(mDir);
        reloaded.load();
        assertEquals(1, reloaded.getFiles().size());

        PoiGridIndex   index  = PoiGridIndex.build(reloaded.enabledPois());
        PoiAlertEngine engine = new PoiAlertEngine(500, 60, 3.0, 3 * 60 * 1000L);

        double lat = CAM_LAT - 400 / 111320.0;
        List<PoiAlertEngine.Alert> alerts = engine.update(lat, CAM_LON, 0, 15.0, 0, index);

        assertEquals(1, alerts.size());
        // the user's name from the CSV wins over the type label
        assertEquals("I-4 median cam ahead, 400 meters",
            PoiPhrases.alertPhrase(alerts.get(0).poi, alerts.get(0).distanceM,
                                   alerts.get(0).stage, PoiPhrases.UNIT_METRIC));
    }

    @Test
    public void disabledFileProducesNoAlerts() throws IOException
    {
        PoiStore store = storeWithCsv(CAM_LAT + "," + CAM_LON + ",1,50,\n");
        PoiFile  pf    = store.getFiles().get(0);

        assertTrue(store.setEnabled(pf, false));

        PoiGridIndex   index  = PoiGridIndex.build(store.enabledPois());
        PoiAlertEngine engine = new PoiAlertEngine(500, 60, 3.0, 3 * 60 * 1000L);

        assertEquals(0, index.size());
        double lat = CAM_LAT - 400 / 111320.0;
        assertEquals(0, engine.update(lat, CAM_LON, 0, 15.0, 0, index).size());
    }

    @Test
    public void liveOsmFixtureFlowsToASpokenPlateReaderAlert() throws IOException
    {
        // live NYC Overpass response -> parsed POIs
        OverpassCameraSource source = new OverpassCameraSource();
        List<Poi> parsed = source.parse(fixture("/poi/overpass_cameras_nyc.json"), true, true);
        assertTrue(parsed.size() > 200);

        // dedupe/accumulate as PoiOnlineManager does
        PoiOnlineCache online = new PoiOnlineCache();
        int added = online.merge(parsed);
        assertTrue(added > 0);
        assertEquals("refetch must add nothing new", 0, online.merge(parsed));

        // persist the merged snapshot as the generated store file
        PoiStore store = new PoiStore(mDir);
        store.load();
        assertNotNull(store.putGenerated("osm-cameras", online.snapshot(), "osm"));

        // offline path from here: store -> index -> engine
        PoiGridIndex index = PoiGridIndex.build(store.enabledPois());
        assertEquals(online.poiCount(), index.size());

        // pick a real ALPR from the data and approach it from the south
        Poi alpr = null;
        for(Poi p : store.enabledPois())
        {
            if("alpr".equals(p.type))
            {
                alpr = p;
                break;
            }
        }
        assertNotNull("NYC fixture must contain ALPR nodes", alpr);

        PoiAlertEngine engine = new PoiAlertEngine(400, 60, 3.0, 3 * 60 * 1000L);
        double lat = alpr.lat - 350 / 111320.0;
        List<PoiAlertEngine.Alert> alerts = engine.update(lat, alpr.lon, 0, 15.0, 0, index);

        // dense-city data may alert on several cameras at once; ours must
        // be among them and speak as a plate reader
        PoiAlertEngine.Alert ours = null;
        for(PoiAlertEngine.Alert a : alerts)
        {
            if(a.poi == alpr)
                ours = a;
        }
        assertNotNull("the approached ALPR must alert", ours);

        String phrase = PoiPhrases.alertPhrase(ours.poi, ours.distanceM,
                                               ours.stage, PoiPhrases.UNIT_METRIC);
        assertTrue("phrase must identify a plate reader: " + phrase,
                   phrase.startsWith("License plate reader"));
        assertTrue(phrase.contains("ahead"));
    }
}
