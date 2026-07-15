package com.franckyl.yav1.aircraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * [P2-ADSB] Watchlist of known law-enforcement aircraft.
 *
 * CSV format: icao_hex,registration,agency[,model[,source[,confidence]]]
 * with '#' comments. The curated starter list ships as an asset
 * (assets/aircraft/enforcement_hex.csv, sourced from the FAA registry and
 * plane-alert-db, see its header) and is merged with an optional user file
 * (&lt;app storage&gt;/aircraft/enforcement_user.csv). Matching is
 * case-insensitive by ICAO hex first, then by registration / callsign.
 * Pure java.io, unit tested.
 *
 * confidence is "high" / "medium" / "low"; rotorcraft are shipped as
 * "low" (mostly patrol / medevac, rarely speed timing) and callers
 * should phrase such matches as tentative.
 */
public class EnforcementWatchlist
{
    public static class Entry
    {
        public final String hex;
        public final String reg;
        public final String agency;
        public final String model;
        public final String source;
        public final String confidence;

        Entry(String hex, String reg, String agency, String model,
              String source, String confidence)
        {
            this.hex        = hex;
            this.reg        = reg;
            this.agency     = agency;
            this.model      = model;
            this.source     = source;
            this.confidence = confidence;
        }

        /** true when the entry is a tentative match (e.g. patrol rotorcraft) */
        public boolean lowConfidence()
        {
            return "low".equalsIgnoreCase(confidence);
        }
    }

    private final Map<String, Entry> mByHex = new HashMap<String, Entry>();
    private final Map<String, Entry> mByReg = new HashMap<String, Entry>();

    public int size()
    {
        return mByHex.size();
    }

    /**
     * Merge entries from a CSV stream into this list. Malformed lines are
     * skipped. Returns the number of entries added.
     */
    public int load(Reader reader) throws IOException
    {
        BufferedReader br = (reader instanceof BufferedReader
                                ? (BufferedReader) reader : new BufferedReader(reader));

        int added = 0;
        String line;

        while((line = br.readLine()) != null)
        {
            line = line.trim();
            if(line.isEmpty() || line.startsWith("#"))
                continue;

            String[] f = line.split(",", -1);
            if(f.length < 3)
                continue;

            String hex = f[0].trim().toUpperCase(Locale.US);
            String reg = f[1].trim().toUpperCase(Locale.US);

            if(hex.isEmpty() || !hex.matches("[0-9A-F]{6}"))
                continue;

            Entry e = new Entry(hex, reg, f[2].trim(),
                                f.length > 3 ? f[3].trim() : "",
                                f.length > 4 ? f[4].trim() : "",
                                f.length > 5 ? f[5].trim() : "");

            mByHex.put(hex, e);
            if(!reg.isEmpty())
                mByReg.put(reg, e);
            added++;
        }

        return added;
    }

    /** Match an aircraft by hex, then registration, then callsign. */
    public Entry match(Aircraft ac)
    {
        if(ac == null)
            return null;

        if(ac.hex != null && !ac.hex.isEmpty())
        {
            Entry e = mByHex.get(ac.hex.trim().toUpperCase(Locale.US));
            if(e != null)
                return e;
        }

        if(ac.reg != null && !ac.reg.isEmpty())
        {
            Entry e = mByReg.get(ac.reg.trim().toUpperCase(Locale.US));
            if(e != null)
                return e;
        }

        if(ac.flight != null && !ac.flight.isEmpty())
        {
            Entry e = mByReg.get(ac.flight.trim().toUpperCase(Locale.US));
            if(e != null)
                return e;
        }

        return null;
    }
}
