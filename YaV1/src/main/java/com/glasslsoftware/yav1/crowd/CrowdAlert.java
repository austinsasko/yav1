package com.glasslsoftware.yav1.crowd;

/**
 * [CSA] One crowdsourced report (police / crash / hazard) from the Waze
 * live-map feed or the self-hosted YaV1 relay. Mirrors the iOS app's
 * CrowdAlert so both platforms treat crowd data identically.
 */
public class CrowdAlert
{
    public static final int KIND_POLICE   = 0;
    public static final int KIND_ACCIDENT = 1;
    public static final int KIND_HAZARD   = 2;

    /** stable per-report id (source uuid) so refreshes never re-announce */
    public final String id;
    public final int    kind;
    public final double lat;
    public final double lon;
    /** epoch ms of the report; 0 = unknown */
    public final long   reportedAtMs;
    public final int    thumbsUp;
    /** "waze" or "yav1" */
    public final String source;
    /** short qualifier from the source subtype ("visible"/"hidden"); null if none */
    public final String detail;

    public CrowdAlert(String id, int kind, double lat, double lon,
                      long reportedAtMs, int thumbsUp, String source)
    {
        this(id, kind, lat, lon, reportedAtMs, thumbsUp, source, null);
    }

    public CrowdAlert(String id, int kind, double lat, double lon,
                      long reportedAtMs, int thumbsUp, String source, String detail)
    {
        this.id           = id;
        this.kind         = kind;
        this.lat          = lat;
        this.lon          = lon;
        this.reportedAtMs = reportedAtMs;
        this.thumbsUp     = thumbsUp;
        this.source       = source;
        this.detail       = detail;
    }

    public String kindText()
    {
        String base;
        switch(kind)
        {
            case KIND_POLICE:   base = "Police reported"; break;
            case KIND_ACCIDENT: base = "Crash reported";  break;
            default:            base = "Hazard reported";  break;
        }
        return detail != null ? base + " (" + detail + ")" : base;
    }

    /** Map a Waze subtype to a short qualifier; only police carry a useful one. */
    public static String detailFor(int kind, String subtype)
    {
        if(kind != KIND_POLICE || subtype == null)
            return null;
        String s = subtype.toUpperCase();
        if(s.contains("HIDING") || s.contains("HIDDEN"))
            return "hidden";
        if(s.contains("VISIBLE"))
            return "visible";
        return null;
    }
}
