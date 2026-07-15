package com.franckyl.yav1.psl;

/**
 * [P1-PSL] Source of posted speed limits.
 *
 * Implementations must be non-blocking: return what is known right now
 * (typically from a cache) and, if needed, refresh in the background.
 */
public interface SpeedLimitProvider
{
    /**
     * Return the posted speed limit in km/h at the given location, or
     * null when the limit is unknown.
     *
     * @param lat     latitude in degrees
     * @param lon     longitude in degrees
     * @param bearing travel bearing in degrees (0-360, 0 = north), used to
     *                pick the road best aligned with the direction of travel
     */
    Integer getSpeedLimitKph(double lat, double lon, float bearing);
}
