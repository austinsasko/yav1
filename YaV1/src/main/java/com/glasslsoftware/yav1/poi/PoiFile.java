package com.glasslsoftware.yav1.poi;

import java.util.ArrayList;
import java.util.List;

/**
 * [P2-POI] One imported POI database file: metadata plus its points.
 * Serialized to JSON (Gson) under &lt;app storage&gt;/poi/.
 */
public class PoiFile
{
    /** Display name (source file base name). */
    public String    name       = "";

    /** Import timestamp (wall clock ms). */
    public long      importedAt = 0;

    /** Included in the runtime index? */
    public boolean   enabled    = true;

    /** Absolute path of the CSV the file was imported from (informational). */
    public String    source     = "";

    /** Rows skipped during import (malformed). */
    public int       skipped    = 0;

    public List<Poi> pois       = new ArrayList<Poi>();

    /** Backing JSON file, not serialized. */
    public transient java.io.File jsonFile = null;

    public int count()
    {
        return pois == null ? 0 : pois.size();
    }
}
