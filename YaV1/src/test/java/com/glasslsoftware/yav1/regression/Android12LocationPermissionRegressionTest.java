package com.glasslsoftware.yav1.regression;

import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.functional.RepoFile;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * [QA-REG] Pins the Android 12 grouped location-permission setup.
 *
 * Bug: on Android 12+ a runtime request for ACCESS_FINE_LOCATION is silently
 * ignored unless ACCESS_COARSE_LOCATION is requested in the same dialog, and
 * a request is only ever shown for permissions declared in the manifest. The
 * app used to request fine location alone, so GPS lockouts and BTLE scanning
 * silently lost location on Android 12+.
 * Fixed in commit 811d840 (PR #7, "Fix Android permission and BLE
 * reliability issues"): YaV1Activity.requestNeededPermissions adds BOTH
 * location permissions to the request whenever either is missing, and
 * YaV1.hasLocationPermission accepts either grant.
 *
 * The runtime half of that logic lives in Activity code
 * (checkSelfPermission / requestPermissions) and needs an instrumented or
 * Robolectric environment; what a JVM test CAN pin is the declaration
 * contract the grouped request depends on. If someone trims the manifest
 * back to fine-only, these tests fail before a device ever would.
 */
public class Android12LocationPermissionRegressionTest
{
    private static String sManifest;

    @BeforeClass
    public static void readManifest() throws IOException
    {
        sManifest = RepoFile.read(RepoFile.find("src/main/AndroidManifest.xml"));
    }

    private static void assertDeclared(String permission)
    {
        assertTrue("AndroidManifest.xml must declare " + permission,
                   sManifest.contains("android.permission." + permission));
    }

    @Test
    public void fineAndCoarseLocationAreBothDeclared()
    {
        // Android 12 grouped-request contract: both must be declared so both
        // can be requested together
        assertDeclared("ACCESS_FINE_LOCATION");
        assertDeclared("ACCESS_COARSE_LOCATION");
    }

    @Test
    public void android12BluetoothRuntimePermissionsAreDeclared()
    {
        // the same fix introduced the Android 12 Bluetooth runtime pair the
        // BLE transport depends on
        assertDeclared("BLUETOOTH_SCAN");
        assertDeclared("BLUETOOTH_CONNECT");
    }

    @Test
    public void foregroundServiceTypesForLocationAndDeviceAreDeclared()
    {
        // Android 14+ kills typed foreground services without these
        assertDeclared("FOREGROUND_SERVICE_LOCATION");
        assertDeclared("FOREGROUND_SERVICE_CONNECTED_DEVICE");
    }

    @Test
    public void groupedRequestLogicIsPresentInTheActivity() throws IOException
    {
        // the request site must keep adding the two location permissions as a
        // pair; this is a source-level pin because the Activity cannot run on
        // the JVM (see PR body)
        String activity = RepoFile.read(
            RepoFile.find("src/main/java/com/glasslsoftware/yav1/YaV1Activity.java"));

        int fine   = activity.indexOf("missing.add(android.Manifest.permission.ACCESS_FINE_LOCATION)");
        int coarse = activity.indexOf("missing.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)");

        assertTrue("YaV1Activity must add ACCESS_FINE_LOCATION to the grouped request", fine >= 0);
        assertTrue("YaV1Activity must add ACCESS_COARSE_LOCATION to the grouped request", coarse >= 0);
    }
}
