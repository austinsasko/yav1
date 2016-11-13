/**
 * Created by franck on 6/29/13.
 */

package com.franckyl.yav1;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class ListPairedBTActivity extends ListActivity
{
    private BluetoothAdapter m_bta;
    private Set<BluetoothDevice> m_pairedDevices;

    private ArrayList<String> m_deviceNameList;
    private ArrayList<BluetoothDevice> m_devicesList;
    private ListView m_listview;

    TextView m_searchingBar;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // we show our view
        setContentView(R.layout.listpairbt_activity);

        // get the bluethooh paired adapter (if false), we must have an error set
        setUpBlueTooth(true);
    }

    // we click on an adpater

    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        // retrieve the device at the given position
        BluetoothDevice sDevice = m_devicesList.get(position);
        // we respond
        Intent response = new Intent();
        response.putExtra("SelectedBluetoothDevice", sDevice);
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
        super.onDestroy();
    }


    @Override
    public void onPause()
    {
        YaV1.superPause();
        super.onPause();
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

        m_deviceNameList = new ArrayList<String>();
        m_devicesList    = new ArrayList<BluetoothDevice>();
        m_pairedDevices  = m_bta.getBondedDevices();

        // hope we have devices

        if (m_pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : m_pairedDevices)
            {
                String name;
                if (device.getName() == null)
                    name = device.getAddress();
                else
                    name = device.getName();
                if (name == null)
                    continue;
                m_deviceNameList.add(name);
                m_devicesList.add(device);
            }
        }

        ArrayAdapter<String> m_pairedAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, m_deviceNameList);

        // set the list
        setListAdapter(m_pairedAdapter);
        // always warn user that the list show only the paired devices
        if(warn)
            mShowMessage(getString(R.string.device_must_be_paired), getString(R.string.warning));
        return true;
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
