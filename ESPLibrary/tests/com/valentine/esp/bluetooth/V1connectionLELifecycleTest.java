package com.valentine.esp.bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.valentine.esp.ValentineESP;
import com.valentine.esp.constants.PacketId;
import com.valentine.esp.packets.ESPPacket;
import com.valentine.esp.packets.TestFrames;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reconnect / teardown stress tests for the V1connection LE bridge. These hammer the
 * lifecycle paths that hurt in the field: connect timeouts, close() racing the GATT
 * callback thread, double close, writes against a dead link and notification data
 * arriving after close. No exception may leak and the streams must terminate.
 */
public class V1connectionLELifecycleTest
{
	private static final int V1_NIBBLE = 0x0A;
	private static final int GB_NIBBLE = 0x08;

	private static final byte[] DISPLAY_PAYLOAD =
			{0x77, 0x00, 0x00, 0x22, 0x00, 0x41, 0x00, 0x00};

	private V1connectionLE m_conn;

	@Before
	public void setUp()
	{
		ESPPacket.resetDecoderState();
		m_conn = new V1connectionLE(new ValentineESP(10));
	}

	/** A bare ESP frame (SOF..EOF) like the ones carried in LE notifications. */
	private static byte[] displayFrame()
	{
		return TestFrames.bareFrame(GB_NIBBLE, V1_NIBBLE, 0x31, DISPLAY_PAYLOAD, true);
	}

	/** A complete PACK frame like the ones DataWriterThread hands to the output stream. */
	private static byte[] packFrame()
	{
		return TestFrames.escape(TestFrames.packFrame(
				TestFrames.bareFrame(V1_NIBBLE, 0x06, 0x01, new byte[0], true)));
	}

	@Test
	public void doubleCloseIsSafeAndIdempotent()
	{
		m_conn.close();
		m_conn.close();
		assertFalse(m_conn.isReady());
	}

	@Test
	public void connectRejectsNullArguments()
	{
		assertFalse(m_conn.connect(null, null, 100));
		assertFalse(m_conn.isReady());
	}

	@Test
	public void connectTimeoutClosesTheLinkAndShutsDownTheInputStream() throws Exception
	{
		m_conn.beginConnectAttempt();

		long start = System.currentTimeMillis();
		assertFalse("nobody marked the link ready, awaitReady must time out",
				m_conn.awaitReady(200));
		long elapsed = System.currentTimeMillis() - start;
		assertTrue("awaitReady returned before the timeout: " + elapsed + "ms", elapsed >= 150);

		assertFalse(m_conn.isReady());

		try
		{
			m_conn.getInputStream().available();
			fail("available() must throw once the link is closed");
		}
		catch (IOException expected)
		{
		}
	}

	@Test
	public void markReadyFromAnotherThreadCompletesTheConnectAttempt() throws Exception
	{
		m_conn.beginConnectAttempt();

		Thread callback = new Thread(new Runnable()
		{
			public void run()
			{
				m_conn.markReady();
			}
		});
		callback.start();

		assertTrue(m_conn.awaitReady(5000));
		assertTrue(m_conn.isReady());
		callback.join(5000);

		m_conn.close();
		assertFalse(m_conn.isReady());
	}

	@Test
	public void closeFromCallbackThreadDuringConnectAttemptUnblocksAwaitReady() throws Exception
	{
		m_conn.beginConnectAttempt();

		Thread callback = new Thread(new Runnable()
		{
			public void run()
			{
				m_conn.close();
			}
		});
		callback.start();

		long start = System.currentTimeMillis();
		assertFalse(m_conn.awaitReady(10000));
		long elapsed = System.currentTimeMillis() - start;
		assertTrue("close() must unblock awaitReady well before the timeout, took "
				+ elapsed + "ms", elapsed < 8000);
		assertFalse(m_conn.isReady());
		callback.join(5000);
	}

	@Test
	public void markReadyAfterCloseDoesNotResurrectTheConnection()
	{
		m_conn.beginConnectAttempt();
		m_conn.close();

		// simulates the descriptor write completion arriving after a disconnect
		m_conn.markReady();

		assertFalse(m_conn.isReady());
	}

