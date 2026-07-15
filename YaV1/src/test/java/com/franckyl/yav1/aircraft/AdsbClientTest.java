package com.franckyl.yav1.aircraft;

import static org.junit.Assert.assertEquals;
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

/**
 * [P2-ADSB] AdsbClient against a local socket server: endpoint fallback,
 * failure softness, response size cap and radius clamping. Mirrors the
 * failure modes observed against the live services (2026-07-14): non-200
 * answers, unreachable hosts and oversized bodies.
 */
public class AdsbClientTest
{
    private ServerSocket mServer;
    private Thread       mThread;
    private int          mPort;

    private final List<String> mPaths = new ArrayList<String>();

    private volatile int    mStatus = 200;
    private volatile byte[] mBody   = "{\"ac\":[]}".getBytes();

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
        }, "AdsbClientTest-server");
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
            while((line = br.readLine()) != null && !line.isEmpty())
                ;                                          // drain headers

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

    private String local(String prefix)
    {
        return "http://127.0.0.1:" + mPort + "/" + prefix + "/%s/%s/%d";
    }

    /** privileged port with no listener: connection refused immediately */
    private String unroutable()
    {
        return "http://127.0.0.1:1/unused/%s/%s/%d";
    }

    private List<String> paths()
    {
        synchronized(mPaths)
        {
            return new ArrayList<String>(mPaths);
        }
    }

    @Test
    public void primaryAnswerIsUsed()
    {
        AdsbClient c = new AdsbClient(new String[] { local("a"), local("b") });

        String body = c.fetchPoint(33.64, -84.43, 10);

        assertEquals("{\"ac\":[]}", body);
        assertEquals(1, paths().size());
        assertEquals("/a/33.6400/-84.4300/10", paths().get(0));
    }

    @Test
    public void fallbackUsedWhenPrimaryIsUnreachable()
    {
        AdsbClient c = new AdsbClient(new String[] { unroutable(), local("b") });

        String body = c.fetchPoint(33.64, -84.43, 10);

        assertEquals("{\"ac\":[]}", body);
        assertEquals(1, paths().size());
        assertTrue(paths().get(0).startsWith("/b/"));
    }

    @Test
    public void allEndpointsTriedOnServerErrors()
    {
        mStatus = 503;

        AdsbClient c = new AdsbClient(new String[] { local("a"), local("b") });

        // primary gets 503 -> fallback also 503 -> null, both were hit
        assertNull(c.fetchPoint(33.64, -84.43, 10));
        assertEquals(2, paths().size());
        assertTrue(paths().get(0).startsWith("/a/"));
        assertTrue(paths().get(1).startsWith("/b/"));
    }

    @Test
    public void nullWhenEveryEndpointIsDown()
    {
        AdsbClient c = new AdsbClient(new String[] { unroutable(), unroutable() });
        assertNull(c.fetchPoint(33.64, -84.43, 10));
    }

    @Test
    public void oversizedResponseIsDropped()
    {
        byte[] big = new byte[AdsbClient.MAX_RESPONSE_BYTES + 4096];
        Arrays.fill(big, (byte) 'x');
        mBody = big;

        AdsbClient c = new AdsbClient(new String[] { local("a") });
        assertNull(c.fetchPoint(33.64, -84.43, 10));
    }

    @Test
    public void radiusIsClampedToApiRange()
    {
        AdsbClient c = new AdsbClient(new String[] { local("a") });

        c.fetchPoint(32.90, -97.04, 500);
        c.fetchPoint(32.90, -97.04, 0);

        assertEquals(2, paths().size());
        assertTrue(paths().get(0).endsWith("/" + AdsbClient.MAX_RADIUS_NM));
        assertTrue(paths().get(1).endsWith("/" + AdsbClient.MIN_RADIUS_NM));
    }

    @Test
    public void coordinatesUseFixedUsLocaleFormatting()
    {
        AdsbClient c = new AdsbClient(new String[] { local("a") });

        c.fetchPoint(-33.865143, 151.2099, 25);

        assertEquals("/a/-33.8651/151.2099/25", paths().get(0));
    }
}
