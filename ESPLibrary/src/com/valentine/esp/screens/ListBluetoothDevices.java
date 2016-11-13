package com.valentine.esp.screens;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.valentine.esp.ValentineClient;
import com.valentine.esp.utilities.Utilities;

public class ListBluetoothDevices extends Activity {
	private int REQUEST_ENABLE_BT = 1;

	private BluetoothAdapter     m_bta;
	private Set<BluetoothDevice> m_pairedDevices;

	private ArrayList<String>          m_deviceNameList;
	private ArrayList<BluetoothDevice> m_devicesList;


	private ListView m_listView;

	ArrayAdapter<String> m_pairedAdapter;

	private BluetoothDevice m_device;

	boolean demoDevice;

	TextView m_searchingBar;

	private boolean m_isTimerRunning = true;
	private Handler m_handler;
	private int      numberOfDots     = 1;
	
	/**
	 * Handler that will update the text view at the top of the screen indicating the device discovery status.
	 */
	private Runnable mUpdateTitleTask = new Runnable() {
		public void run() {
			if (m_isTimerRunning) {
				StringBuilder sb = new StringBuilder();
				sb.append("Searching for devices");

				for (int i = 0; i < numberOfDots; i++) {
					sb.append(".");
				}

				numberOfDots++;
				if (numberOfDots == 5) {
					numberOfDots = 0;
				}
				m_searchingBar.setText(sb.toString());
				m_handler.postDelayed(mUpdateTitleTask, 2000);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LinearLayout layout = new LinearLayout(this);

		LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
		                                                              LinearLayout.LayoutParams.FILL_PARENT);

		layout.setOrientation(LinearLayout.VERTICAL);

		m_searchingBar = new TextView(this);
		m_searchingBar.setTextSize(Utilities.getPixelFromDp(18, getResources().getDisplayMetrics().density));
		m_searchingBar.setText("");

		LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
		                                                              LinearLayout.LayoutParams.WRAP_CONTENT);
		m_searchingBar.setLayoutParams(sbp);
		layout.addView(m_searchingBar);


		m_listView = new ListView(this);
		LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
		                                                              LinearLayout.LayoutParams.FILL_PARENT);
		m_listView.setLayoutParams(lp3);
		layout.addView(m_listView);

		setContentView(layout, rlp);

