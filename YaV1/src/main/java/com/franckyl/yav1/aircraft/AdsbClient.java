package com.franckyl.yav1.aircraft;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
 */
public class AdsbClient
{
    private static final String LOG_TAG = "Valentine ADSB";

    private static final String[] ENDPOINTS = {
        "https://api.adsb.lol/v2/point/%s/%s/%d",
        "https://api.airplanes.live/v2/point/%s/%s/%d",
    };

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 8000;

    /**
     * Fetch the raw JSON for all aircraft within radiusNm of the position,
     * or null when every endpoint failed.
     */
    public String fetchPoint(double lat, double lon, int radiusNm)
    {
        // coordinates with fixed US-locale formatting (the API path is /lat/lon/)
        String sLat = String.format(Locale.US, "%.4f", lat);
        String sLon = String.format(Locale.US, "%.4f", lon);

        for(String pattern: ENDPOINTS)
        {
            String url = String.format(Locale.US, pattern, sLat, sLon, radiusNm);
            String body = get(url);
            if(body != null)
                return body;
        }

        return null;
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
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null)
                sb.append(line);
            br.close();

            return sb.toString();
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
