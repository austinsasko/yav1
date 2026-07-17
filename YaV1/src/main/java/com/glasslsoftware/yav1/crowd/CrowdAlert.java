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

    public CrowdAlert(String id, int kind, double lat, double lon,
                      long reportedAtMs, int thumbsUp, String source)
    {
        this.id           = id;
        this.kind         = kind;
        this.lat          = lat;
        this.lon          = lon;
        this.reportedAtMs = reportedAtMs;
        this.thumbsUp     = thumbsUp;
        this.source       = source;
    }

    public String kindText()
    {
        switch(kind)
        {
            case KIND_POLICE:   return "Police reported";
            case KIND_ACCIDENT: return "Crash reported";
            default:            return "Hazard reported";
        }
    }
}
