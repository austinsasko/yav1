package com.glasslsoftware.yav1.poi;

/**
 * [P2-POI] A single point of interest (camera / enforcement point) loaded from
 * a user-imported CSV file. Pure data class, no Android dependencies.
 */
public class Poi
{
    public double lat;
    public double lon;

    /** Free-form type token from the source file ("1", "redlight", ...). */
    public String type;

    /** Speed limit in the source file's unit (usually km/h), 0 when absent. */
    public int    speed;

    /** Optional name / comment, "" when absent. */
    public String name;

    /**
     * Provenance: "" for user CSV imports, "osm:speed_camera" /
     * "osm:enforcement" / "osm:alpr" for the online sources.
     */
    public String source = "";

    public Poi()
    {
        type = "";
        name = "";
    }

    public Poi(double lat, double lon, String type, int speed, String name)
    {
        this.lat   = lat;
        this.lon   = lon;
        this.type  = (type == null ? "" : type);
        this.speed = speed;
        this.name  = (name == null ? "" : name);
    }

    public Poi(double lat, double lon, String type, int speed, String name, String source)
    {
        this(lat, lon, type, speed, name);
        this.source = (source == null ? "" : source);
    }

    /**
     * Human label used in alerts: the name when present, otherwise a label
     * derived from the type code.
     */
    public String label()
    {
        if(name != null && !name.isEmpty())
            return name;
        return typeLabel(type);
    }

    /**
     * Map common camera-type codes to a speakable label. The numeric codes
     * follow the usual IGO speedcam.txt convention (1 fixed speed camera,
     * 2 mobile / average-speed, 3 red-light camera, 4 section control,
     * 5 red-light + speed) but providers differ, so unknown codes fall back
     * to a generic label.
     */
    public static String typeLabel(String type)
    {
        if(type == null)
            return "Camera";

        String t = type.trim().toLowerCase();

        if(t.contains("alpr") || t.contains("plate"))
            return "License plate reader";
        if(t.equals("1") || t.contains("fixed") || t.contains("speed"))
            return "Speed camera";
        if(t.equals("2") || t.contains("mobile") || t.contains("average"))
            return "Mobile camera";
        if(t.equals("3") || t.contains("red"))
            return "Red light camera";
        if(t.equals("4") || t.contains("section"))
            return "Section control";
        if(t.equals("5") || t.contains("combo"))
            return "Red light and speed camera";
        if(t.isEmpty())
            return "Camera";

        // free-form text type: use it as-is, capitalized
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
