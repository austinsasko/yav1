/**
 * Created by franck on 6/29/13.
 */

package com.glasslsoftware.yav1;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.valentine.esp.ValentineESP;
import com.valentine.esp.bluetooth.V1connectionLE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class ListPairedBTActivity extends ListActivity
{
    // How long a BTLE scan runs before it is stopped automatically.
    private static final long LE_SCAN_PERIOD_MS = 10000;
    private static final int  BT_PERMISSION_REQUEST = 9912;

    private BluetoothAdapter m_bta;
    private Set<BluetoothDevice> m_pairedDevices;

    private ArrayList<String> m_deviceNameList;
    private ArrayList<BluetoothDevice> m_devicesList;
    private ArrayList<Integer> m_connectionTypeList;
    private ArrayAdapter<String> m_pairedAdapter;
    private ListView m_listview;

    private Button  m_scanLeButton;
    private boolean m_leScanning = false;
    private Handler m_handler = new Handler();

    TextView m_searchingBar;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // we show our view
        setContentView(R.layout.listpairbt_activity);

        m_scanLeButton = (Button) findViewById(R.id.scan_le_button);
        m_scanLeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (m_leScanning)
                    stopLeScan();
                else
                    startLeScan();
            }
        });

        // make sure we hold the Bluetooth runtime permissions before touching the adapter
        requestBtPermissionsIfNeeded();

        // get the bluethooh paired adapter (if false), we must have an error set
        setUpBlueTooth(true);
    }

    // we click on an adpater

    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        stopLeScan();
        // retrieve the device at the given position
        BluetoothDevice sDevice = m_devicesList.get(position);
        // we respond
        Intent response = new Intent();
        response.putExtra("SelectedBluetoothDevice", sDevice);
        response.putExtra("SelectedConnectionType", m_connectionTypeList.get(position).intValue());
        setResult(Activity.RESULT_OK, response);
        finish();
    }

    @Override
    protected void onResume()
    {
        YaV1.superResume();
        super.onResume();
        setUpBlueTooth(false);
    }

    @Override
    public void onDestroy()
    {
        stopLeScan();
        super.onDestroy();
    }


    @Override
    public void onPause()
    {
        stopLeScan();
        YaV1.superPause();
        super.onPause();
    }

    /** Request the Android 12+ Bluetooth permissions (or location below that) if missing. */
    private void requestBtPermissionsIfNeeded()
    {
        if (Build.VERSION.SDK_INT < 23)
            return;

        ArrayList<String> missing = new ArrayList<String>();

        if (Build.VERSION.SDK_INT >= 31)
        {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // BTLE scan results require location below Android 12
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!missing.isEmpty())
            requestPermissions(missing.toArray(new String[0]), BT_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BT_PERMISSION_REQUEST)
            setUpBlueTooth(false);
    }

    private boolean setUpBlueTooth(boolean warn)
    {
        m_bta = BluetoothAdapter.getDefaultAdapter();

        if(m_bta == null || !m_bta.isEnabled())
        {
            mShowMessage(getString(R.string.bluetooth_not_running), getString(R.string.error));
            return false;
        }

        // get the paired adapters to populate the list

        m_deviceNameList     = new ArrayList<String>();
        m_devicesList        = new ArrayList<BluetoothDevice>();
        m_connectionTypeList = new ArrayList<Integer>();
        try
        {
            m_pairedDevices = m_bta.getBondedDevices();
        }
        catch (SecurityException e)
        {
            // BLUETOOTH_CONNECT not granted yet
            m_pairedDevices = Collections.emptySet();
        }

        // hope we have devices

        if (m_pairedDevices != null && m_pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : m_pairedDevices)
            {
                String name;
                try
                {
                    name = device.getName();
                }
                catch (SecurityException e)
                {
                    name = null;
                }
                if (name == null)
                    name = device.getAddress();
                if (name == null)
                    continue;
                int connectionType = getDeviceConnectionType(device);
                if (connectionType == ValentineESP.CONNECTION_LE)
                    name += " " + getString(R.string.bt_le_suffix);
                m_deviceNameList.add(name);
                m_devicesList.add(device);
                m_connectionTypeList.add(connectionType);
            }
        }

        m_pairedAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, m_deviceNameList);

        // set the list
        setListAdapter(m_pairedAdapter);
        // always warn user that the list show only the paired devices
        if(warn)
            mShowMessage(getString(R.string.device_must_be_paired), getString(R.string.warning));
        return true;
    }

    /**
     * Determine which Bluetooth transport to use for a device. Bonded V1connection LE
     * dongles report DEVICE_TYPE_LE, everything else uses classic SPP.
     */
    private int getDeviceConnectionType(BluetoothDevice device)
    {
        try
        {
            if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE)
                return ValentineESP.CONNECTION_LE;
        }
        catch (SecurityException e)
        {
            // fall through to SPP
        }
        return ValentineESP.CONNECTION_SPP;
    }

    // BTLE scan handling, using the modern scanner API with a service UUID filter so
    // only V1connection LE devices show up.

    private ScanCallback m_leScanCallback = null;

    private void startLeScan()
    {
        if (m_bta == null || !m_bta.isEnabled() || m_leScanning)
            return;

        // need scan permission on Android 12+, location below that
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
        {
            requestBtPermissionsIfNeeded();
            return;
        }

        final BluetoothLeScanner scanner = m_bta.getBluetoothLeScanner();
        if (scanner == null)
            return;

        if (m_leScanCallback == null)
        {
            m_leScanCallback = new ScanCallback()
            {
                @Override
                public void onScanResult(int callbackType, ScanResult result)
                {
                    addLeDevice(result.getDevice());
                }
            };
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(V1connectionLE.V1_CONNECTION_LE_SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try
        {
            scanner.startScan(Collections.singletonList(filter), settings, m_leScanCallback);
        }
        catch (SecurityException e)
        {
            return;
        }

        m_leScanning = true;
        m_scanLeButton.setText(R.string.bt_scanning_le);

        m_handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                stopLeScan();
            }
        }, LE_SCAN_PERIOD_MS);
    }

    private void stopLeScan()
    {
        if (m_leScanning && m_bta != null && m_leScanCallback != null)
        {
            try
            {
                BluetoothLeScanner scanner = m_bta.getBluetoothLeScanner();
                if (scanner != null)
                    scanner.stopScan(m_leScanCallback);
            }
            catch (SecurityException e)
            {
                // nothing to do
            }
        }
        m_leScanning = false;
        if (m_scanLeButton != null)
            m_scanLeButton.setText(R.string.bt_scan_le);
    }

    private void addLeDevice(BluetoothDevice device)
    {
        if (m_devicesList == null || device == null)
            return;

        // Skip devices we already have in the list.
        for (BluetoothDevice known : m_devicesList)
        {
            if (known.getAddress().equals(device.getAddress()))
                return;
        }

        String name;
        try
        {
            name = device.getName();
        }
        catch (SecurityException e)
        {
            name = null;
        }
        if (name == null || name.isEmpty())
            name = device.getAddress();

        m_deviceNameList.add(name + " " + getString(R.string.bt_le_suffix));
        m_devicesList.add(device);
        m_connectionTypeList.add(ValentineESP.CONNECTION_LE);
        m_pairedAdapter.notifyDataSetChanged();
    }

    // show message

    private void mShowMessage(final String msg, final String _title)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(ListPairedBTActivity.this);
                builder.setTitle(_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.cancel();
                            }
                        })
                        .create()
                        .show();
            }
        });
    }
}
