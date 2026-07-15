package com.franckyl.yav1.poi;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * [P2-POI] On-device store for imported POI databases.
 *
 * Each imported CSV becomes one JSON file (Gson) in the store directory
 * (&lt;app storage&gt;/poi/). Multiple files are supported with a per-file
 * enabled flag. As a convenience, any *.csv dropped directly into the store
 * directory (e.g. via adb or a file manager) is auto-imported on load.
 *
 * Pure java.io + Gson, unit tested against a temp directory.
 */
public class PoiStore
{
    private final File mDir;
    private final Gson mGson = new Gson();

    private final List<PoiFile> mFiles = new ArrayList<PoiFile>();

    private String mLastError = "";

    public PoiStore(File dir)
    {
        mDir = dir;
    }

    public String getLastError()
    {
        return mLastError;
    }

    public File getDir()
    {
        return mDir;
    }

    /** Load all stored files (and auto-import loose CSVs). Thread-safe-ish: call from one thread. */
    public synchronized void load()
    {
        mFiles.clear();

        if(!mDir.isDirectory() && !mDir.mkdirs())
            return;

        // auto-import loose CSV files without a corresponding .json
        File[] all = mDir.listFiles();
        if(all == null)
            return;

        for(File f: all)
        {
            String n = f.getName().toLowerCase();
            if(n.endsWith(".csv") && !jsonFor(f).exists())
                importCsv(f);
        }

        // importCsv() adds to mFiles; the JSON scan below re-reads everything,
        // so start from a clean list to avoid duplicates
        mFiles.clear();

        all = mDir.listFiles();
        if(all == null)
            return;

        for(File f: all)
        {
            if(!f.getName().toLowerCase().endsWith(".json"))
                continue;

            PoiFile pf = read(f);
            if(pf != null)
            {
                pf.jsonFile = f;
                mFiles.add(pf);
            }
        }
    }

    /** The currently loaded files (metadata + points). */
    public synchronized List<PoiFile> getFiles()
    {
        return new ArrayList<PoiFile>(mFiles);
    }

    /**
     * Import a CSV file. On success the parsed database is saved to the store
     * and added to the loaded list (replacing an earlier import of the same
     * name).
     *
     * @return the imported file, or null on failure (see getLastError()).
     */
    public synchronized PoiFile importCsv(File src)
    {
        mLastError = "";

        if(!mDir.isDirectory() && !mDir.mkdirs())
        {
            mLastError = "cannot create " + mDir;
            return null;
        }

        PoiCsvParser.Result r;
        InputStreamReader in = null;

        try
        {
            in = new InputStreamReader(new FileInputStream(src), "UTF-8");
            r = PoiCsvParser.parse(in);
        }
        catch(IOException e)
        {
            mLastError = e.toString();
            return null;
        }
        finally
        {
            if(in != null)
            {
                try { in.close(); } catch(IOException ignored) {}
            }
        }

        if(r.pois.isEmpty())
        {
            mLastError = "no valid POI rows found (" + r.skipped + " rows skipped)";
            return null;
        }

        PoiFile pf    = new PoiFile();
        pf.name       = src.getName();
        pf.importedAt = System.currentTimeMillis();
        pf.enabled    = true;
        pf.source     = src.getAbsolutePath();
        pf.skipped    = r.skipped;
        pf.pois       = r.pois;
        pf.jsonFile   = jsonFor(src);

        if(!write(pf))
            return null;

        // replace a previous import of the same name
        for(int i = mFiles.size() - 1; i >= 0; i--)
        {
            if(mFiles.get(i).name.equals(pf.name))
                mFiles.remove(i);
        }
        mFiles.add(pf);

        return pf;
    }

    /**
     * Insert or replace a programmatically generated database (online
     * sources). Unlike importCsv this takes the points directly; the
     * user's enabled flag from an earlier generation is preserved.
     *
     * @return the stored file, or null on write failure.
     */
    public synchronized PoiFile putGenerated(String name, List<Poi> pois, String source)
    {
        mLastError = "";

        if(!mDir.isDirectory() && !mDir.mkdirs())
        {
            mLastError = "cannot create " + mDir;
            return null;
        }

        boolean enabled = true;
        for(int i = mFiles.size() - 1; i >= 0; i--)
        {
            if(mFiles.get(i).name.equals(name))
            {
                enabled = mFiles.get(i).enabled;
                mFiles.remove(i);
            }
        }

        PoiFile pf    = new PoiFile();
        pf.name       = name;
        pf.importedAt = System.currentTimeMillis();
        pf.enabled    = enabled;
        pf.source     = source;
        pf.skipped    = 0;
        pf.pois       = new ArrayList<Poi>(pois);
        pf.jsonFile   = jsonFor(new File(name));

        if(!write(pf))
            return null;

        mFiles.add(pf);
        return pf;
    }

    /** Toggle a file's enabled flag and persist it. */
    public synchronized boolean setEnabled(PoiFile pf, boolean enabled)
    {
        pf.enabled = enabled;
        return write(pf);
    }

    /** Remove an imported file from the store (deletes its JSON). */
    public synchronized boolean remove(PoiFile pf)
    {
        mFiles.remove(pf);

        boolean ok = true;
        if(pf.jsonFile != null && pf.jsonFile.exists())
            ok = pf.jsonFile.delete();

        // also remove a loose CSV of the same name inside the store dir,
        // otherwise it would silently re-import on next load
        File csv = new File(mDir, pf.name);
        if(csv.exists() && csv.getParentFile().equals(mDir))
            csv.delete();

        return ok;
    }

    /** All POIs of all enabled files (for index building). */
    public synchronized List<Poi> enabledPois()
    {
        List<Poi> out = new ArrayList<Poi>();
        for(PoiFile pf: mFiles)
        {
            if(pf.enabled && pf.pois != null)
                out.addAll(pf.pois);
        }
        return out;
    }

    /** Total count of POIs in enabled files. */
    public synchronized int enabledCount()
    {
        int n = 0;
        for(PoiFile pf: mFiles)
            if(pf.enabled)
                n += pf.count();
        return n;
    }

    // ------------------------------------------------------------------ io

    private File jsonFor(File src)
    {
        String base = src.getName();
        // sanitize: keep it a simple flat name
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(mDir, base + ".json");
    }

    private PoiFile read(File f)
    {
        InputStreamReader in = null;
        try
        {
            in = new InputStreamReader(new FileInputStream(f), "UTF-8");
            return mGson.fromJson(in, PoiFile.class);
        }
        catch(Exception e)
        {
            mLastError = e.toString();
            return null;
        }
        finally
        {
            if(in != null)
            {
                try { in.close(); } catch(IOException ignored) {}
            }
        }
    }

    private boolean write(PoiFile pf)
    {
        if(pf.jsonFile == null)
            pf.jsonFile = jsonFor(new File(pf.name));

        Writer out = null;
        try
        {
            out = new OutputStreamWriter(new FileOutputStream(pf.jsonFile), "UTF-8");
            mGson.toJson(pf, out);
            return true;
        }
        catch(Exception e)
        {
            mLastError = e.toString();
            return false;
        }
        finally
        {
            if(out != null)
            {
                try { out.close(); } catch(IOException ignored) {}
            }
        }
    }
}