	@Test
	public void closeUnblocksABlockedReader() throws Exception
	{
		final InputStream in = m_conn.getInputStream();
		final AtomicInteger result = new AtomicInteger(-999);
		final List<Throwable> errors = new ArrayList<Throwable>();
		final CountDownLatch started = new CountDownLatch(1);

		Thread reader = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					started.countDown();
					result.set(in.read());
				}
				catch (Throwable t)
				{
					errors.add(t);
				}
			}
		});
		reader.start();

		assertTrue(started.await(5, TimeUnit.SECONDS));
		Thread.sleep(50); // let the reader block inside read()
		m_conn.close();

		reader.join(5000);
		assertFalse("reader thread still blocked after close()", reader.isAlive());
		assertTrue("reader threw " + errors, errors.isEmpty());
		assertEquals("read() must return end-of-stream after close", -1, result.get());
	}

	@Test
	public void bufferedDataRemainsReadableAfterCloseThenTheStreamTerminates() throws Exception
	{
		m_conn.handleNotification(displayFrame());
		m_conn.close();

		InputStream in = m_conn.getInputStream();
		ArrayList<Byte> received = new ArrayList<Byte>();
		int avail = in.available();
		assertTrue("data buffered before close must remain readable", avail > 0);
		for (int i = 0; i < avail; i++)
		{
			received.add((byte) in.read());
		}

		// the buffered bytes must be a parseable PACK frame
		ESPPacket p = ESPPacket.makeFromBuffer(received);
		assertNotNull(p);
		assertEquals(PacketId.infDisplayData, p.getPacketIdentifier());

		// and once drained the stream terminates
		assertEquals(-1, in.read());
		try
		{
			in.available();
			fail("available() must throw once the link is closed and drained");
		}
		catch (IOException expected)
		{
		}
	}

	@Test
	public void notificationDataArrivingAfterCloseIsDropped() throws Exception
	{
		m_conn.close();
		m_conn.handleNotification(displayFrame());

		try
		{
			m_conn.getInputStream().available();
			fail("data arriving after close must not resurrect the stream");
		}
		catch (IOException expected)
		{
		}
		assertEquals(-1, m_conn.getInputStream().read());
	}

	@Test
	public void notificationsSplitAcrossChunksReassembleIntoOneFrame() throws Exception
	{
		// BLE delivers at most 20 bytes per notification with the default MTU
		byte[] frame = displayFrame();
		int chunk = 5;
		for (int off = 0; off < frame.length; off += chunk)
		{
			int len = Math.min(chunk, frame.length - off);
			byte[] part = new byte[len];
			System.arraycopy(frame, off, part, 0, len);
			m_conn.handleNotification(part);
		}

		InputStream in = m_conn.getInputStream();
		ArrayList<Byte> received = new ArrayList<Byte>();
		int avail = in.available();
		assertTrue(avail > 0);
		for (int i = 0; i < avail; i++)
		{
			received.add((byte) in.read());
		}

		ESPPacket p = ESPPacket.makeFromBuffer(received);
		assertNotNull(p);
		assertEquals(PacketId.infDisplayData, p.getPacketIdentifier());
	}

	@Test
	public void garbageBeforeTheStartOfFrameIsSkipped() throws Exception
	{
		byte[] frame = displayFrame();
		byte[] noisy = new byte[3 + frame.length];
		noisy[0] = 0x00;
		noisy[1] = 0x42;
		noisy[2] = 0x13;
		System.arraycopy(frame, 0, noisy, 3, frame.length);

		m_conn.handleNotification(noisy);

		InputStream in = m_conn.getInputStream();
		ArrayList<Byte> received = new ArrayList<Byte>();
		int avail = in.available();
		for (int i = 0; i < avail; i++)
		{
			received.add((byte) in.read());
		}

		ESPPacket p = ESPPacket.makeFromBuffer(received);
		assertNotNull(p);
		assertEquals(PacketId.infDisplayData, p.getPacketIdentifier());
	}

	@Test
	public void writeOnAClosedLinkThrowsIOExceptionInsteadOfCrashing() throws Exception
	{
		m_conn.close();
		try
		{
			m_conn.getOutputStream().write(packFrame());
			fail("writing on a closed link must throw IOException");
		}
		catch (IOException expected)
		{
		}
	}

	@Test
	public void writeWithoutAGattConnectionThrowsIOException() throws Exception
	{
		// never connected: no GATT, but not closed either
		try
		{
			m_conn.getOutputStream().write(packFrame());
			fail("writing without a GATT connection must throw IOException");
		}
		catch (IOException expected)
		{
		}
	}

	@Test
	public void byteAtATimeWriterFailsCleanlyOnAClosedLink()
	{
		m_conn.close();
		OutputStream out = m_conn.getOutputStream();
		byte[] frame = packFrame();

		try
		{
			for (int i = 0; i < frame.length; i++)
			{
				out.write(frame[i] & 0xFF);
			}
			fail("completing a frame byte-at-a-time on a closed link must throw IOException");
		}
		catch (IOException expected)
		{
		}
	}

	@Test
	public void concurrentCloseFromManyThreadsIsSafe() throws Exception
	{
		final int threads = 8;
		final CyclicBarrier barrier = new CyclicBarrier(threads);
		final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<Throwable>());
		List<Thread> workers = new ArrayList<Thread>();

		for (int i = 0; i < threads; i++)
		{
			Thread t = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						barrier.await(5, TimeUnit.SECONDS);
						m_conn.close();
					}
					catch (Throwable t)
					{
						errors.add(t);
					}
				}
			});
			workers.add(t);
			t.start();
		}

		for (Thread t : workers)
		{
			t.join(5000);
			assertFalse(t.isAlive());
		}
		assertTrue("concurrent close threw: " + errors, errors.isEmpty());
		assertFalse(m_conn.isReady());
	}

	@Test
	public void repeatedConnectCloseCyclesNeverLeakExceptions() throws Exception
	{
		final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<Throwable>());

		for (int cycle = 0; cycle < 100; cycle++)
		{
			m_conn.beginConnectAttempt();

			final boolean ready = (cycle % 2 == 0);
			Thread callback = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						if (ready)
						{
							m_conn.markReady();
						}
						else
						{
							m_conn.close();
						}
					}
					catch (Throwable t)
					{
						errors.add(t);
					}
				}
			});
			callback.start();

			boolean connected = m_conn.awaitReady(5000);
			assertEquals(ready, connected);
			callback.join(5000);

			if (connected)
			{
				// push a frame through while up, then tear down mid-traffic
				m_conn.handleNotification(displayFrame());
				m_conn.close();
			}
			assertFalse(m_conn.isReady());
		}

		assertTrue("lifecycle cycles threw: " + errors, errors.isEmpty());

		// after the last close the input stream must be terminated (drain, then EOF)
		InputStream in = m_conn.getInputStream();
		while (true)
		{
			int avail;
			try
			{
				avail = in.available();
			}
			catch (IOException expected)
			{
				break;
			}
			if (avail == 0)
			{
				assertEquals(-1, in.read());
				break;
			}
			in.read();
		}
	}
}
