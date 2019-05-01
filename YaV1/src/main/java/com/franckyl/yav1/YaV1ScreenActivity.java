package com.franckyl.yav1;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.v4.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.franckyl.yav1.events.InfoEvent;
import com.franckyl.yav1.lockout.LockoutData;
import com.squareup.otto.Subscribe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;

import static android.R.color.black;

/**
 * Created by franck on 6/29/13.
 */

public class YaV1ScreenActivity extends FragmentActivity
{
    // the view type
    public static int VIEWV1     = 0;
    public static int VIEWALERT  = 1;
    public static int VIEWTOOL   = 2;

    public static String sIntentNames[] = {"V1Frequ", "V1Alert"};
    public static boolean sInTools      = false;
    public static int     sToolTabIndex = 2;

    // YaV1Service after bind call
    private YaV1AlertService mService;
    // keep track of the bounding
    private boolean   mBound = false;
    // YaV1GpsService if needed
    private YaV1GpsService.gpsBinder  mGpsService;
    // keep track of bindings
    private boolean   mGpsBound = false;
    // Our menu
    private Menu      mMenu;
    // handler
    private final Handler mHandler    = new Handler();

    // the lockout drift and undo
    private String mFragmentTag[]     = {"", "", "", ""};

    // the layout for popup
    RelativeLayout mV1Name    = null;

