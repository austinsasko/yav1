package com.glasslsoftware.yav1.aircraft;

/**
 * [P2-ADSB] One aircraft state vector from an ADS-B point query.
 * Pure data class, no Android dependencies.
 */
public class Aircraft
{
    /** ICAO 24-bit address, lower-case hex as sent by the API. */
    public String  hex      = "";

    /** Callsign / flight (often the registration for GA aircraft). */
    public String  flight   = "";

    /** Registration ("r" field), e.g. N25HP. */
    public String  reg      = "";

    /** ICAO type code ("t" field), e.g. C182. */
    public String  type     = "";

    public double  lat      = Double.NaN;
    public double  lon      = Double.NaN;

    /** Barometric altitude in feet; Integer.MIN_VALUE = unknown. */
    public int     altFt    = Integer.MIN_VALUE;

    /** True when the API reports the aircraft on the ground. */
    public boolean onGround = false;

    /** Ground speed in knots; NaN = unknown. */
    public double  gsKt     = Double.NaN;

    /** Track / heading over ground in degrees; NaN = unknown. */
    public double  trackDeg = Double.NaN;

    // -- multi-source aggregation (filled by AdsbAggregator) -----------

    /** when this state vector was fetched (wall clock ms); 0 = unset */
    public long    seenAtMs    = 0;

    /** distinct feeds that reported this hex within the merge window */
    public int     sourceCount = 0;

    /** name of the feed that provided this (freshest) state vector */
    public String  feed        = "";

    public boolean hasPosition()
    {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    public String bestIdent()
    {
        if(reg != null && !reg.isEmpty())
            return reg;
        if(flight != null && !flight.isEmpty())
            return flight;
        return hex;
    }
}
