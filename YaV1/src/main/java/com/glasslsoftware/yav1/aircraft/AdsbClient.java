package com.glasslsoftware.yav1.aircraft;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * [P2-ADSB] Minimal HTTP client for free, key-less ADS-B point queries.
 *
 * Primary endpoint:  https://api.adsb.lol/v2/point/{lat}/{lon}/{radius_nm}
 * Fallback:          https://api.airplanes.live/v2/point/{lat}/{lon}/{radius_nm}
 *
 * Both are community services without an API key; availability is
 * best-effort, so every failure is soft (returns null). Called from a
 * background thread only.
 *
 * Hardening from the live pass (2026-07-14): responses are capped at
 * {@link #MAX_RESPONSE_BYTES} (an oversized radius produced a 400KB+
 * payload that trickled past 30s) and the radius is clamped to the API
 * range. Endpoints are injectable for tests.
 */
public class AdsbClient
{
    private static final String LOG_TAG = "Valentine ADSB";

    private static final String[] DEFAULT_ENDPOINTS = {
        "https://api.adsb.lol/v2/point/%s/%s/%d",
        "https://api.airplanes.live/v2/point/%s/%s/%d",
    };

    /** radius bounds accepted by the /v2/point APIs */
    public static final int MIN_RADIUS_NM = 1;
    public static final int MAX_RADIUS_NM = 250;

    /** hard cap on the response body; larger bodies are dropped */
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 8000;

    private final String[] mEndpoints;

    public AdsbClient()
    {
        this(DEFAULT_ENDPOINTS);
    }

    /** visible for tests: endpoint patterns with %s lat, %s lon, %d radius */
    public AdsbClient(String[] endpoints)
    {
        mEndpoints = endpoints;
    }

    /**
     * Fetch the raw JSON for all aircraft within radiusNm of the position,
     * or null when every endpoint failed.
     */
    public String fetchPoint(double lat, double lon, int radiusNm)
    {
        if(radiusNm < MIN_RADIUS_NM)
            radiusNm = MIN_RADIUS_NM;
        else if(radiusNm > MAX_RADIUS_NM)
            radiusNm = MAX_RADIUS_NM;

        // coordinates with fixed US-locale formatting (the API path is /lat/lon/)
        String sLat = String.format(Locale.US, "%.4f", lat);
        String sLon = String.format(Locale.US, "%.4f", lon);

        for(String pattern: mEndpoints)
        {
            String url = String.format(Locale.US, pattern, sLat, sLon, radiusNm);
            String body = get(url);
            if(body != null)
                return body;
        }

        return null;
    }

    /** Fetch one URL with the hardened transport (timeouts, size cap). */
    public String fetchUrl(String url)
    {
        return get(url);
    }

    private String get(String url)
    {
        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "YaV1/2.1 (radar detector companion; ADS-B awareness)");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if(code != 200)
            {
                Log.d(LOG_TAG, "HTTP " + code + " from " + url);
                return null;
            }

            InputStream in = conn.getInputStream();
            try
            {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte chunk[] = new byte[8192];
                int  n;
                while((n = in.read(chunk)) > 0)
                {
                    buf.write(chunk, 0, n);
                    if(buf.size() > MAX_RESPONSE_BYTES)
                    {
                        Log.d(LOG_TAG, "response too large from " + url + ", dropping");
                        return null;
                    }
                }

                return new String(buf.toByteArray(), Charset.forName("UTF-8"));
            }
            finally
            {
                in.close();
            }
        }
        catch(IOException e)
        {
            Log.d(LOG_TAG, "fetch failed " + url + ": " + e);
            return null;
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "fetch error " + url + ": " + e);
            return null;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }
}