    // receiver handler
    private BroadcastReceiver mDisplayReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if(action.equals("V1Display"))
            {
                String s = intent.getStringExtra("Message");
                if(s != null && !s.isEmpty())
                    Toast.makeText(getBaseContext(), s, Toast.LENGTH_SHORT).show();

                adjustIcons();
            }
        }
    };

    // Demo mode
    public boolean  mDemo       = false;

    // From Dark mode

    public boolean  mFromDark   = false;

    // context
    Context         mContext;

    // Action bar stuff

    private ActionBar           mActionBar;
    private ViewPager           mPager;
    private ActionBar.Tab       tab;
    private YaV1ScreenAdapter   mViewPagerAdapter;
    public  static int          mLastView = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // request the action bar
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        // started in demo Mode
        mDemo = this.getIntent().getBooleanExtra("demoMode", false);

        // from Dark mode ?
        mFromDark = this.getIntent().getBooleanExtra("fromdark", false);

        // first we check that a device exists
        if(!mDemo)
            checkDeviceExists();

        setContentView( (YaV1CurrentPosition.enabled ? R.layout.yav1_screen_activity_gps : R.layout.yav1_screen_activity));

        if(YaV1.sPrefs.getBoolean("full_screen", false))
            this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mV1Name    = (RelativeLayout) findViewById(R.id.v1_name);

        // set the Action Bar
        // set the ActionBar background to black instead of default to transparent
        mActionBar = getActionBar();
        mActionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#000000")));
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Allow up activities
        mActionBar.setDisplayHomeAsUpEnabled(true);

        // Do not let the phone go to sleep when running this app
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Locate ViewPager in activity_main.xml
        mPager = (ViewPager) findViewById(R.id.pager);

        // Activate Fragment Manager
        FragmentManager fm = getSupportFragmentManager();

        // Capture ViewPager page swipes
        ViewPager.SimpleOnPageChangeListener ViewPagerListener = new ViewPager.SimpleOnPageChangeListener()
        {
            @Override
            public void onPageSelected(int position)
            {
                super.onPageSelected(position);
                mLastView = position;

                // according to position, we set the View type in service
                sInTools = mViewPagerAdapter.isTool(mLastView);
                if(mBound)
                    mService.setCurrentView(mLastView);

                if(sInTools)
                    mV1Name.setVisibility(View.GONE);
                else
                    mHandler.post(mShowSetting);

                mActionBar.setSelectedNavigationItem(mLastView);
            }
        };

        mPager.setOnPageChangeListener(ViewPagerListener);

        mViewPagerAdapter = new YaV1ScreenAdapter(fm);
        // Set the View Pager Adapter into ViewPager
        mPager.setAdapter(mViewPagerAdapter);

        // Capture tab button clicks
        ActionBar.TabListener tabListener = new ActionBar.TabListener()
        {
            @Override
            public void onTabSelected(ActionBar.Tab tab, android.app.FragmentTransaction ft)
            {
                int pos = tab.getPosition();
                mPager.setCurrentItem(pos);
                sInTools = mViewPagerAdapter.isTool(pos);

                if(mBound)
                    mService.setCurrentView(pos);

                if(sInTools)
                    mV1Name.setVisibility(View.GONE);
                else
                    mHandler.post(mShowSetting);
            }

            @Override
            public void onTabUnselected(ActionBar.Tab tab, android.app.FragmentTransaction ft)
            {
                int pos = tab.getPosition();
            }

            @Override
            public void onTabReselected(ActionBar.Tab tab, android.app.FragmentTransaction ft)
            {
                int pos = tab.getPosition();
            }
        };

        // Set our tab background to black if on Android 4.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getActionBar().setStackedBackgroundDrawable(new ColorDrawable(Color.parseColor("#000000")));
        }

        // Create first Tab
        tab = mActionBar.newTab().setText(R.string.alert_run_v1_view).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // Create second Tab
        tab = mActionBar.newTab().setText(R.string.alert_run_v1_alert_view).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // create the tool Fragment
        tab = mActionBar.newTab().setText(R.string.alert_run_tool_view).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // media sound controlled by phone key
        if(YaV1.sPrefs.getBoolean("phone_alert", false) && YaV1.sPrefs.getBoolean("volume_alert", false))
            setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // get the tool tab index
        sToolTabIndex = mActionBar.getTabCount() - 1;

        // if we did the long click on overlay, we issue the dialog now
        if(this.getIntent().getBooleanExtra("override", false))
        {
            YaV1.sLockoutOverride.resetActivityOwner(this);
        }
    }

    // our callback from the v1 service after bind/unbind

    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // We've bound to YaV1AlertService, cast the IBinder and get LocalService instance
            mService = ((YaV1AlertService.LocalBinder) service).getService();
            mBound = true;

            mService.setCurrentView(mLastView);
            // if not running, wait for Device
            if(mDemo)
            {
                mService.setDemoMode(true);
                // delay the read data and fire them
                if(!pushDemo())
                {
                    mService.setDemoMode(false);
                    finish();
                    return;
                }
            }
            else
            {
                if(!mService.isClientRunning())
                {
                    // we relaunch the main activity with restart
                    Intent intent = new Intent(YaV1.sContext, YaV1Activity.class);
                    intent.putExtra("restart", true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    YaV1.sContext.startActivity(intent);
                    finish();
                    return;
                }
                else
                {
                    if(mFromDark)
                    {
                        // Log.d("Valentine", "Restart from DarkMode");
                        // reset the V1 display
                        if(mService.getDisplay() != YaV1.sDarkDisplay)
                            mService.forceDisplay(YaV1.sDarkDisplay);
                        // muting
                        if(mService.isMuteByUser() != YaV1.sDarkMute)
                            mService.toggleMute();
                        // reset the mutting
                        mFromDark = false;
                    }

                    adjustIcons();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            mBound = false;
        }
    };

    // our callback from the gsp service after bind/unbind
    private ServiceConnection mGpsConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // We've bound to V1AlertService, cast the IBinder and get LocalService instance
            mGpsService = ((YaV1GpsService.gpsBinder) service);
            mGpsBound   = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            mGpsBound = false;
        }
    };

    // Long press for dark mode
    public boolean onKeyLongPress(int keyCode, KeyEvent event)
    {
        if(keyCode == KeyEvent.KEYCODE_BACK && YaV1.sPrefs.getBoolean("go_dark", false) && mBound)
        {
            // save current mute / display
            YaV1.sDarkDisplay = mService.getDisplay();
            YaV1.sDarkMute    = mService.isMuteByUser();
            // mute V1 and Display off
            if(!YaV1.sDarkMute)
                mService.toggleMute();
            if(YaV1.sDarkDisplay)
                mService.forceDisplay(false);

            Intent intent = new Intent(this, YaV1DarkActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onKeyLongPress(keyCode, event);
    }

    // can be called from Fragment to reset Gps

    public void resetGps()
    {
        // only if Gps is bound
        if(mGpsBound)
            mGpsService.restartGps();
    }

    // long click in Gps

    public void onLongClick()
    {
        // we would check if we have some alert to show in dialog
        if(YaV1AlertService.requestOverride())
        {
            // Activity to run the dialog on
            YaV1.sLockoutOverride.setActivity(this);
        }
    }

    // click the mute button

    public void clickCtrl(View view)
    {
        boolean refreshFragment = false;
        boolean mode            = false;

        switch(view.getId())
        {
            case R.id.mute_button:
                if(mBound)
                {
                    mService.toggleMute();
                    adjustIcons();
                }
                break;
            case R.id.savvy_speed_minus:
                if(mService.mSavvySpeedAsInt > 10)
                {
                    mService.setSavvySpeed(mService.mSavvySpeedAsInt - 5);
                    refreshFragment = true;
                }
                break;
            case R.id.savvy_speed_plus:
                if(mService.mSavvySpeedAsInt < 90)
                {
                    mService.setSavvySpeed(mService.mSavvySpeedAsInt + 5);
                    refreshFragment = true;
                }
                break;
            case R.id.v1_mode:
            {
                int i = YaV1.getNextMode();
                if(mBound && i >= 0)
                {
                    YaV1.mV1Client.changeMode( (byte) i);
                    mode = true;
                }
                break;
            }
            case R.id.current_setting_name:
            {
                if(mBound)
                {
                    popupQuickSettings();
                }
                break;
            }
            case R.id.current_sweep_name:
            {
                if(mBound)
                {
                    popupQuickSweeps();
                }
                break;
            }
            case R.id.alert_sound:
            {
                if(SoundParam.phoneAlertEnabled)
                {
                    SoundParam.mPhoneAlertOn = !SoundParam.mPhoneAlertOn;
                    refreshFragment = true;
                }
                break;
            }
            case R.id.voice_sound:
            {
                if(SoundParam.sVoiceCount > 0)
                {
                    SoundParam.mVoiceAlertOn = !SoundParam.mVoiceAlertOn;
                    refreshFragment = true;
                }
                break;
            }
            case R.id.vibrate_sound:
            {
                if(SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED)
                {
                    SoundParam.mVibratorOn = !SoundParam.mVibratorOn;
                    refreshFragment = true;
                }
                break;
            }
            case R.id.lockout:
            {
                if(YaV1CurrentPosition.sLockout && YaV1.sAutoLockout != null)
                {
                    // we switch from one mode to another
                    if(YaV1.sAutoLockout.mMode == LockoutData.MODE_NORMAL)
                        YaV1.sAutoLockout.mMode = LockoutData.MODE_FROZEN;
                    else if(YaV1.sAutoLockout.mMode == LockoutData.MODE_FROZEN)
                        YaV1.sAutoLockout.mMode = LockoutData.MODE_NORMAL;
                    refreshFragment = true;
                }

                break;
            }
        }

        if(refreshFragment || mode)
        {
            if(mode)
            {
                final ProgressDialog lDlg = new ProgressDialog(YaV1ScreenActivity.this);
                lDlg.setCancelable(false);
                lDlg.setIndeterminate(true);
                lDlg.setMessage(getText(R.string.wait));
                lDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                lDlg.show();
                new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        YaV1AlertService.renewModeData();
                        lDlg.dismiss();
                        YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
                        // mHandler.post(mRefreshToolTab);
                    }
                }).start();
            }
            else
            {
                YaV1.postEvent(new InfoEvent(InfoEvent.Type.V1_INFO));
            }
        }
    }
    // Create our Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        mMenu = menu;

        MenuInflater inflater = getMenuInflater();
        // remove the stop option when !in Demo mode
        inflater.inflate(R.menu.yav1_screen, menu);
        // adjust the icons state
        adjustIcons();
        return true;
    }

    // Do the actions in the menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;

            case R.id.disp_toggle:
                if(mBound)
                {
                    mService.toggleDisplay();
                    adjustIcons();
                    return true;
                }
                return false;
            case R.id.sound_toggle:
                if(mBound)
                {
                    mService.toggleMute();
                    adjustIcons();
                    return true;
                }
                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume()
    {
        // check if the last view is compatible
        if(mLastView < 0 || mLastView > mViewPagerAdapter.getCount() || mViewPagerAdapter.isTool(mLastView))
            mLastView = Integer.valueOf(YaV1.sPrefs.getString("default_view", "1"));

        super.onResume();
        mContext = this;

        // register our selves to the Bus
        YaV1.getEventBus().register(this);

        // Log.d("Valentine", "Screen Activity resumed");

        if(!mBound)
        {
            // we bind to the service
            Intent intent = new Intent(getApplicationContext(), YaV1AlertService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }

        // rebind to GpsService if needed

        if(!mGpsBound && YaV1CurrentPosition.enabled)
        {
            // we bind to the service
            Intent gintent = new Intent(getApplicationContext(), YaV1GpsService.class);
            bindService(gintent, mGpsConnection, Context.BIND_AUTO_CREATE);
        }

        // we go to the last known view
        if(mPager.getCurrentItem() != mLastView)
        {
            if(mBound)
                mService.setCurrentView(mLastView);
            mPager.setCurrentItem(mLastView);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(mDisplayReceiver, new IntentFilter("V1Display"));

        //mHandler.postDelayed(mShowSetting, 7000);

        // check if we need to hide the speaker button
        ImageButton iBtn = (ImageButton) findViewById(R.id.mute_button);
        iBtn.setVisibility(YaV1.sPrefs.getBoolean("hide_speaker_button", false) ? View.GONE : View.VISIBLE);
    }

    @Override

    public void onPostResume()
    {
        super.onPostResume();
        YaV1.superResume();
    }

    @Override
    public void onPause()
    {
        YaV1.superPause();
        super.onPause();

        // unregister
        YaV1.getEventBus().unregister(this);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDisplayReceiver);

        // Unbind from the service
        if(mBound)
        {
            if(mDemo /* && !YaV1.sInTestingMode*/)
                mService.setDemoMode(false);
            unbindService(mConnection);
            mBound = false;
        }

        if(mGpsBound)
        {
            unbindService(mGpsConnection);
            mGpsBound = false;
        }
        Log.d("Valentine", "Screen activity onPause not visible lastView " + mLastView);
    }

    @Override
    public void onStop()
    {
        Log.d("Valentine", "Screen Activity Stop");
        mContext = null;
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        Log.d("Valentine", "Screen Activity Destroy");
        mContext = null;
        super.onDestroy();
    }

    // subscribe to the InfoEvent
    @Subscribe
    public void onInfoEvent(InfoEvent info)
    {
        // we would redisplay the settings, or adjust the icon
        if(info.getType() == InfoEvent.Type.V1_INFO)
        {
            Log.d("Valentine", "Refresh settings");
            // show the V1 info (setting / sweep / battery)
            runOnUiThread(mShowSetting);
        }
    }

    // an adapter use for V1 settings and custom sweeps

    class quickAdapter extends ArrayAdapter<String>
    {
        Context mCtx;
        List<String> v1Settings;
        int currentPosition;

        public quickAdapter(Context context, int textViewResourceId, List<String> list, int current)
        {
            super(context, textViewResourceId, list);
            currentPosition = current;
            v1Settings = list;
            this.mCtx = context;
        }

        // get the view for the settings, current is yellow, other are ligh blue

        public View getView (int position, View convertView, ViewGroup parent)
        {
            View row = convertView;

            if (row == null)
            {
                LayoutInflater inflater = ((FragmentActivity) mCtx).getLayoutInflater ();  // we get a reference to the activity
                row = inflater.inflate (R.layout.quick_v1_settings_item, parent, false);
            }

            TextView tv1 = (TextView) row.findViewById (R.id.singleItem);
            if(position == currentPosition)
            {
                tv1.setBackgroundResource(R.drawable.btn_default_normal_yellow);
                tv1.setTextColor(Color.parseColor("#ff0000"));
            }
            else
            {
                tv1.setBackgroundResource(R.drawable.btn_default_normal_lblue);
                tv1.setTextColor(Color.parseColor("#000000"));
            }
            tv1.setText(v1Settings.get(position));
            return row;

        }
    };

    // show the V1 settings list as popup

    private void popupQuickSettings()
    {
        final int currentPosition     = YaV1.sV1Settings.getCurrentSettingPosition();


        final Dialog dialog = new Dialog(YaV1ScreenActivity.this);
        dialog.setContentView(R.layout.quick_v1_settings);
        dialog.setTitle(R.string.mn_v1_settings);

        ListView listView = (ListView) dialog.findViewById(R.id.list);

        quickAdapter ad = new quickAdapter(this, R.id.singleItem, YaV1.sV1Settings.getSettingList(), currentPosition);
        listView.setAdapter(ad);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                // we would push if the position is not same as current
                if(mBound && arg2 != currentPosition)
                {
                    final int newPos = arg2;
                    // ask for Push ?
                    new AlertDialog.Builder(mContext)
                            .setTitle(null)
                            .setMessage(R.string.tool_tab_push_setting)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int whichButton)
                                {
                                    if(YaV1.sV1Settings.pushSettingFromPosition(newPos))
                                    {
                                        return;
                                    }
                                }
                            }).setNegativeButton(R.string.no, null).show();
                }
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void popupQuickSweeps()
    {
        final int currentPosition = YaV1.sSweep.getCurrentPosition();

        final Dialog dialog = new Dialog(YaV1ScreenActivity.this);
        dialog.setContentView(R.layout.quick_v1_settings);
        dialog.setTitle(R.string.mn_sweeps);

        // we need the Adapter
        ListView listView = (ListView) dialog.findViewById(R.id.list);

        quickAdapter ad = new quickAdapter(this, R.id.singleItem, YaV1.sSweep.getQuickSweepList(), currentPosition);
        listView.setAdapter(ad);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                // we would push if the position is not same as current
                if(mBound && arg2 != currentPosition)
                {
                    final int newPos = arg2;
                    // ask for Push ?
                    new AlertDialog.Builder(mContext)
                            .setTitle(null)
                            .setMessage(R.string.tool_tab_push_sweep)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int whichButton)
                                {
                                    YaV1.sSweep.pushFromQuickSweeps(newPos, mContext);
                                }
                            }).setNegativeButton(R.string.no, null).show();
                }
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // Runnable to display the current settings name as Toast

    private final Runnable mShowSetting = new Runnable()
    {
        public void run()
        {
            mV1Name.setVisibility(View.VISIBLE);
            TextView txtName = (TextView) findViewById(R.id.setting_name);
            String str = YaV1.sV1Settings.getCurrentName();
            txtName.setText(str);
            // not yet ready yet ?
            if(!str.isEmpty())
            {
                // if euro mode and custom sweeps, get the sweep names
                if(YaV1.sModeData != null && YaV1.sModeData.getCustomSweeps())
                {
                    str = str + "\n" + YaV1.sSweep.getCurrentName();
                    txtName.setText(str);
                }
                // if Voltage required, show it
                if(!mDemo && YaV1AlertService.sBatteryVoltage && YaV1.sBatteryVoltage > 0)
                {
                    str = str + "\n" + String.format("%.1f V", YaV1.sBatteryVoltage.floatValue());
                    txtName.setText(str);
                }
            }
        }
    };

    // adjust the icon state for sound/display
    private void adjustIcons()
    {
        MenuItem item;
        if(mBound && mMenu != null)
        {
            // display
            boolean s       = mService.getDisplay();
            item   = mMenu.findItem(R.id.disp_toggle);
            if(item != null)
            {
                if(s)
                {
                    item.setIcon(R.drawable.disp_on);
                    item.setTitle(R.string.mn_v1_display_on);
                }
                else
                {
                    item.setIcon(R.drawable.disp_off);
                    item.setTitle(R.string.mn_v1_display_off);
                }
            }

            // sound

            s    = mService.getMute();
            item = mMenu.findItem(R.id.sound_toggle);
            ImageView iv = (ImageView) findViewById(R.id.mute_button);

            if(item != null)
            {
                if(s)
                {
                    if(mService.isMuteByUser())
                    {
                        item.setIcon(R.drawable.mute_on_user);
                        if(iv != null)
                            iv.setImageResource(R.drawable.mute_on_user);
                    }
                    else
                    {
                        item.setIcon(R.drawable.mute_on);
                        if(iv != null)
                            iv.setImageResource(R.drawable.mute_on);
                    }
                    item.setTitle(R.string.mn_v1_mute_on);
                }
                else
                {
                    item.setIcon(R.drawable.mute_off);
                    if(iv != null)
                        iv.setImageResource(R.drawable.mute_off);
                    item.setTitle(R.string.mn_v1_mute_off);
                }
            }

        }
    }

    // we read demo data and fire them

    private boolean pushDemo()
    {
        // get random demo data file
        try
        {
            String [] list = getResources().getAssets().list("demo");
            if(list.length > 0)
            {
                // Select a random (valid) index of the array
                Random rnd = new Random();
                int index  = rnd.nextInt(list.length);

                // for each band after others
                //index = 2;

                Log.d("Valentine", "Playing demo " + list[index] + " Index " + index);

                // open the give file and play it
                InputStream inputStream  = getResources().getAssets().open("demo/" + list[index]);

                BufferedReader reader    = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder sb         = new StringBuilder();

                String line              = null;
                while ((line = reader.readLine()) != null)
                    sb.append(line).append("\n");


                // close the stream
                inputStream.close();
                // fire the complete string
                YaV1.mV1Client.startDemoMode(sb.toString(), false);

                return true;
            }
        }
        catch(IOException e)
        {
        }
        return false;
    }

    // check if a device exists, if not we finish the activity after a message

    private void checkDeviceExists()
    {
        if(YaV1.sDeviceName == "")
        {
            mShowMessage(getString(R.string.device_error), getString(R.string.error));
            finish();
            return;
        }
    }

    // our FragmentTag

    public void setFragmentTag(int i, String s)
    {
        mFragmentTag[i] = s;
    }

    /*
     * This is a helper function to show an AlertDialog box
     */
    private void mShowMessage(final String msg, final String _title)
    {
        runOnUiThread(new Runnable() {
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(YaV1ScreenActivity.this);
                builder.setTitle(_title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        })
                        .create()
                        .show();
            }
        });
    }
}
