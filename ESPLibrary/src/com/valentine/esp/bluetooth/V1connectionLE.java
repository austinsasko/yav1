package com.valentine.esp.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.valentine.esp.ValentineESP;
import com.valentine.esp.constants.ESPLibraryLogController;

/**
 * Bluetooth Low Energy (BTLE) transport for the V1connection LE dongle.
 *
 * The V1connection LE exposes a GATT service where ESP packets travel as bare frames
 * (SOF 0xAA ... EOF 0xAB) without the 0x7F PACK wrapper, length byte, wrapper checksum
 * or byte escaping used by the classic SPP V1connection. This class bridges between the
 * two formats so the existing DataReaderThread / DataWriterThread and ESPPacket parser
 * can be reused unchanged:
 *
 *   - Notifications from the V1 are reassembled into complete ESP frames, wrapped in
 *     PACK framing (delimiters, length, wrapper checksum, escaping) and exposed through
 *     an InputStream with a working available().
 *   - PACK frames written by DataWriterThread are unescaped, stripped back to the bare
 *     ESP frame and written to the client-out characteristic, serialized on the GATT
 *     write-complete callback.
 *
 * Service and characteristic UUIDs match Valentine Research's official ESP library.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class V1connectionLE
{
	private static final String LOG_TAG = "V1connectionLE";

	/** The UUID of the V1connection LE GATT service. */
	public static final UUID V1_CONNECTION_LE_SERVICE_UUID =
			UUID.fromString("92a0aff4-9e05-11e2-aa59-f23c91aec05e");
	/** Characteristic carrying data from the V1 to the client (notify). */
	public static final UUID V1_OUT_CLIENT_IN_SHORT_UUID =
			UUID.fromString("92a0b2ce-9e05-11e2-aa59-f23c91aec05e");
	/** Characteristic carrying data from the client to the V1 (write). */
	public static final UUID CLIENT_OUT_V1_IN_SHORT_UUID =
			UUID.fromString("92a0b6d4-9e05-11e2-aa59-f23c91aec05e");
	/** Standard Client Characteristic Configuration descriptor. */
	public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID =
			UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final byte PACK_DELIMITER   = 0x7F;
	private static final byte ESCAPE_BYTE      = 0x7D;
	private static final byte SOF              = (byte) 0xAA;
	private static final byte EOF              = (byte) 0xAB;

	// Maximum bytes per GATT write with the default ATT MTU of 23.
	private static final int  MAX_LE_WRITE     = 20;

	private final ValentineESP     m_esp;
	private BluetoothGatt          m_gatt;
	private BluetoothGattCharacteristic m_writeCharacteristic;

	private final BleInputStream   m_inputStream  = new BleInputStream();
	private final BleOutputStream  m_outputStream = new BleOutputStream();

	// Released by onCharacteristicWrite so writes stay serialized.
	private final Semaphore        m_writeComplete = new Semaphore(0);
	// Counted down once notifications are enabled and the link is usable.
	private CountDownLatch         m_readyLatch;
	private volatile boolean       m_ready;
	private volatile boolean       m_closed;

	// Reassembly buffer for ESP frames that span multiple notifications.
	private byte[]                 m_leBuffer = new byte[64];
	private int                    m_leBufferLen = 0;

	public V1connectionLE(ValentineESP _esp)
	{
		m_esp = _esp;
	}

	/**
	 * Connect to the V1connection LE device and block until the notification pipe is
	 * ready or the timeout elapses.
	 *
	 * @param _context   Context used to open the GATT connection.
	 * @param _device    The LE device to connect to.
	 * @param _timeoutMs How long to wait for the connection to become usable.
	 *
	 * @return true if the connection is ready for ESP traffic, else false.
	 */
	public boolean connect(Context _context, BluetoothDevice _device, long _timeoutMs)
	{
		if (_context == null || _device == null)
		{
			return false;
		}

		beginConnectAttempt();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
			// Force the LE transport; letting the stack pick on dual-mode devices is a
			// well known source of spurious GATT status 133 disconnects.
			m_gatt = _device.connectGatt(_context, false, m_gattCallback, BluetoothDevice.TRANSPORT_LE);
		}
		else
		{
			m_gatt = _device.connectGatt(_context, false, m_gattCallback);
		}

		if (m_gatt == null)
		{
			return false;
		}

		return awaitReady(_timeoutMs);
	}

	/**
	 * Reset the connection state for a fresh connect attempt.
	 *
	 * Package visible for unit testing.
	 */
	void beginConnectAttempt()
	{
		m_closed = false;
		m_ready = false;
		m_readyLatch = new CountDownLatch(1);
	}

	/**
	 * Block until the link becomes ready (markReady), the connection is closed, or the
	 * timeout elapses. On anything but a ready link the connection is closed.
	 *
	 * Package visible for unit testing.
	 *
	 * @return true if the link is ready for ESP traffic, else false.
	 */
	boolean awaitReady(long _timeoutMs)
	{
		try
		{
			m_readyLatch.await(_timeoutMs, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		if (!m_ready)
		{
			close();
			return false;
		}

		return true;
	}

	public boolean isReady()
	{
		return m_ready && !m_closed;
	}

	public InputStream getInputStream()
	{
		return m_inputStream;
	}

	public OutputStream getOutputStream()
	{
		return m_outputStream;
	}

	/**
	 * Tear down the GATT connection and unblock the streams. Safe to call repeatedly
	 * and from any thread (including the Bluetooth callback thread).
	 */
	public void close()
	{
		synchronized (this)
		{
			if (m_closed)
			{
				return;
			}
			m_closed = true;
			m_ready = false;
		}

		BluetoothGatt gatt = m_gatt;
		m_gatt = null;
		m_writeCharacteristic = null;
		if (gatt != null)
		{
			try
			{
				gatt.disconnect();
				gatt.close();
			}
			catch (Exception e)
			{
				if (ESPLibraryLogController.LOG_WRITE_WARNING)
				{
					Log.w(LOG_TAG, "Error closing GATT connection", e);
				}
			}
		}

		// Shutting the input stream down makes available() throw in DataReaderThread,
		// which shuts the whole ESP stack down cleanly (threads, queues, callbacks).
		m_inputStream.shutdown();
		// Unblock any writer waiting on a GATT write completion.
		m_writeComplete.release();

		// Unblock a connect() caller that is still waiting for the link to become ready.
		CountDownLatch latch = m_readyLatch;
		if (latch != null)
		{
			latch.countDown();
		}

		boolean wasConnected = m_esp.getIsConnected();
		m_esp.setIsConnected(false);
		if (wasConnected)
		{
			m_esp.broadcastV1Event(com.valentine.esp.ValentineClient.V1_ESP_CONNECTED, false);
		}
	}

	private final BluetoothGattCallback m_gattCallback = new BluetoothGattCallback()
	{
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
		{
			if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS)
			{
				if (ESPLibraryLogController.LOG_WRITE_DEBUG)
				{
					Log.d(LOG_TAG, "GATT connected, discovering services");
				}
				if (!gatt.discoverServices())
				{
					if (ESPLibraryLogController.LOG_WRITE_ERROR)
					{
						Log.e(LOG_TAG, "Unable to start GATT service discovery");
					}
					close();
				}
			}
			else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS)
			{
				// Covers both an orderly disconnect and error statuses (e.g. the
				// infamous 133) reported with a bogus connection state. close() shuts
				// the ESP stack down and releases any connect() waiter.
				if (ESPLibraryLogController.LOG_WRITE_DEBUG)
				{
					Log.d(LOG_TAG, "GATT disconnected (status " + status + ", state " + newState + ")");
				}
				close();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status)
		{
			if (status != BluetoothGatt.GATT_SUCCESS)
			{
				close();
				return;
			}

			BluetoothGattService service = gatt.getService(V1_CONNECTION_LE_SERVICE_UUID);
			if (service == null)
			{
				if (ESPLibraryLogController.LOG_WRITE_ERROR)
				{
					Log.e(LOG_TAG, "V1connection LE service not found on device");
				}
				close();
				return;
			}

			m_writeCharacteristic = service.getCharacteristic(CLIENT_OUT_V1_IN_SHORT_UUID);
			BluetoothGattCharacteristic notifyCharacteristic =
					service.getCharacteristic(V1_OUT_CLIENT_IN_SHORT_UUID);

			if (m_writeCharacteristic == null || notifyCharacteristic == null)
			{
				close();
				return;
			}

			gatt.setCharacteristicNotification(notifyCharacteristic, true);
			BluetoothGattDescriptor descriptor =
					notifyCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
			if (descriptor != null)
			{
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				if (!gatt.writeDescriptor(descriptor))
				{
					if (ESPLibraryLogController.LOG_WRITE_ERROR)
					{
						Log.e(LOG_TAG, "Unable to write the notification descriptor");
					}
					close();
				}
			}
			else
			{
				// No CCC descriptor; assume notifications are on and continue.
				markReady();
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
		{
			if (CLIENT_CHARACTERISTIC_CONFIGURATION_UUID.equals(descriptor.getUuid()))
			{
				if (status == BluetoothGatt.GATT_SUCCESS)
				{
					markReady();
				}
				else
				{
					if (ESPLibraryLogController.LOG_WRITE_ERROR)
					{
						Log.e(LOG_TAG, "Enabling notifications failed with status " + status);
					}
					close();
				}
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
		{
			m_writeComplete.release();
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
		{
			byte[] value = characteristic.getValue();
			if (value != null && value.length > 0)
			{
				handleNotification(value);
			}
		}
	};

	/**
	 * Mark the link usable and release a connect() waiter. A close() that happened
	 * first wins the race and the connection stays down.
	 *
	 * Package visible for unit testing.
	 */
	void markReady()
	{
		synchronized (this)
		{
			if (m_closed)
			{
				// close() won the race (e.g. a disconnect arrived while the descriptor
				// write completion was in flight); do not resurrect the connection.
				return;
			}
			m_ready = true;
		}
		m_esp.setIsConnected(true);
		m_esp.broadcastV1Event(com.valentine.esp.ValentineClient.V1_ESP_CONNECTED, true);
		CountDownLatch latch = m_readyLatch;
		if (latch != null)
		{
			latch.countDown();
		}
	}

	/**
	 * Reassemble bare ESP frames from notification data and hand complete frames to the
	 * input stream in PACK framing.
	 *
	 * Package visible for unit testing.
	 */
	synchronized void handleNotification(byte[] _data)
	{
		if (m_closed)
		{
			// A GATT callback can still fire briefly after close(); drop the data so
			// stale packets are not processed on a dead connection.
			return;
		}

		// Append to the reassembly buffer, growing it if needed.
		if (m_leBufferLen + _data.length > m_leBuffer.length)
		{
			byte[] grown = new byte[Math.max(m_leBuffer.length * 2, m_leBufferLen + _data.length)];
			System.arraycopy(m_leBuffer, 0, grown, 0, m_leBufferLen);
			m_leBuffer = grown;
		}
		System.arraycopy(_data, 0, m_leBuffer, m_leBufferLen, _data.length);
		m_leBufferLen += _data.length;

		while (true)
		{
			// Resynchronize on the start of frame byte.
			int drop = 0;
			while (drop < m_leBufferLen && m_leBuffer[drop] != SOF)
			{
				drop++;
			}
			if (drop > 0)
			{
				if (ESPLibraryLogController.LOG_WRITE_WARNING)
				{
					Log.w(LOG_TAG, "Dropping " + drop + " bytes while looking for SOF");
				}
				System.arraycopy(m_leBuffer, drop, m_leBuffer, 0, m_leBufferLen - drop);
				m_leBufferLen -= drop;
			}

			if (m_leBufferLen < 5)
			{
				// Not enough data to know the frame length yet.
				return;
			}

			// Frame layout: SOF dest orig id payloadLen payload... EOF
			int payloadLen = m_leBuffer[4] & 0xFF;
			int frameLen = 6 + payloadLen;
			if (m_leBufferLen < frameLen)
			{
				return;
			}

			if (m_leBuffer[frameLen - 1] != EOF)
			{
				// Bad frame; drop the SOF byte and resynchronize.
				if (ESPLibraryLogController.LOG_WRITE_WARNING)
				{
					Log.w(LOG_TAG, "Frame missing EOF, resynchronizing");
				}
				System.arraycopy(m_leBuffer, 1, m_leBuffer, 0, m_leBufferLen - 1);
				m_leBufferLen -= 1;
				continue;
			}

			byte[] espFrame = new byte[frameLen];
			System.arraycopy(m_leBuffer, 0, espFrame, 0, frameLen);
			System.arraycopy(m_leBuffer, frameLen, m_leBuffer, 0, m_leBufferLen - frameLen);
			m_leBufferLen -= frameLen;

			m_inputStream.append(wrapInPackFraming(espFrame));
		}
	}

	/**
	 * Wrap a bare ESP frame in the PACK framing produced by the classic SPP V1connection:
	 * 0x7F, length, escaped frame bytes, wrapper checksum, 0x7F.
	 *
	 * Package visible for unit testing.
	 */
	static byte[] wrapInPackFraming(byte[] _espFrame)
	{
		int checksum = _espFrame.length; // The length byte is included in the checksum.
		for (int i = 0; i < _espFrame.length; i++)
		{
			checksum += (_espFrame[i] & 0xFF);
		}

		// Worst case every inner byte needs escaping.
		byte[] out = new byte[(_espFrame.length + 2) * 2 + 2];
		int pos = 0;
		out[pos++] = PACK_DELIMITER;
		pos = appendEscaped(out, pos, (byte) _espFrame.length);
		for (int i = 0; i < _espFrame.length; i++)
		{
			pos = appendEscaped(out, pos, _espFrame[i]);
		}
		pos = appendEscaped(out, pos, (byte) checksum);
		out[pos++] = PACK_DELIMITER;

		byte[] result = new byte[pos];
		System.arraycopy(out, 0, result, 0, pos);
		return result;
	}

	private static int appendEscaped(byte[] _out, int _pos, byte _value)
	{
		if (_value == PACK_DELIMITER)
		{
			_out[_pos++] = ESCAPE_BYTE;
			_out[_pos++] = 0x5F;
		}
		else if (_value == ESCAPE_BYTE)
		{
			_out[_pos++] = ESCAPE_BYTE;
			_out[_pos++] = 0x5D;
		}
		else
		{
			_out[_pos++] = _value;
		}
		return _pos;
	}

	/**
	 * Strip PACK framing written by DataWriterThread back to the bare ESP frame:
	 * remove the delimiters, unescape, then drop the length byte and wrapper checksum.
	 *
	 * @return the bare ESP frame, or null if the buffer is not a valid PACK frame.
	 *
	 * Package visible for unit testing.
	 */
	static byte[] stripPackFraming(byte[] _packFrame)
	{
		if (_packFrame == null || _packFrame.length < 4
				|| _packFrame[0] != PACK_DELIMITER
				|| _packFrame[_packFrame.length - 1] != PACK_DELIMITER)
		{
			return null;
		}

		// Unescape the bytes between the delimiters.
		byte[] unescaped = new byte[_packFrame.length];
		int len = 0;
		for (int i = 1; i < _packFrame.length - 1; i++)
		{
			byte cur = _packFrame[i];
			if (cur == ESCAPE_BYTE && i + 1 < _packFrame.length - 1)
			{
				i++;
				cur = (_packFrame[i] == 0x5F) ? PACK_DELIMITER
						: (_packFrame[i] == 0x5D) ? ESCAPE_BYTE : _packFrame[i];
			}
			unescaped[len++] = cur;
		}

		// unescaped now holds: length, ESP frame..., wrapper checksum
		if (len < 3)
		{
			return null;
		}

		byte[] espFrame = new byte[len - 2];
		System.arraycopy(unescaped, 1, espFrame, 0, len - 2);
		return espFrame;
	}

	/**
	 * InputStream fed by GATT notifications. available() reports buffered bytes and
	 * throws once the connection is gone so DataReaderThread shuts the library down.
	 */
	private class BleInputStream extends InputStream
	{
		private byte[] m_buffer = new byte[256];
		private int m_start = 0;
		private int m_end = 0;
		private boolean m_shutdown = false;

		synchronized void append(byte[] _data)
		{
			int size = m_end - m_start;
			if (size + _data.length > m_buffer.length)
			{
				byte[] grown = new byte[Math.max(m_buffer.length * 2, size + _data.length)];
				System.arraycopy(m_buffer, m_start, grown, 0, size);
				m_buffer = grown;
				m_start = 0;
				m_end = size;
			}
			else if (m_end + _data.length > m_buffer.length)
			{
				System.arraycopy(m_buffer, m_start, m_buffer, 0, size);
				m_start = 0;
				m_end = size;
			}
			System.arraycopy(_data, 0, m_buffer, m_end, _data.length);
			m_end += _data.length;
			notifyAll();
		}

		synchronized void shutdown()
		{
			m_shutdown = true;
			notifyAll();
		}

		@Override
		public synchronized int available() throws IOException
		{
			int size = m_end - m_start;
			if (size == 0 && m_shutdown)
			{
				throw new IOException("V1connection LE link closed");
			}
			return size;
		}

		@Override
		public synchronized int read() throws IOException
		{
			while (m_end - m_start == 0)
			{
				if (m_shutdown)
				{
					return -1;
				}
				try
				{
					wait(500);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while reading from LE link");
				}
			}
			return m_buffer[m_start++] & 0xFF;
		}

		@Override
		public synchronized int read(byte[] _out, int _off, int _len) throws IOException
		{
			if (_len == 0)
			{
				return 0;
			}
			while (m_end - m_start == 0)
			{
				if (m_shutdown)
				{
					return -1;
				}
				try
				{
					wait(500);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while reading from LE link");
				}
			}
			int count = Math.min(_len, m_end - m_start);
			System.arraycopy(m_buffer, m_start, _out, _off, count);
			m_start += count;
			return count;
		}
	}

	/**
	 * OutputStream that accepts PACK frames from DataWriterThread and forwards the bare
	 * ESP frame over GATT, serialized on the write-complete callback.
	 */
	private class BleOutputStream extends OutputStream
	{
		// Accumulator for byte-at-a-time writers; DataWriterThread writes whole frames.
		private byte[] m_pending = new byte[64];
		private int m_pendingLen = 0;

		@Override
		public void write(int _b) throws IOException
		{
			if (m_pendingLen == m_pending.length)
			{
				byte[] grown = new byte[m_pending.length * 2];
				System.arraycopy(m_pending, 0, grown, 0, m_pendingLen);
				m_pending = grown;
			}
			m_pending[m_pendingLen++] = (byte) _b;

			// A frame is complete when it starts and ends with the delimiter.
			if (m_pendingLen >= 4 && m_pending[0] == PACK_DELIMITER
					&& m_pending[m_pendingLen - 1] == PACK_DELIMITER)
			{
				byte[] frame = new byte[m_pendingLen];
				System.arraycopy(m_pending, 0, frame, 0, m_pendingLen);
				m_pendingLen = 0;
				sendFrame(frame);
			}
		}

		@Override
		public void write(byte[] _buffer) throws IOException
		{
			write(_buffer, 0, _buffer.length);
		}

		@Override
		public void write(byte[] _buffer, int _off, int _len) throws IOException
		{
			byte[] frame = new byte[_len];
			System.arraycopy(_buffer, _off, frame, 0, _len);
			sendFrame(frame);
		}

		private void sendFrame(byte[] _packFrame) throws IOException
		{
			if (m_closed || m_gatt == null || m_writeCharacteristic == null)
			{
				throw new IOException("V1connection LE link closed");
			}

			byte[] espFrame = stripPackFraming(_packFrame);
			if (espFrame == null)
			{
				if (ESPLibraryLogController.LOG_WRITE_ERROR)
				{
					Log.e(LOG_TAG, "Discarding malformed PACK frame of " + _packFrame.length + " bytes");
				}
				return;
			}

			int offset = 0;
			while (offset < espFrame.length)
			{
				int chunkLen = Math.min(MAX_LE_WRITE, espFrame.length - offset);
				byte[] chunk = new byte[chunkLen];
				System.arraycopy(espFrame, offset, chunk, 0, chunkLen);
				offset += chunkLen;

				BluetoothGatt gatt = m_gatt;
				BluetoothGattCharacteristic characteristic = m_writeCharacteristic;
				if (gatt == null || characteristic == null)
				{
					throw new IOException("V1connection LE link closed");
				}

				characteristic.setValue(chunk);
				if (!gatt.writeCharacteristic(characteristic))
				{
					throw new IOException("GATT write failed");
				}

				try
				{
					// Wait for onCharacteristicWrite before the next write.
					if (!m_writeComplete.tryAcquire(2, TimeUnit.SECONDS)
							&& ESPLibraryLogController.LOG_WRITE_WARNING)
					{
						Log.w(LOG_TAG, "Timed out waiting for GATT write completion");
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted during GATT write");
				}

				if (m_closed)
				{
					throw new IOException("V1connection LE link closed");
				}
			}
		}
	}
}
