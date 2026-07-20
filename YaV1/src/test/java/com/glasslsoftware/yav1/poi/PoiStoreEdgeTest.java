package com.glasslsoftware.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [QA] Component tests for PoiStore error paths beyond PoiStoreTest:
 * corrupt store files, null input streams, generated-file enabled-flag
 * preservation, loose-CSV cleanup on remove and display-name sanitizing.
 */
public class PoiStoreEdgeTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File mDir;

    @Before
    public void setUp() throws IOException
    {
        mDir = tmp.newFolder("poi");
    }

    private void write(File f, String content) throws IOException
    {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        w.write(content);
        w.close();
    }

    @Test
    public void corruptJsonInStoreDirIsSkippedOthersLoad() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        store.importCsv(new ByteArrayInputStream("28.6,-81.38,1\n".getBytes("UTF-8")),
                        "good.csv", "");

        write(new File(mDir, "broken.json"), "not json at all {{{");

        PoiStore fresh = new PoiStore(mDir);
        fresh.load();

        assertEquals("only the valid file must load", 1, fresh.getFiles().size());
        assertEquals("good.csv", fresh.getFiles().get(0).name);
    }

    @Test
    public void nullStreamFailsWithReadableError()
    {
        PoiStore store = new PoiStore(mDir);
        store.load();

        assertNull(store.importCsv(null, "x.csv", ""));
        assertFalse(store.getLastError().isEmpty());
    }

    @Test
    public void putGeneratedPreservesDisabledFlagAcrossRegeneration()
    {
        PoiStore store = new PoiStore(mDir);
        store.load();

        List<Poi> v1 = Arrays.asList(new Poi(30.0, -95.0, "alpr", 0, "", "osm:alpr"));
        PoiFile pf = store.putGenerated("osm-cameras", v1, "osm");
        assertNotNull(pf);
        assertTrue(pf.enabled);

        // the user turns the generated database off
        assertTrue(store.setEnabled(pf, false));

        // the online manager regenerates it with more points
        List<Poi> v2 = new ArrayList<Poi>(v1);
        v2.add(new Poi(30.1, -95.1, "speed_camera", 0, "", "osm:speed_camera"));
        PoiFile again = store.putGenerated("osm-cameras", v2, "osm");

        assertNotNull(again);
        assertFalse("user's disabled flag must survive regeneration", again.enabled);
        assertEquals(2, again.count());
        assertEquals("still a single store entry", 1, store.getFiles().size());
        assertEquals(0, store.enabledCount());
    }

    @Test
    public void removeAlsoDeletesLooseCsvSoItCannotReimport() throws IOException
    {
        // adb-push workflow: loose CSV auto-imports on load
        write(new File(mDir, "dropped.csv"), "28.6,-81.38,1\n");

        PoiStore store = new PoiStore(mDir);
        store.load();
        assertEquals(1, store.getFiles().size());

        store.remove(store.getFiles().get(0));

        PoiStore fresh = new PoiStore(mDir);
        fresh.load();
        assertEquals("removed file must not resurrect from the loose CSV",
                     0, fresh.getFiles().size());
    }

    @Test
    public void displayNameWithPathComponentsIsFlattened() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();

        PoiFile pf = store.importCsv(
            new ByteArrayInputStream("28.6,-81.38,1\n".getBytes("UTF-8")),
            "../nested/dir/cams.csv", "content://x");

        assertNotNull(pf);
        assertEquals("cams.csv", pf.name);
        assertTrue(pf.jsonFile.getParentFile().equals(mDir));
    }

    @Test
    public void emptyDisplayNameGetsADefault() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();

        PoiFile pf = store.importCsv(
            new ByteArrayInputStream("28.6,-81.38,1\n".getBytes("UTF-8")), "", "src");

        assertNotNull(pf);
        assertEquals("import.csv", pf.name);
    }
}
