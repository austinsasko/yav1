package com.glasslsoftware.yav1.poi;

import java.util.List;

/**
 * [P2-POI] A network-backed camera / enforcement-point source.
 *
 * Implementations build a query for an area around the vehicle and parse
 * the response into {@link Poi}s carrying a provenance tag ({@link
 * Poi#source}). The fetch scheduling (tiles, TTL, rate limits, offline
 * cache) lives in {@link PoiOnlineManager}; sources stay pure logic so
 * they can be unit tested with recorded fixtures.
 *
 * Concrete v1 implementation: {@link OverpassCameraSource} (OSM data:
 * speed cameras, enforcement relations, ALPR/Flock surveillance nodes -
 * the same data DeFlock.me surfaces).
 *
 * Commercial subscription databases (SCDB-style feeds; the "excam"-like
 * products) would slot in here as an authenticated implementation that
 * downloads the provider's CSV/JSON for the region and maps it to Poi -
 * the interface is deliberately transport-agnostic. Until then, exports
 * from such products can be imported manually through the existing CSV
 * path (PoiCsvParser understands the common IGO speedcam.txt format).
 */
public interface PoiOnlineSource
{
    /** short identifier, also used as the provenance prefix */
    String name();

    /** preference key gating this source (see pref_poi.xml) */
    String prefKey();

    /**
     * Query text to POST for the area (radiusM around lat/lon).
     * kinds are source-specific toggles resolved by the manager.
     */
    String buildQuery(double lat, double lon, int radiusM,
                      boolean wantCams, boolean wantAlpr);

    /** Parse a response body into POIs; fail soft to an empty list. */
    List<Poi> parse(String body, boolean wantCams, boolean wantAlpr);
}