		m_listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				m_bta.cancelDiscovery();
				m_listView.setEnabled(false);
				if (position == 0) {
					demoDevice = true;
					Intent response = new Intent();
					response.putExtra("DemoDevice", true);
					setResult(Activity.RESULT_OK, response);
					m_listView.setEnabled(true);
					finish();
				} else {
					m_device = m_devicesList.get(position - 1);


					if (m_device.getBondState() == BluetoothDevice.BOND_NONE) {
						try {
							Method m = m_device.getClass().getMethod("createBond", (Class[]) null);
							m.invoke(m_device, (Object[]) null);
						} catch (SecurityException e) {
							ValentineClient.getInstance().reportError("Unable to connect to " + m_device.getName());
							m_listView.setEnabled(true);
							BluetoothDevice device = m_devicesList.get(position - 1);
							Intent response = new Intent();
							response.putExtra("DemoDevice", false);
							response.putExtra("SelectedBluetoothDevice", device);
							setResult(Activity.RESULT_OK, response);
							finish();
						} catch (IllegalArgumentException e) {
							ValentineClient.getInstance().reportError("Unable to connect to " + m_device.getName());
							m_listView.setEnabled(true);
							BluetoothDevice device = m_devicesList.get(position - 1);
							Intent response = new Intent();
							response.putExtra("DemoDevice", false);
							response.putExtra("SelectedBluetoothDevice", device);
							setResult(Activity.RESULT_OK, response);
							finish();
						} catch (Exception e) {
							ValentineClient.getInstance().reportError("Unable to connect to " + m_device.getName());
							m_listView.setEnabled(true);
							BluetoothDevice device = m_devicesList.get(position - 1);
							Intent response = new Intent();
							response.putExtra("DemoDevice", false);
							response.putExtra("SelectedBluetoothDevice", device);
							setResult(Activity.RESULT_OK, response);
							finish();
						}
					} else {
						m_listView.setEnabled(true);
						BluetoothDevice device = m_devicesList.get(position - 1);
						Intent response = new Intent();
						response.putExtra("DemoDevice", false);
						response.putExtra("SelectedBluetoothDevice", device);
						setResult(Activity.RESULT_OK, response);
						finish();
					}
				}
			}
		});


		m_handler = new Handler();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (setUpBlueTooth()) {

			if (m_bta.isDiscovering()) {
				m_bta.cancelDiscovery();
			}

			m_bta.startDiscovery();
			m_isTimerRunning = true;
			m_handler.postDelayed(mUpdateTitleTask, 2000);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		unregisterReceiver(m_receiver);
		unregisterReceiver(m_paringReceiver);
		if (m_bta != null) {
			m_bta.cancelDiscovery();
		}
		m_isTimerRunning = false;
	}


	@Override
	public void onPause() {
		super.onPause();
		if (m_bta != null) {
			m_bta.cancelDiscovery();
		}
		m_isTimerRunning = false;
	}
	
	/**
	 * Helper method that will handle enabling bluetooth on the phone and starting 
	 * device discover, and setting up the 'devices' listview. As well as registering the Discovery broadcast receivers.
	 * 
	 * @return	Always returns true.
	 */
	private boolean setUpBlueTooth() {
		m_bta = BluetoothAdapter.getDefaultAdapter();

		if (m_bta == null) {
			return false;
		}

		if (!m_bta.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		m_deviceNameList = new ArrayList<String>();

		m_deviceNameList.add("Enter Demo Mode");

		m_devicesList = new ArrayList<BluetoothDevice>();

		m_pairedDevices = m_bta.getBondedDevices();
		if (m_pairedDevices.size() > 0) {
			for (BluetoothDevice device : m_pairedDevices) {
				String name;
				if (device.getName() == null) {
					name = device.getAddress();
				} else {
					name = device.getName();
				}
				if (name == null) {
					continue;
				}
				m_deviceNameList.add(name);
				m_devicesList.add(device);
			}
		}


		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(m_receiver, filter);

		IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		registerReceiver(m_paringReceiver, filter2);

		//cancel any prior BT device discovery
		if (m_bta.isDiscovering()) {
			m_bta.cancelDiscovery();
		}


		m_searchingBar.setText("Searching for devices");

		ArrayAdapter<String> m_pairedAdapter = new ArrayAdapter<String>(this,
		                                                                android.R.layout.simple_list_item_1,
		                                                                android.R.id.text1,
		                                                                m_deviceNameList);

		m_listView.setAdapter(m_pairedAdapter);

		return true;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			setUpBlueTooth();
		} else if (resultCode == Activity.RESULT_CANCELED) {
			ValentineClient.getInstance().reportError("Turning on bluetooth canceled");
		}
	}


	/**
	 *  BroadcastReceiver that handles added the 'found' bluetooth devices to the listview.
	 */
	private BroadcastReceiver m_receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			// When discovery finds a device
			if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				m_bta.startDiscovery();
			} else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// Add the name and address to an array adapter to show in a ListView
				String name;
				if ((device.getName() == null) || (device.getName() == "")) {
					name = device.getAddress();
				} else {
					name = device.getName();
				}

				if (name == null) {
					return;
				}

				boolean found = false;

				for (int i = 0; i < m_deviceNameList.size(); i++) {
					if (name.equals(m_deviceNameList.get(i))) {
						found = true;
						break;
					}
				}

				if (!found) {
					for (int i = 0; i < m_devicesList.size(); i++) {
						if (name.equals(m_devicesList.get(i))) {
							found = true;
							break;
						}
					}
				}


				if (!found) {
					m_deviceNameList.add(name);
					m_devicesList.add(device);

					ListBluetoothDevices.this.runOnUiThread(new Runnable() {
						public void run() {
							BaseAdapter adapter = (BaseAdapter) m_listView.getAdapter();
							adapter.notifyDataSetChanged();
							m_listView.invalidateViews();
						}
					});
				}
			}
		}
	};

	/**
	 * BroadcastReceiver that handles paring the selected bluetooth device and sending the devices to the Splash activity to be stored
	 * in the SharedPreferences.
	 * handles notifying the user if the paring has failed.
	 */
	private BroadcastReceiver m_paringReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
				int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
				int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

				if (prevBondState == BluetoothDevice.BOND_BONDING) {
					// check for both BONDED and NONE here because in some error cases the bonding fails and we need to fail gracefully.
					if (bondState == BluetoothDevice.BOND_BONDED) {
						m_listView.setEnabled(true);
						Intent response = new Intent();
						response.putExtra("DemoDevice", false);
						response.putExtra("SelectedBluetoothDevice", m_device);
						ListBluetoothDevices.this.setResult(Activity.RESULT_OK, response);
						finish();
					} else if (bondState == BluetoothDevice.BOND_NONE) {
						m_listView.setEnabled(true);
						AlertDialog.Builder builder = new AlertDialog.Builder(ListBluetoothDevices.this);
						builder.setTitle("Unable to connect")
						       .setMessage(
								       "Please check the notification area for a pairing request, enter 1234 for the pin and please try again")
						       .setCancelable(false)
						       .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							       public void onClick(DialogInterface dialog, int id) {
								       dialog.cancel();
							       }
						       })
						       .show();
					}
				}
			}
		}
	};

}