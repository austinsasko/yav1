package com.glasslsoftware.yav1.crowd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * [CSA] CrowdRelayClient against a local socket server, mirroring
 * AdsbClientTest: parse on 200, null/false on non-200 and unreachable hosts,
 * response size cap, and the pure relay-URL validation used at pref-save
 * time.
 */
public class CrowdRelayClientTest
{
    private ServerSocket mServer;
    private Thread       mThread;
    private int          mPort;

    private final List<String> mPaths  = new ArrayList<String>();
    private final List<String> mBodies = new ArrayList<String>();

    private volatile int    mStatus = 200;
    private volatile byte[] mBody   = "{\"reports\":[]}".getBytes();

    @Before
    public void startServer() throws IOException
    {
        mServer = new ServerSocket(0);
        mPort   = mServer.getLocalPort();

        mThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(!mServer.isClosed())
                {
                    try
                    {
                        Socket s = mServer.accept();
                        serve(s);
                    }
                    catch(IOException e)
                    {
                        return; // server closed
                    }
                }
            }
        }, "CrowdRelayClientTest-server");
        mThread.setDaemon(true);
        mThread.start();
    }

    private void serve(Socket s) throws IOException
    {
        try
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String request = br.readLine();               // "GET /path HTTP/1.1"
            String line;
            int    contentLength = 0;
            while((line = br.readLine()) != null && !line.isEmpty())
            {
                if(line.toLowerCase(Locale.US).startsWith("content-length:"))
                    contentLength = Integer.parseInt(line.substring(15).trim());
            }

            if(request != null)
            {
                String[] parts = request.split(" ");
                if(parts.length >= 2)
                {
                    synchronized(mPaths)
                    {
                        mPaths.add(parts[1]);
                    }
                }
            }

            if(contentLength > 0)
            {
                char[] buf = new char[contentLength];
                int    read = 0;
                while(read < contentLength)
                {
                    int n = br.read(buf, read, contentLength - read);
                    if(n < 0)
                        break;
                    read += n;
                }
                synchronized(mBodies)
                {
                    mBodies.add(new String(buf, 0, read));
                }
            }

            byte[] body = mBody;
            String head = "HTTP/1.1 " + mStatus + (mStatus == 200 ? " OK" : " Error") + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.length + "\r\n"
                        + "Connection: close\r\n\r\n";

            OutputStream os = s.getOutputStream();
            os.write(head.getBytes("UTF-8"));
            os.write(body);
            os.flush();
        }
        finally
        {
            s.close();
        }
    }

    @After
    public void stopServer() throws IOException
    {
        if(mServer != null)
            mServer.close();
    }

    private String local()
    {
        return "http://127.0.0.1:" + mPort;
    }

    /** privileged port with no listener: connection refused immediately */
    private String unroutable()
    {
        return "http://127.0.0.1:1";
    }

    private List<String> paths()
    {
        synchronized(mPaths)
        {
            return new ArrayList<String>(mPaths);
        }
    }

    private List<String> bodies()
    {
        synchronized(mBodies)
        {
            return new ArrayList<String>(mBodies);
        }
    }

    // ------------------------------------------------------------ fetch

    @Test
    public void fetchParsesReportsOn200()
    {
        mBody = ("{\"reports\":[{\"id\":\"r-1\",\"kind\":\"police\","
               + "\"lat\":39.73,\"lon\":-104.99,\"at\":1752750000}]}").getBytes();

        List<CrowdAlert> alerts = new CrowdRelayClient(local()).fetch(39.73, -104.99, 15);

        assertEquals(1, alerts.size());
        assertEquals("r-1", alerts.get(0).id);
        assertEquals(CrowdAlert.KIND_POLICE, alerts.get(0).kind);
        assertEquals(1, paths().size());
        assertTrue(paths().get(0).startsWith("/alerts?lat=39.73&lon=-104.99&radius_km=15"));
    }

    @Test
    public void fetchNullOnNon200()
    {
        mStatus = 503;
        assertNull(new CrowdRelayClient(local()).fetch(39.73, -104.99, 15));
    }

    @Test
    public void fetchNullWhenOversized()
    {
        byte[] big = new byte[WazeClient.MAX_RESPONSE_BYTES + 4096];
        Arrays.fill(big, (byte) 'x');
        mBody = big;

        assertNull(new CrowdRelayClient(local()).fetch(39.73, -104.99, 15));
    }

    @Test
    public void fetchNullWhenUnreachable()
    {
        assertNull(new CrowdRelayClient(unroutable()).fetch(39.73, -104.99, 15));
    }

    @Test
    public void fetchNullWhenNotConfigured()
    {
        assertNull(new CrowdRelayClient("").fetch(39.73, -104.99, 15));
        assertNull(new CrowdRelayClient(null).fetch(39.73, -104.99, 15));
        assertTrue(paths().isEmpty()); // no network attempt
    }

    // ------------------------------------------------------------ report

    @Test
    public void reportPostsKindAndLocation()
    {
        assertTrue(new CrowdRelayClient(local()).report(CrowdAlert.KIND_POLICE, 39.73, -104.99));

        assertEquals(1, paths().size());
        assertEquals("/report", paths().get(0));
        assertEquals(1, bodies().size());
        String body = bodies().get(0);
        assertTrue(body.contains("\"kind\":\"police\""));
        assertTrue(body.contains("39.73"));
        assertTrue(body.contains("-104.99"));
    }

    @Test
    public void reportFalseOnNon200()
    {
        mStatus = 500;
        assertFalse(new CrowdRelayClient(local()).report(CrowdAlert.KIND_POLICE, 39.73, -104.99));
    }

    @Test
    public void reportFalseWhenUnreachable()
    {
        assertFalse(new CrowdRelayClient(unroutable()).report(CrowdAlert.KIND_POLICE, 39.73, -104.99));
    }

    @Test
    public void reportFalseWhenNotConfigured()
    {
        assertFalse(new CrowdRelayClient("").report(CrowdAlert.KIND_POLICE, 39.73, -104.99));
        assertTrue(paths().isEmpty());
    }

    // ------------------------------------------------------------ url validation

    @Test
    public void validUrlIsNormalized()
    {
        assertEquals("https://relay.example.com",
                     CrowdRelayClient.validateRelayUrl("  https://relay.example.com/  "));
        assertEquals("https://relay.example.com/csa",
                     CrowdRelayClient.validateRelayUrl("https://relay.example.com/csa///"));
        assertEquals("https://relay.example.com:8443",
                     CrowdRelayClient.validateRelayUrl("https://relay.example.com:8443"));
    }

    @Test
    public void emptyMeansRelayOff()
    {
        assertEquals("", CrowdRelayClient.validateRelayUrl(""));
        assertEquals("", CrowdRelayClient.validateRelayUrl("   "));
        assertEquals("", CrowdRelayClient.validateRelayUrl(null));
    }

    @Test
    public void cleartextHttpIsRejected()
    {
        // targetSdk 35 blocks cleartext silently at request time, so it must
        // be refused up front at save time
        assertNull(CrowdRelayClient.validateRelayUrl("http://relay.example.com"));
    }

    @Test
    public void malformedUrlsAreRejected()
    {
        assertNull(CrowdRelayClient.validateRelayUrl("relay.example.com"));
        assertNull(CrowdRelayClient.validateRelayUrl("https://"));
        assertNull(CrowdRelayClient.validateRelayUrl("https://exa mple.com"));
        assertNull(CrowdRelayClient.validateRelayUrl("not a url"));
        assertNull(CrowdRelayClient.validateRelayUrl("ftp://relay.example.com"));
    }
}
