package com.franckyl.yav1;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.franckyl.yav1.alert_histo.AlertHistoryActivity;
import com.franckyl.yav1.lockout.LockoutActivity;
import com.franckyl.yav1.lockout.LockoutData;
import com.franckyl.yav1.lockout.LockoutOverride;
import com.franckyl.yav1.lockout.LockoutParam;
import com.franckyl.yav1.utils.MiscUtils;
import com.franckyl.yav1.utils.YaV1DbgLogger;
import com.franckyl.yav1lib.YaV1Alert;
import com.valentine.esp.ValentineClient;

import java.io.File;
import java.io.IOException;
import java.util.Set;


public class YaV1Activity extends Activity implements ActionBar.OnNavigationListener
{
    private final int GET_DEVICE     = 10;
    private final int GET_SETTINGS   = 11;
    private final int GET_SWEEP      = 12;
    private final int GET_V1SETTING  = 100;
    public  static final int GET_VOICEALERT = 200;

    private boolean       mIsBtEnabled       = true;

    // button for switching mode

    private boolean       mRestart        = false;
    private boolean       mStop           = false;
    private boolean       mFromDark       = false;
    private static boolean mHasBeenStarted = false;

    private static boolean mInWait        = false;

    private static WaitDialog    mDialog  = null;
    public  static YaV1Overlay   sOverlay = null;

    // we need this
    private static NotificationCompat.Builder sBuilder = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        String action = getIntent().getAction();
        // setTheme(R.style.Theme_Sherlock);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.yav1_activity);
        boolean autoStart = false;

        // be sure to have preferences
        PreferenceManager.getDefaultSharedPreferences(this);

        if(YaV1.sLooper == null || !YaV1.sLooper.isRunning())
            YaV1.sLooper = new YaV1Looper();

        // init the Alert processor parameters parameters
        if(!AlertProcessorParam.mInitDone)
            AlertProcessorParam.init(YaV1.sPrefs);

        if(!SoundParam.mInitDone)
            SoundParam.init(YaV1.sPrefs);

        if(!LockoutParam.mInitDone)
            LockoutParam.init(YaV1.sPrefs);

        if(YaV1.mV1Client == null)
        {
            int v1Timeout   = Integer.valueOf(YaV1.sPrefs.getString("v1_timeout", "6000"));
            // was in milliseconds
            if(v1Timeout >= 1000)
                v1Timeout /= 1000;

            YaV1.mV1Client = new ValentineClient(YaV1.sContext, v1Timeout);

            // enable the BT work around
            YaV1.mV1Client.enableBtWorkAround(YaV1.sPrefs.getBoolean("bt_workaround", false));

            // set the looper Handler
            //YaV1.mV1Client.setHandler(YaV1.sLooper.getHandler());

            // check for testing mode
            File f = new File(YaV1.sStorageDir + "/testing_mode");
            YaV1.sInTestingMode = f.exists();
            YaV1.sExpert        = f.exists();

            // check for font increase on v1 alert
            int fr = MiscUtils.checkFontIncrease();

            if(fr > 0 && fr != 100)
            {
                // store at application
                YaV1.sFontFrequencyRatio = ( (float) fr / 100f);
                Log.d("Valentine", "Font ratio is set to " + YaV1.sFontFrequencyRatio);
            }
        }

        // store the preferences
        YaV1.sPrefs = YaV1PreferenceActivity.getYaV1Preference();
        YaV1.refreshSettings();

        // check the bt workaround
        //YaV1.mV1Client.setAttempt2(YaV1.sPrefs.getBoolean("bt_workaround", false));

        // check logging
        checkLogging();

        if(!YaV1.sDemoEnable)
            checkDemoAvailable();

        // check the img in alert
        if(!YaV1Alert.sTheme.equals(YaV1.sPrefs.getString("alert_theme", "")))
            YaV1Alert.loadDirImg(this, YaV1.sPrefs.getString("alert_theme", ""));

        // check the autoLockout
        if(YaV1.sAutoLockout == null)
        {
            if(YaV1.sPrefs.getBoolean("enable_lockout", false))
            {
                YaV1.sAutoLockout      = new LockoutData(false);
                YaV1.sLockoutOverride  = new LockoutOverride();
            }
        }

