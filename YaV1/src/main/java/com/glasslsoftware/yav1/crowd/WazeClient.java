package com.glasslsoftware.yav1.crowd;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * [CSA] Minimal HTTP client for the Waze live-map feed (unofficial, key-less;
 * the same crowd pool Google Maps shows). Availability is best-effort — every
 * failure is soft (returns null). Called from a background thread only.
 * Follows the AdsbClient hardening: response cap, timeouts, injectable
 * endpoint for tests.
 */
public class WazeClient
{
    private static final String LOG_TAG = "Valentine CSA";

    public static final String DEFAULT_ENDPOINT =
        "https://www.waze.com/live-map/api/georss?top=%s&bottom=%s&left=%s&right=%s&env=na&types=alerts";

    /** bounding box half-size in degrees (~7 km at mid latitudes) */
    public static final double BOX_HALF_DEG = 0.07;

    public static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 8000;

    private final String mEndpoint;

    public WazeClient()
    {
        this(DEFAULT_ENDPOINT);
    }

    public WazeClient(String endpoint)
    {
        mEndpoint = endpoint;
    }

    /**
     * Fetch the raw feed JSON for a box around the location, or null on any
     * failure.
     */
    public String fetch(double lat, double lon)
    {
        String urlStr = String.format(Locale.US, mEndpoint,
                                      String.valueOf(lat + BOX_HALF_DEG),
                                      String.valueOf(lat - BOX_HALF_DEG),
                                      String.valueOf(lon - BOX_HALF_DEG),
                                      String.valueOf(lon + BOX_HALF_DEG));

        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if(code != 200)
            {
                Log.d(LOG_TAG, "waze feed http " + code);
                return null;
            }

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int    n;
            int    total = 0;

            while((n = in.read(chunk)) > 0)
            {
                total += n;
                if(total > MAX_RESPONSE_BYTES)
                {
                    Log.d(LOG_TAG, "waze feed oversized, dropped");
                    return null;
                }
                buffer.write(chunk, 0, n);
            }

            return new String(buffer.toByteArray(), Charset.forName("UTF-8"));
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "waze feed failed: " + e);
            return null;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }
}
