package com.glasslsoftware.yav1.crowd;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [CSA] Client for the self-hosted YaV1 crowd relay (Server/csa-worker.js in
 * the yav1-ios repo — Cloudflare Workers free tier). Anonymous by
 * construction: a report is kind + location only. Inactive until the user
 * configures the relay URL in preferences.
 */
public class CrowdRelayClient
{
    private static final String LOG_TAG = "Valentine CSA";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS    = 8000;

    private final String mBaseUrl;

    public CrowdRelayClient(String baseUrl)
    {
        mBaseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public boolean isConfigured()
    {
        return !mBaseUrl.isEmpty();
    }

    /** Fetch nearby reports, or null on any failure (soft). */
    public List<CrowdAlert> fetch(double lat, double lon, int radiusKm)
    {
        if(!isConfigured())
            return null;

        HttpURLConnection conn = null;

        try
        {
            String url = mBaseUrl + "/alerts?lat=" + URLEncoder.encode(String.valueOf(lat), "UTF-8")
                       + "&lon=" + URLEncoder.encode(String.valueOf(lon), "UTF-8")
                       + "&radius_km=" + radiusKm;

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            if(conn.getResponseCode() != 200)
                return null;

            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int    n;

            while((n = in.read(chunk)) > 0)
                buffer.write(chunk, 0, n);

            return parseRelayResponse(new String(buffer.toByteArray(), Charset.forName("UTF-8")));
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "relay fetch failed: " + e);
            return null;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }

    /** Post an anonymous report; returns success. */
    public boolean report(int kind, double lat, double lon)
    {
        if(!isConfigured())
            return false;

        String kindStr;
        switch(kind)
        {
            case CrowdAlert.KIND_POLICE:   kindStr = "police"; break;
            case CrowdAlert.KIND_ACCIDENT: kindStr = "accident"; break;
            default:                       kindStr = "hazard"; break;
        }

        HttpURLConnection conn = null;

        try
        {
            conn = (HttpURLConnection) new URL(mBaseUrl + "/report").openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject body = new JSONObject();
            body.put("kind", kindStr);
            body.put("lat", lat);
            body.put("lon", lon);

            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes(Charset.forName("UTF-8")));
            out.close();

            return conn.getResponseCode() == 200;
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "relay report failed: " + e);
            return false;
        }
        finally
        {
            if(conn != null)
                conn.disconnect();
        }
    }

    /** Parse the relay's {"reports":[{id,kind,lat,lon,at}]} response. Pure, tested. */
    public static List<CrowdAlert> parseRelayResponse(String json)
    {
        List<CrowdAlert> result = new ArrayList<CrowdAlert>();

        try
        {
            JSONArray reports = new JSONObject(json).optJSONArray("reports");
            if(reports == null)
                return result;

            for(int i = 0; i < reports.length(); i++)
            {
                JSONObject item = reports.optJSONObject(i);
                if(item == null)
                    continue;

                String id      = item.optString("id", "");
                String kindStr = item.optString("kind", "").toLowerCase(Locale.US);
                if(id.isEmpty())
                    continue;

                int kind;
                if(kindStr.equals("police"))
                    kind = CrowdAlert.KIND_POLICE;
                else if(kindStr.equals("accident"))
                    kind = CrowdAlert.KIND_ACCIDENT;
                else if(kindStr.equals("hazard"))
                    kind = CrowdAlert.KIND_HAZARD;
                else
                    continue;

                result.add(new CrowdAlert(id, kind,
                                          item.optDouble("lat", 0),
                                          item.optDouble("lon", 0),
                                          (long)(item.optDouble("at", 0) * 1000),
                                          0, "yav1"));
            }
        }
        catch(Exception e)
        {
            // soft failure
        }

        return result;
    }
}