        // custom sweeps
        if(YaV1.sSweep == null)
        {
            YaV1.sSweep = new YaV1SweepSets();
            YaV1.sSweep.read();
        }

        // V1 settings
        if(YaV1.sV1Settings == null)
        {
            YaV1.sV1Settings = new YaV1SettingSet();
            YaV1.sV1Settings.read();
        }

        // dbg logger
        if(YaV1.sPrefs.getBoolean("dbg_log", false))
        {
            if(YaV1.sDbgLog == null)
            {
                YaV1.sDbgLog = new YaV1DbgLogger();
                YaV1.sDbgLog.startLog();
            }
        }
        else
        {
            if(YaV1.sDbgLog != null)
            {
                YaV1.sDbgLog.stopLogger();
                YaV1.sDbgLog = null;
            }
        }

        // set the speed preferences
        YaV1GpsService.refreshSettings();

        // refresh settings of real savvy
        YaV1RealSavvy.refreshSettings();

        // other settings to refresh
        YaV1.refreshSettings();

        // set the Image view
        ImageButton back;
        back = (ImageButton) findViewById(R.id.yav1_alert);
        back.setOnClickListener(mListener);

        if(sBuilder == null)
        {
            sBuilder = new NotificationCompat.Builder(this);

            Intent i = new Intent(this, YaV1Activity.class)
                      .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
            // set Title / Icon / Intent
            sBuilder.setContentTitle(getText(R.string.app_name));
            sBuilder.setContentText(getText( YaV1AlertService.isActive()? R.string.alert_run_v1_active : R.string.alert_run_v1_inactive));
            sBuilder.setSmallIcon( (YaV1AlertService.isActive()? R.drawable.ic_notify :R.drawable.ic_notify_off));
            sBuilder.setContentIntent(pi);
            Notification note = sBuilder.build();
            NotificationManager nm = (NotificationManager) getSystemService(YaV1.sContext.NOTIFICATION_SERVICE);
            nm.notify(YaV1.APP_ID, note);
        }

        // static initialization

        YaV1.appEnter();

        // retrieve the default device

        getDefaultDevice(false);

        if(mIsBtEnabled)
        {
            // we will launcn the BlueTooth Activity if we do not have device name
            if(YaV1.sDeviceName == "")
            {
                //Log.d("Valentine", "No Device Starting Bleutooth Activity");
                Intent newIntent = new Intent(this, ListPairedBTActivity.class);
                startActivityForResult(newIntent, GET_DEVICE);
            }
            else
            {
                // auto start
                autoStart = YaV1.sPrefs.getBoolean("auto_start", false);
            }
        }
        else
        {
            // show an error message to turn on bluethooth and select "device" option
            AlertDialog.Builder builder = new AlertDialog.Builder(YaV1Activity.this);
            builder.setTitle(R.string.error)
                    .setMessage(R.string.bluetooth_not_running)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
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

        // request start of service
        YaV1.startAlertService(true);

        if(sOverlay == null)
            sOverlay = new YaV1Overlay(this);

        if(!mHasBeenStarted && autoStart)
            startAlert(false);

        mHasBeenStarted = true;
    }

    // get the notification builder

    public static NotificationCompat.Builder getNotificationBuilder()
    {
        if(sBuilder == null)
            YaV1.DbgLog("Request for notification builder which is null");

        return sBuilder;
    }
    // when stopped

    protected void onNewIntent(Intent intent)
    {
        //Log.d("Valentine", "New intent received");
        super.onNewIntent(intent);
        if(intent.getBooleanExtra("stop", false))
        {
            YaV1.v1Wait = null;
            mStop = true;
            return;
        }

        if(intent.getBooleanExtra("restart", false))
        {
            startAlert(false);
        }

        if(intent.getBooleanExtra("fromdark", false))
        {
            mFromDark = true;
            startAlert(false);
        }

        if(intent.getBooleanExtra("retry", false))
        {
        }
    }

