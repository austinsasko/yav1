package com.franckyl.yav1.alert_histo;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.utils.GMapUtils;
import com.franckyl.yav1lib.YaV1Alert;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryActivity extends Activity
{
    private ActionBar                mActionBar;
    private ViewPager                mPager;
    private ActionBar.Tab            tab;
    private AlertHistoryPagerAdapter mViewPagerAdapter;
    private String                   mFragmentTag[] = {"", "", ""};

    public  static AlertHistoryList   mAlertList         = new AlertHistoryList("", false);
    public  static int                mCurrentSelection  = -1;

    public static int                TTL                = 10;
    public static int                DEFAULT_TTL        = 10;
    public static int                MIN_MARK_SPACE     = 50;
    public static final int          MODE_STANDARD      = 0;
    public static final int          MODE_ALL           = 1;

    private static int               mCurrentMode       = MODE_STANDARD;

    public static  int               mDefaultListColor[] = {Color.parseColor("#FF0000"),
                                                            Color.parseColor("#FF0590"),
                                                            Color.parseColor("#FAACDA"),
                                                            Color.parseColor("#FFFF00"),
                                                            Color.parseColor("#FFB90F") };

    public static SparseArray<BitmapDescriptor> sIconColor = new SparseArray<BitmapDescriptor>();
    private static float sHsvCompo[] = {0.f, 0.f, 0.f};

    // create the activity

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        try
        {
            MapsInitializer.initialize(getApplicationContext());
        }
        catch(Exception e)
        {
            Toast.makeText(this, "Google map can't be initialized, stopping", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // request the action bar
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        // get the settings
        TTL             = Integer.valueOf(YaV1.sPrefs.getString("gmap_ttl", "10"));
        MIN_MARK_SPACE  = Integer.valueOf(YaV1.sPrefs.getString("gmap_marker", "50"));
        GMapUtils.refreshSettings();

        // get the default color

        mDefaultListColor[YaV1Alert.BAND_LASER] = YaV1.sPrefs.getInt("gmap_laser_color", Color.TRANSPARENT);
        mDefaultListColor[YaV1Alert.BAND_KA] = YaV1.sPrefs.getInt("gmap_ka_color", Color.TRANSPARENT);
        mDefaultListColor[YaV1Alert.BAND_K] = YaV1.sPrefs.getInt("gmap_k_color", Color.TRANSPARENT);
        mDefaultListColor[YaV1Alert.BAND_X] = YaV1.sPrefs.getInt("gmap_x_color", Color.TRANSPARENT);
        mDefaultListColor[YaV1Alert.BAND_KU] = YaV1.sPrefs.getInt("gmap_ku_color", Color.TRANSPARENT);

        // if we have Gps and not in demo, we use the gps layout
        setContentView(R.layout.alert_histo_alert_history_activiy);

        // set the Action Bar
        mActionBar = getActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Locate ViewPager in activity_main.xml
        mPager = (ViewPager) findViewById(R.id.pager);

        // Activate Fragment Manager
        FragmentManager fm = getFragmentManager();

        // Capture ViewPager page swipes
        ViewPager.SimpleOnPageChangeListener ViewPagerListener = new ViewPager.SimpleOnPageChangeListener()
        {
            @Override
            public void onPageSelected(int position)
            {
                super.onPageSelected(position);
                mActionBar.setSelectedNavigationItem(position);
            }
        };

        mPager.setOnPageChangeListener(ViewPagerListener);

        mViewPagerAdapter = new AlertHistoryPagerAdapter(fm);
        // Set the View Pager Adapter into ViewPager
        mPager.setAdapter(mViewPagerAdapter);
        mPager.setCurrentItem(0);

        // Locate the adapter class called ViewPagerAdapter.java
        //Log.d("Valentine", "Set the ViewAdapter");

        // Capture tab button clicks
        ActionBar.TabListener tabListener = new ActionBar.TabListener()
        {
            @Override
            public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft)
            {
                int pos = tab.getPosition();
                mPager.setCurrentItem(pos);
            }

            @Override
            public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft)
            {
                int pos = tab.getPosition();
            }

            @Override
            public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft)
            {
                int pos = tab.getPosition();
            }
        };

        // get the default alert color

        // Create first Tab
        tab = mActionBar.newTab().setText(R.string.gmap_tab_files).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // Create second Tab
        tab = mActionBar.newTab().setText(R.string.gmap_tab_alerts).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // the map
        tab = mActionBar.newTab().setText(R.string.gmap_tab_map).setTabListener(tabListener);
        mActionBar.addTab(tab);

        // current tab is 0
        mPager.setCurrentItem(0);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        //YaV1.superResume();
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
        super.onPause();
        YaV1.superPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        if(isFinishing())
        {
            mAlertList.clear();
            mAlertList.setNbSelected(0);
            mCurrentSelection = -1;
            mCurrentMode      = MODE_STANDARD;
        }

        super.onDestroy();
    }

    // check if we have the color in the bitmap descriptot

    public static void checkIconColor(int mColor)
    {
        if(sIconColor.get(mColor) == null)
        {
            // get the HSV
            Color.colorToHSV(mColor, sHsvCompo);
            // we create the bitmapDescriptor
            sIconColor.put(mColor, BitmapDescriptorFactory.defaultMarker(sHsvCompo[0]));
        }
    }

    // when user click to get the details

    public void ClickDetail(View v)
    {
        int pos = (Integer) v.getTag();
        try
        {
            AlertHistory h = mAlertList.get(pos);

            if(h != null)
            {
                AlertHistorySummaryDialog summary = new AlertHistorySummaryDialog(this, h, mAlertList.getFileName(), mAlertList.isCsv() ? 0 : 1);
                // show the dialog
                summary.show();
            }
        }
        catch(ArrayIndexOutOfBoundsException e)
        {
            Log.d("Valentine", "Out of bound exception Detail dialog " + e.toString());
        }

    }

    public static void setCurrentMode(int i)
    {
        mCurrentMode = i;
        mAlertList.resetChanged(true);
    }

    public static int getCurrentMode()
    {
        return mCurrentMode;
    }

    public void setFileSelected(int i)
    {
        mCurrentSelection = i;
    }
    // we have got a new AlertList

    public void updateList(AlertHistoryList l)
    {
        mAlertList = l;
        mAlertList.setNbSelected(0);

        // switch to the Alert tab
        mPager.setCurrentItem(1);
        mCurrentMode = MODE_STANDARD;

        // update the adapter
        AlertHistoryAlertFragment fa = (AlertHistoryAlertFragment) getFragmentManager().findFragmentByTag(mFragmentTag[1]);
        fa.updateList();

        // clear the map if there
        AlertHistoryMapFragment fm = (AlertHistoryMapFragment) getFragmentManager().findFragmentByTag(mFragmentTag[2]);
        if(fm != null)
            fm.clearMap();
    }

    // initialize the Fragment tag

    public void setFragmentTag(int i, String s)
    {
        mFragmentTag[i] = s;
    }

    public String getFragmentTag(int i)
    {
        return mFragmentTag[i];
    }

    public static AlertHistoryList getAlertList()
    {
        return mAlertList;
    }
}
