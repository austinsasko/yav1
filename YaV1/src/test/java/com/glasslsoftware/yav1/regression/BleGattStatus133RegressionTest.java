package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;

import com.valentine.esp.ValentineESP;
import com.valentine.esp.bluetooth.V1connectionLE;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * [QA-REG] Pins the BLE GATT error handling of V1connectionLE.
 *
 * Bugs pinned:
 *  - the infamous GATT status 133: Android reports it on connect failures and
 *    spurious disconnects, sometimes with a bogus connection state. The
 *    original transport only reacted to STATE_DISCONNECTED + GATT_SUCCESS, so
 *    a 133 left the connect() caller blocked and the ESP reader spinning.
 *    Fixed in the BLE hardening of commit ee29637 (PR #2, "P0/P1 foundation:
 *    ... BLE hardening") and the reliability pass of commit 811d840 (PR #7,
 *    "Fix Android permission and BLE reliability issues"): any error status
 *    or disconnect closes the link, terminates the streams and unblocks the
 *    connect() waiter.
 *  - permission revocation mid-session (Android 12 runtime BT permissions):
 *    every GATT boundary catches SecurityException and fails the connection
 *    instead of crashing (commit 811d840). The SecurityException itself
 *    cannot be injected without a mockable BluetoothGatt seam (noted in the
 *    PR body); what is pinned here is the fail-closed contract those paths
 *    share: after any failure the transport reports IOException, never a
 *    crash or a hang.
 *
 * The GATT callback object is private; the tests reach it via reflection and
 * invoke it exactly like the Android Bluetooth stack would.
 */
public class BleGattStatus133RegressionTest
{
    private static final int GATT_ERROR_133 = 133;

    private V1connectionLE        mConn;
    private BluetoothGattCallback mCallback;

    @Before
    public void setUp() throws Exception
    {
        mConn = new V1connectionLE(new ValentineESP(10));

        Field f = V1connectionLE.class.getDeclaredField("m_gattCallback");
        f.setAccessible(true);
        mCallback = (BluetoothGattCallback) f.get(mConn);
    }

    private void invokeConnectionStateChange(int status, int newState)
    {
        // m_gatt is null before connectGatt succeeds; passing null matches it,
        // which is exactly the early-connect window where 133 shows up
        mCallback.onConnectionStateChange(null, status, newState);
    }

    private Object call(String method) throws Exception
    {
        Method m = V1connectionLE.class.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(mConn);
    }

    private boolean awaitReady(long timeoutMs) throws Exception
    {
        Method m = V1connectionLE.class.getDeclaredMethod("awaitReady", long.class);
        m.setAccessible(true);
        return ((Boolean) m.invoke(mConn, timeoutMs)).booleanValue();
    }

    @Test
    public void status133DisconnectClosesTheLink() throws Exception
    {
        call("beginConnectAttempt");

        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_DISCONNECTED);

        assertFalse(mConn.isReady());

        // the ESP reader must be shut down (available() throws -> the library
        // tears its threads down instead of spinning on a dead link)
        try
        {
            mConn.getInputStream().available();
            fail("available() must throw after a status-133 disconnect");
        }
        catch(IOException expected)
        {
        }
    }

    @Test
    public void status133UnblocksAPendingConnectAttemptQuickly() throws Exception
    {
        call("beginConnectAttempt");

        // the error arrives while connect() is still waiting for ready
        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_DISCONNECTED);

        long start = System.currentTimeMillis();
        assertFalse("a 133 must fail the connect attempt", awaitReady(10_000));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("the failed attempt must not wait for the timeout (took " + elapsed + " ms)",
                   elapsed < 5_000);
    }

    @Test
    public void errorStatusWithBogusConnectedStateStillClosesTheLink() throws Exception
    {
        // field-observed: status 133 delivered with newState = STATE_CONNECTED;
        // the guard must treat any non-success status as a failure
        call("beginConnectAttempt");

        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_CONNECTED);

        assertFalse(mConn.isReady());
        assertFalse(awaitReady(1_000));
    }

    @Test
    public void lateCallbackAfterCloseIsIgnored() throws Exception
    {
        mConn.close();

        // callbacks can still fire briefly after close(); they must be dropped
        invokeConnectionStateChange(BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_DISCONNECTED);

        assertFalse(mConn.isReady());
    }

    @Test
    public void reconnectAttemptAfterA133StartsClean() throws Exception
    {
        // first attempt dies with 133
        call("beginConnectAttempt");
        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_DISCONNECTED);
        assertFalse(awaitReady(1_000));

        // the retry must not inherit the dead state: markReady on the new
        // attempt brings the link up
        call("beginConnectAttempt");
        call("markReady");
        assertTrue(awaitReady(1_000));
        assertTrue(mConn.isReady());

        mConn.close();
        assertFalse(mConn.isReady());
    }

    @Test
    public void writesOnALinkKilledByA133FailWithIOException() throws Exception
    {
        call("beginConnectAttempt");
        invokeConnectionStateChange(GATT_ERROR_133, BluetoothProfile.STATE_DISCONNECTED);

        // the same fail-closed contract the permission-revocation paths use:
        // IOException to the writer thread, never a crash
        try
        {
            mConn.getOutputStream().write(new byte[] {0x7F, 0x01, 0x00, 0x7F});
            fail("writing on a 133-killed link must throw IOException");
        }
        catch(IOException expected)
        {
        }
    }

    @Test
    public void connectWithoutPermissionContextFailsClosed()
    {
        // connect() guards its arguments before touching the stack; the
        // SecurityException catch paths funnel into the same close()
        assertFalse(mConn.connect(null, null, 100));
        assertFalse(mConn.isReady());
    }
}
