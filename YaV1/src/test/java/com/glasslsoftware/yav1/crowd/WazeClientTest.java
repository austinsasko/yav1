package com.glasslsoftware.yav1.crowd;

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
 * [CSA] WazeClient against a local socket server, mirroring AdsbClientTest:
 * body pass-through on 200, soft null on non-200 / unreachable, response size
 * cap, and the bounding box in the query.
 */
public class WazeClientTest
{
    private ServerSocket mServer;
    private Thread       mThread;
    private int          mPort;

    private final List<String> mPaths = new ArrayList<String>();

    private volatile int    mStatus = 200;
    private volatile byte[] mBody   = "{\"alerts\":[]}".getBytes();

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
        }, "WazeClientTest-server");
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

    private String local()
    {
        return "http://127.0.0.1:" + mPort + "/georss?top=%s&bottom=%s&left=%s&right=%s";
    }

    /** privileged port with no listener: connection refused immediately */
    private String unroutable()
    {
        return "http://127.0.0.1:1/georss?top=%s&bottom=%s&left=%s&right=%s";
    }

    private List<String> paths()
    {
        synchronized(mPaths)
        {
            return new ArrayList<String>(mPaths);
        }
    }

    @Test
    public void bodyReturnedOn200()
    {
        WazeClient c = new WazeClient(local());
        assertEquals("{\"alerts\":[]}", c.fetch(39.73, -104.99));
    }

    @Test
    public void boundingBoxIsBuiltAroundLocation()
    {
        new WazeClient(local()).fetch(39.73, -104.99);

        assertEquals(1, paths().size());
        String path = paths().get(0);
        assertTrue(path.contains("top=" + (39.73 + WazeClient.BOX_HALF_DEG)));
        assertTrue(path.contains("bottom=" + (39.73 - WazeClient.BOX_HALF_DEG)));
        assertTrue(path.contains("left=" + (-104.99 - WazeClient.BOX_HALF_DEG)));
        assertTrue(path.contains("right=" + (-104.99 + WazeClient.BOX_HALF_DEG)));
    }

    @Test
    public void nullOnNon200()
    {
        mStatus = 503;
        assertNull(new WazeClient(local()).fetch(39.73, -104.99));
    }

    @Test
    public void nullWhenOversized()
    {
        byte[] big = new byte[WazeClient.MAX_RESPONSE_BYTES + 4096];
        Arrays.fill(big, (byte) 'x');
        mBody = big;

        assertNull(new WazeClient(local()).fetch(39.73, -104.99));
    }

    @Test
    public void nullWhenUnreachable()
    {
        assertNull(new WazeClient(unroutable()).fetch(39.73, -104.99));
    }
}