    // Create our ActionBar
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.ya_v1, menu);
        return true;
    }

    // Do the actions in the menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent newIntent = null;
        switch (item.getItemId())
        {
            case R.id.yav1_demo:
                // we can't start demo when V1 is connected
                if(YaV1.mV1Client.isConnected())
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.warning);
                    builder.setMessage(R.string.alert_no_demo);
                    builder.setPositiveButton(R.string.ok, null).show();
                }
                else
                    startAlert(true);
                return true;
            case R.id.yav1_lockout:
                if(!YaV1.sPrefs.getBoolean("enable_lockout", false) || YaV1.sAutoLockout == null)
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.warning);
                    builder.setMessage(R.string.lockout_disabled);
                    builder.setPositiveButton(R.string.ok, null).show();
                }
                else
                {
                    newIntent = new Intent(this, LockoutActivity.class);
                    startActivity(newIntent);
                }
                return true;
            case R.id.yav1_alert_map:
                newIntent = new Intent(this, AlertHistoryActivity.class);
                startActivity(newIntent);
                return true;
            case R.id.yav1_device:
                newIntent = new Intent(this, ListPairedBTActivity.class);
                startActivityForResult(newIntent, GET_DEVICE);
                return true;
            case R.id.yav1_settings:
                newIntent = new Intent(this, YaV1PreferenceActivity.class);
                startActivityForResult(newIntent, GET_SETTINGS);
                return true;
            case R.id.yav1_sweep:
                newIntent = new Intent(this, YaV1SweepSetActivity.class);
                startActivityForResult(newIntent, GET_SWEEP);
                return true;
            case R.id.yav1_v1setting:
                newIntent = new Intent(this, YaV1SettingSetActivity.class);
                startActivityForResult(newIntent, GET_V1SETTING);
                return true;
            case R.id.yav1_savvy:
                if(!YaV1RealSavvy.enabled || YaV1RealSavvy.sSavvyStatus == null)
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.wired_savvy);
                    builder.setMessage(R.string.no_wired_savvy);
                    builder.setPositiveButton(R.string.ok, null).show();
                }
                else
                {
                    YaV1RealSavvyDialog dialog = new YaV1RealSavvyDialog(this);
                    dialog.show();
                }
                return true;
            case R.id.yav1_about:
                FragmentManager fm = getFragmentManager();
                AboutDialogFragment ab = new AboutDialogFragment();
                ab.show(fm, "about");
                return true;
            case R.id.yav1_quit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId)
    {
        // Log.d("Valentine", "Navigation not handled" + itemId);
        return false;
    }

    // check if we need to start the Logging

    private void checkLogging()
    {
        // start the YaV1Logger (we have to check about the settings)
        if(YaV1.sPrefs.getBoolean("log", false) && (YaV1.sLog == null || !YaV1.sLog.isActive()))
        {
            YaV1.sLog = new YaV1Logger();
            YaV1.sLog.startLogging();
        }
    }

    // private function that retrieve the last bluetooth device

    private boolean getDefaultDevice(boolean onCheck)
    {
        Context context = this;
        SharedPreferences m_preferences = context.getSharedPreferences("com.valentine.esp.savedData", context.MODE_PRIVATE);

        // first we check if we have bleutooth and it's on
        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();

        if(bta == null || !bta.isEnabled())
        {
            mIsBtEnabled = false;
            // change the message for adapter
            return false;
        }

        if(onCheck)
            return true;

        String bluetoothAddress = m_preferences.getString("com.valentine.esp.LastBlueToothConnectedDevice", "");

        if(bluetoothAddress != "")
        {
            Set<BluetoothDevice> pairedDevices;
            pairedDevices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
            if (pairedDevices.size() > 0)
            {
                for (BluetoothDevice bd : pairedDevices)
                {
                    String test = bd.getAddress();
                    if (test.equals(bluetoothAddress))
                    {
                        YaV1.sDevice     = bd;
                        YaV1.sDeviceName = bd.getName();
                        // if no name, get the Address
                        if(YaV1.sDeviceName == "")
                            YaV1.sDeviceName = bd.getAddress();
                        // we always get the device address
                        YaV1.sDeviceAddr = bd.getAddress();
                        return true;
                    }
                }
            }
        }
        else
            YaV1.sDeviceName = "";

        return false;
    }

    // check if Demo is enabled

    private void checkDemoAvailable()
    {
        //
        // check if we have Bluetooth GPS application
        //
        File sDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + YaV1.PACKAGE_NAME);
        YaV1.sHasBluetoothGpsApp = false;
        if(sDir.isDirectory() || sDir.mkdirs())
        {
            // we will open file and use Gson
            String fName  =  "blue_gps_app";
            // check if file exists
            File  f = new File(sDir, fName);
            if(f.exists())
                // check if really installed
                YaV1.sHasBluetoothGpsApp = isPackageExists("org.broeuschmeul.android.gps.bluetooth.provider");
        }

        YaV1.sDemoEnable = false;
        try
        {
            String [] list = getResources().getAssets().list("demo");
            YaV1.sDemoEnable = (list.length > 0 ? true : false);
        }
        catch (IOException e)
        {
        }
    }

    public boolean isPackageExists(String targetPackage)
    {
        PackageManager pm=getPackageManager();
        try
        {
            PackageInfo info=pm.getPackageInfo(targetPackage,PackageManager.GET_META_DATA);
        }
        catch(PackageManager.NameNotFoundException e)
        {
            return false;
        }
        return true;
    }

    public void onStop()
    {
        super.onStop();
        //Log.d("Valentine", "Stopping " + (isFinishing() ? " Finishing" : " Not finishing"));
    }

    public void onDestroy()
    {
        // Log.d("Valentine", "On Destroy finishing " + isFinishing());
        super.onDestroy();

        // kill the app to make sure everything is stopped (if enabled)
        if(isFinishing())
        {
            // stop the looper
            if(YaV1.sLooper != null)
            {
                YaV1.sLooper.stopLoop();
                YaV1.sLooper = null;
            }

            // clear the cache
            // YaV1.trimCache(this);

            // clear the app Context
            // YaV1.trimCache(YaV1.sContext);

            //if(YaV1.sPrefs.getBoolean("force_stop", false))
            //    android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    // resume means finish the YaV1_screen we save the nre frequency if needed

    public void onResume()
    {
        super.onResume();
        // YaV1.superResume();

        //Log.d("Valentine", " ** Resuming Main restart " + mRestart + " Stop App " + mStop + " in wait " + mInWait + " Retry " + mRetry + " From Dark " + mFromDark);

        if(mStop)
        {
            mStop = false;
            Toast.makeText(getBaseContext(), R.string.main_auto_stop, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if(mRestart)
        {
            mRestart = false;
            startAlert(false);
        }
        else
        {
            if(mInWait && mDialog == null)
            {
                showWaitDialog();
            }
            // V1 info
            sysInfo();
        }
    }

    @Override

    public void onPostResume()
    {
        super.onPostResume();
        YaV1.superResume();
    }

    // on pause

    public void onPause()
    {
        YaV1.superPause();
        super.onPause();
        // dismiss if in wait
        if(mInWait && mDialog != null)
        {
            mDialog.dismiss();
            mDialog = null;
        }


        if(isFinishing())
        {
            cleanUp();
        }
    }

    // cleaning when finishing

    private void cleanUp()
    {
        Log.d("Valentine", "YaV1Activity cleanUp");
        // stop alert service
        YaV1.startAlertService(false);

        // stop GPS
        YaV1.startGpsService(false);

        YaV1.mInWait.set(false);
        YaV1.v1Wait = null;

        // close the Lockout DB

        if(YaV1.sAutoLockout != null)
        {
            YaV1.sAutoLockout.endLockout();
            YaV1.sLockoutOverride.cleanup(true);
            YaV1.sLockoutOverride = null;
        }

            // stop the logger
        if(YaV1.sLog != null)
            YaV1.sLog.stopLogging();

        // destroy V1setting

        if(YaV1.sV1Settings != null)
            YaV1.sV1Settings = null;

        // destroy sWeeps

        if(YaV1.sSweep != null)
            YaV1.sSweep = null;

        // reset to get them when restart
        YaV1.sModeData = null;
        // YaV1.sV1Bogey  = 0;

        // remove the overlay
        if(sOverlay != null)
        {
            sOverlay.removeOverlay();
            sOverlay = null;
        }

        // reset activity counter
        YaV1.sCurrentActivity = 0;

        // stop debug log
        if(YaV1.sDbgLog != null)
        {
            YaV1.sDbgLog.stopLogger();
            YaV1.sDbgLog = null;
        }

        mInWait         = false;
        mFromDark       = false;
        mHasBeenStarted = false;

        YaV1.sV1Version = "";
        YaV1.sV1Serial  = "";
        YaV1.sSavvyVersion   = "";

        // reset to null
        YaV1.sAutoLockout = null;
        YaV1.appExit();
        Log.d("Valentine", "YaV1Activity appExit called");
    }

    // refresh the system settings

    private void sysInfo()
    {
        if(YaV1.sModeData != null)
        {
            findViewById(R.id.v1_info).setVisibility(View.VISIBLE);

            TextView  txt;
            TextView  txt1;
            ImageView sLetter;

            txt = (TextView) findViewById(R.id.sys_country);
            txt.setText((YaV1.sModeData.getEuroMode() ? R.string.main_euro_mode : R.string.main_usa_mode));

            sLetter = (ImageView) findViewById(R.id.sys_letter);
            YaV1Bogey sBogey = new YaV1Bogey();

            // in euro mode, we can't have advance logic mode
            sBogey.setBogey(YaV1.getModeByte());
            sLetter.setImageResource(sBogey.getImageNotDot());

            // we add
            txt  = (TextView) findViewById(R.id.sys_sweep);
            txt1 = (TextView) findViewById(R.id.curr_sweep);

            if(!YaV1.sModeData.getCustomSweeps())
            {
                txt.setVisibility(View.INVISIBLE);
                txt1.setVisibility(View.INVISIBLE);
            }
            else
            {
                txt.setVisibility(View.VISIBLE);
                txt1.setVisibility(View.VISIBLE);
                txt1.setText(getText(R.string.main_cs_prefix)  + YaV1.sSweep.getCurrentName());
            }

            txt = (TextView) findViewById(R.id.curr_setting);
            txt.setVisibility(View.VISIBLE);
            txt.setText(getText(R.string.main_v1_setting_prefix) + YaV1.sV1Settings.getCurrentName());
            txt = (TextView) findViewById(R.id.sys_version);
            txt.setText(YaV1.sV1Version + " (" + (YaV1.sLegacy ? getString(R.string.main_legacy_mode) : getString(R.string.main_esp_mode)) + ")");

            txt.setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.v1_info).setVisibility(View.INVISIBLE);
            findViewById(R.id.sys_version).setVisibility(View.INVISIBLE);
            findViewById(R.id.curr_sweep).setVisibility(View.INVISIBLE);
            findViewById(R.id.curr_setting).setVisibility(View.INVISIBLE);
        }
    }

    // activity result (from Bluethooh selection)

    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode)
        {
            case GET_DEVICE:
            {
                if(resultCode == Activity.RESULT_OK )
                {
                    // The user selected a Bluetooth device.
                    YaV1.sDevice     = data.getParcelableExtra("SelectedBluetoothDevice");
                    YaV1.sDeviceName = YaV1.sDevice.getName();
                    if(YaV1.sDeviceName == "")
                        YaV1.sDeviceName = YaV1.sDevice.getAddress();
                    // we always get the device address
                    YaV1.sDeviceAddr = YaV1.sDevice.getAddress();
                    //
                    // We start the Arlet Service
                    // We restart the sV1Client
                    if(YaV1.isClientStarted)
                        YaV1.mV1Client.Shutdown();
                    YaV1.mV1Client.StartUp(YaV1.sDevice);
                    YaV1.isClientStarted = true;
                    //Log.d("Valentine", "Activity result OK We have a Device " + BMV.sDeviceName);
                    startAlert(false);
                }
                break;
            }
            case GET_SETTINGS:
            {
                // settings might have change
                YaV1.sPrefs = YaV1PreferenceActivity.getYaV1Preference();
                LockoutParam.init(YaV1.sPrefs);
                AlertProcessorParam.init(YaV1.sPrefs);
                SoundParam.init(YaV1.sPrefs);
                YaV1.refreshSettings();
                YaV1AlertService.refreshSettings(false);
                // check about overlay
                if(YaV1.sPrefs.getBoolean("overlay", false))
                {
                    if(sOverlay == null)
                        sOverlay = new YaV1Overlay(this);
                    else
                    {
                        // need to check for change
                        if(sOverlay.recreate())
                        {
                            sOverlay.removeOverlay();
                            sOverlay = null;
                            sOverlay = new YaV1Overlay(this);
                        }
                    }
                }

                // refresh the Gps preferences
                YaV1GpsService.refreshSettings();
                // refresh settings
                YaV1RealSavvy.refreshSettings();
                // Logging might have change
                checkLogging();
                break;
            }
            case GET_SWEEP:
            {
                // we will request the sweep again
                // Intent sIntent = new Intent("YaV1Push");
                // sIntent.putExtra("Suspend", YaV1AlertService.V1_GETMODE_DATA);
                // LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
                // check if we have to restart
                if(data != null && data.getBooleanExtra("restart", false))
                    startAlert(false);
                // might save the sweep ?
                YaV1.sSweep.save(false);
                break;
            }
            case GET_V1SETTING:
            {
                YaV1.sV1Settings.save(false);
                // if ok, we save, we startAlert and request settings again
                if(resultCode == Activity.RESULT_OK )
                {
                    // this will request the current settings
                    // Intent sIntent = new Intent("YaV1Push");
                    // sIntent.putExtra("Suspend", YaV1AlertService.V1_GETMODE_DATA);
                    // LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
                    // restart ?
                    if(data != null && data.getBooleanExtra("restart", false))
                        startAlert(false);
                }

                break;
            }
        }
    }


    // Start the Service on the Given Device
    private void startAlert(boolean demo)
    {
        Log.d("Valentine", "In start alert service");
        // we launch our service here (if not there)
        YaV1.startAlertService(true);

        // if we enabled Gps, we need to start here maybe

        if(YaV1.sPrefs.getBoolean("use_gps", false))
        {
            // enable Gps here
            YaV1CurrentPosition.reset(true);
            // if not started we start
            YaV1.startGpsService(true);
        }
        else
        {
            YaV1.startGpsService(false);
            YaV1CurrentPosition.reset(false);
        }

        // check if we have a connection with V1

        if(!demo && !YaV1AlertService.isClientRunning())
            waitForV1();
        else
        {
            Intent dIntent = new Intent(this, YaV1ScreenActivity.class);
            // make sure only one
            dIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            // we can fire it
            if(demo)
                dIntent.putExtra("demoMode", true);
            // from Dark
            if(mFromDark)
                dIntent.putExtra("fromdark", true);
            mFromDark = false;
            startActivity(dIntent);
        }
    }

    // user click on button

    final OnClickListener mListener = new OnClickListener()
    {
        public void onClick(final View v)
        {
            int newMode = 0;

            switch(v.getId())
            {
                case R.id.yav1_alert:
                    startAlert(false);
                    break;
            }
        }
    };

    // when we have to connect to V1

    public class WaitV1Thread extends Thread
    {
        public boolean mIsCanceled = false;
        public boolean mAvailable  = false;
        public boolean mStopApp    = false;
        public boolean mPostDone   = false;

        private int loop           = 0;
        int    auto_stop     = Integer.valueOf(YaV1.sPrefs.getString("auto_stop", "0"));
        long   tstart        = System.currentTimeMillis();

        public WaitV1Thread()
        {
            mIsCanceled = false;
            mAvailable  = false;
            mStopApp    = false;
            mPostDone   = false;
            loop        = 0;
            tstart      = System.currentTimeMillis();
            //Log.d("Valentine", "Wait constructor start is " + tstart);
            auto_stop   = Integer.valueOf(YaV1.sPrefs.getString("auto_stop", "0"));
            setPriority(MAX_PRIORITY);
            // un caught exception
            setUncaughtExceptionHandler(new UncaughtExceptionHandler()
            {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable)
                {
                    // Log.d("Valentine", "Uncaught exception " + throwable.toString());
                    if(!mPostDone)
                        afterRun();
                }
            });
        }

        public void cancelIt()
        {
            mIsCanceled = true;
            //Log.d("Valentine", "Wait has been canceled");
        }

        public void run()
        {
            boolean stop    = false;
            int     nb      = 0;
            int     tout    = 500;
            long    elapsed = 0;

            tstart          = SystemClock.elapsedRealtime();

            while(!mIsCanceled)
            {
                loop++;
                if(YaV1.mV1Client == null || (auto_stop > 0 && SystemClock.elapsedRealtime() > tstart + (auto_stop * 1000)))
                {
                    if(YaV1.mV1Client != null)
                    {
                        mStopApp = true;
                    }
                    break;
                }

                // check for Running state first
                if(!YaV1.isClientStarted || !YaV1.mV1Client.isConnected())
                {
                    boolean stCall = false;
                    stCall = YaV1.mV1Client.StartUp(YaV1.sDevice);
                    Log.d("Valentine", "Client startup " + stCall);
                    YaV1.isClientStarted = true;
                }
                else
                {
                    // cancel can be long
                    if(!mIsCanceled)
                    {
                        if(nb == 0)
                        {
                            //if(YaV1.mV1Client != null && YaV1.mV1Client.isRunning())
                            if(YaV1.mV1Client != null && YaV1.mV1Client.isConnected())
                            {
                                Intent sIntent = new Intent("YaV1Push");
                                sIntent.putExtra("Suspend", YaV1AlertService.V1_REBIND);
                                LocalBroadcastManager.getInstance(YaV1.sContext).sendBroadcast(sIntent);
                            }
                        }
                        else
                        {
                            stop = YaV1AlertService.isActive();
                            tout = 500;
                        }
                        nb++;
                    }
                }

                if(mIsCanceled)
                    break;

                if(stop)
                {
                    mAvailable = true;
                    break;
                }

                // wait the desire timeout
                SystemClock.sleep(tout);
            }

            if(mIsCanceled)
                mAvailable = false;
            // after run
            afterRun();
        }

        // when we have the results

        private void afterRun()
        {
            mPostDone = true;
            // dialog can be null when detached in pause
            if(mDialog != null)
                mDialog.dismiss();

            // if stop or available we restart
            if(mStopApp || mAvailable)
            {
                Intent dIntent = new Intent(YaV1.sContext, YaV1Activity.class);
                if(mStopApp)
                    dIntent.putExtra("stop", true);
                else
                    dIntent.putExtra("restart", true);

                YaV1.mInWait.set(false);
                mInWait = false;
                dIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                YaV1.sContext.startActivity(dIntent);
            }

            YaV1.mInWait.set(false);
            mInWait = false;
            // reset to null
            YaV1.v1Wait = null;
            mDialog = null;
        }
    }

    // wait for V1

    private void waitForV1()
    {
        mInWait = true;
        showWaitDialog();
        YaV1.v1Wait = new WaitV1Thread();
        YaV1.v1Wait.start();
    }

    private static class WaitDialog extends AlertDialog
    {
        public WaitDialog(Context context)
        {
            super(context);

            //setTitle("Profile");
            setTitle(R.string.search_valentine);
            setMessage(context.getString(R.string.waiting_for)  + "  " + (YaV1.sDevice != null ? YaV1.sDevice.getName() : "???"));
            setCanceledOnTouchOutside(false);
            setCancelable(false);
            Button cancel = new Button(getContext());
            setView(cancel);
            cancel.setText(R.string.cancel);
            cancel.setOnClickListener(new View.OnClickListener()
            {

                public void onClick(View v)
                {
                    // I want the dialog to close at this point
                    setMessage("Cancelling bluetooth search ..");
                    WaitV1Thread t = (WaitV1Thread) YaV1.v1Wait;
                    if(t == null)
                        dismiss();
                    else
                        t.cancelIt();
                }
            });
        }
    }

    // create the dialog for the V1Wait

    public void showWaitDialog()
    {
        mDialog = new WaitDialog(this);
        if(mDialog != null)
            mDialog.show();
    }
}
