package com.franckyl.yav1.poi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class PoiStoreTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File mDir;

    @Before
    public void setUp() throws IOException
    {
        mDir = tmp.newFolder("poi");
    }

    private File csv(String name, String content) throws IOException
    {
        File f = new File(tmp.getRoot(), name);
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        w.write(content);
        w.close();
        return f;
    }

    @Test
    public void importParsesAndPersists() throws IOException
    {
        File src = csv("cams.csv", "28.6,-81.38,1,50,Cam A\n28.7,-81.40,3\nbad,row\n");

        PoiStore store = new PoiStore(mDir);
        store.load();

        PoiFile pf = store.importCsv(src);
        assertNotNull(pf);
        assertEquals(2, pf.count());
        assertEquals(1, pf.skipped);
        assertTrue(pf.enabled);
        assertTrue(pf.jsonFile.exists());

        // a fresh store re-reads the imported file
        PoiStore store2 = new PoiStore(mDir);
        store2.load();
        assertEquals(1, store2.getFiles().size());
        assertEquals(2, store2.getFiles().get(0).count());
        assertEquals("cams.csv", store2.getFiles().get(0).name);
    }

    @Test
    public void importFailsOnEmptyFile() throws IOException
    {
        File src = csv("empty.csv", "# nothing here\n");

        PoiStore store = new PoiStore(mDir);
        store.load();

        assertNull(store.importCsv(src));
        assertFalse(store.getLastError().isEmpty());
        assertEquals(0, store.getFiles().size());
    }

    @Test
    public void enableFlagPersists() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        PoiFile pf = store.importCsv(csv("cams.csv", "28.6,-81.38,1\n"));

        assertEquals(1, store.enabledCount());
        assertTrue(store.setEnabled(pf, false));
        assertEquals(0, store.enabledCount());
        assertEquals(0, store.enabledPois().size());

        PoiStore store2 = new PoiStore(mDir);
        store2.load();
        assertFalse(store2.getFiles().get(0).enabled);
    }

    @Test
    public void removeDeletesJson() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        PoiFile pf = store.importCsv(csv("cams.csv", "28.6,-81.38,1\n"));
        File json = pf.jsonFile;
        assertTrue(json.exists());

        assertTrue(store.remove(pf));
        assertFalse(json.exists());
        assertEquals(0, store.getFiles().size());
    }

    @Test
    public void looseCsvInStoreDirAutoImports() throws IOException
    {
        // drop a CSV directly into the store directory (adb push workflow)
        File loose = new File(mDir, "dropped.csv");
        Writer w = new OutputStreamWriter(new FileOutputStream(loose), "UTF-8");
        w.write("28.6,-81.38,1\n28.61,-81.39,2\n");
        w.close();

        PoiStore store = new PoiStore(mDir);
        store.load();

        assertEquals(1, store.getFiles().size());
        assertEquals(2, store.getFiles().get(0).count());
        assertEquals(2, store.enabledCount());
    }

    @Test
    public void multipleFilesAggregate() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        store.importCsv(csv("a.csv", "28.6,-81.38,1\n"));
        PoiFile b = store.importCsv(csv("b.csv", "28.7,-81.40,2\n28.8,-81.41,3\n"));

        assertEquals(2, store.getFiles().size());
        assertEquals(3, store.enabledCount());

        store.setEnabled(b, false);
        assertEquals(1, store.enabledCount());
    }

    @Test
    public void reimportSameNameReplaces() throws IOException
    {
        PoiStore store = new PoiStore(mDir);
        store.load();
        store.importCsv(csv("cams.csv", "28.6,-81.38,1\n"));
        store.importCsv(csv("cams.csv", "28.6,-81.38,1\n28.7,-81.4,1\n28.8,-81.5,1\n"));

        assertEquals(1, store.getFiles().size());
        assertEquals(3, store.enabledCount());
    }
}
