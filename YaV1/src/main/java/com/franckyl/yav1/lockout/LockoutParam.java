package com.franckyl.yav1.lockout;

import android.content.SharedPreferences;
import android.graphics.Color;

import com.franckyl.yav1lib.YaV1Alert;

/**
 * Created by franck on 4/3/15.
 * Holds the lockout parameter
 */
public class LockoutParam
{
    public  static boolean mInitDone   = false;
    // the radius used (200 meters is default)
    public  static float mRadius        = 200f;
    // the radius for the list of lockout
    public  static int  mListRadius     = 12000;
    // move from radius center to build new list
    public  static int  mUpdateDistance = 6000;
    // default consecutive misses alert to remove it from DB
    public  static int  mMaxUnseen      = 2;
    // default minimum seen time to mark as lockout
    public  static int  mMinSeen        = 5;
    // default minimum seen to set the color alert as "Learning", none by default
    public  static int  mLearningSeen   = 0;
    // maximum unseen time to add on top of mMaxUnseen to remove a manual lockout, default is same
    public  static int  mMaxManualUnseen  = 0;
    // same as above for white listed
    public  static int  mMaxWhiteUnseen   = 0;

    // default lockout color
    public  static int  mLockoutColor       = Color.TRANSPARENT;
    // default in learning color
    public  static int  mNearLockoutColor   = Color.TRANSPARENT;
    // default manual locked color
    public  static int  mManualLockoutColor = Color.TRANSPARENT;
    // default white list color
    public  static int  mWhiteLockoutColor  = Color.TRANSPARENT;

    // Filter on signal difference

    public  static final    int   FILTER_SIGNAL_NONE   = 0;
    public  static final    int   FILTER_SIGNAL_SOFT   = 1;
    public  static final    int   FILTER_SIGNAL_STRONG = 2;

    // Which one we use, default none
    public  static int  mUseSignalFilter               = FILTER_SIGNAL_NONE;
    public  static int  mCurrentSignalThreshold        = 8;

    // the signal threshold to apply the filter
    // NONE
    // SOFT
    // STRONG

    public  static int  mSignalThreshold[]             = {8, 4, 2};

    // default drift per band
    // laser none
    // Ka 6 Mhz
    // k  10 Mhz
    // X  4 Mhz
    // Ku - not used
    public  static int  mDrift[]                          = {0, 6, 10, 4, 5};

    // allow the reset busy when still in the area, default false
    public  static boolean mAllowRemoveInCurrent          = false;

    // init from shared preferences

    public static void init(SharedPreferences sh)
    {
        mMinSeen        = Integer.valueOf(sh.getString("lockout_seen", "3"));
        mMaxUnseen      = Integer.valueOf(sh.getString("lockout_unseen", "5"));
        mLearningSeen   = Integer.valueOf(sh.getString("lockout_learning", "0"));
        mMaxWhiteUnseen = Integer.valueOf(sh.getString("lockout_white_unseen", "0"));

        // get the colors

        mLockoutColor        = sh.getInt("lockout_color", Color.TRANSPARENT);
        mNearLockoutColor    = sh.getInt("near_lockout_color", Color.TRANSPARENT);
        mManualLockoutColor  = sh.getInt("manual_lockout_color", Color.TRANSPARENT);
        mWhiteLockoutColor   = sh.getInt("white_lockout_color", Color.TRANSPARENT);

        // reset to default (if too high because of previous version)

        if(mMaxUnseen > 8)
            mMaxUnseen = 5;

        mUseSignalFilter = Integer.valueOf(sh.getString("lockout_use_signal_filter", "0"));

        mCurrentSignalThreshold = mSignalThreshold[mUseSignalFilter];

        mMaxManualUnseen = mMaxUnseen + Integer.valueOf(sh.getString("lockout_manual_add_unseen", "0"));

        // the drift is now only adjustable for K band
        mDrift[YaV1Alert.BAND_K] = Integer.valueOf(sh.getString("lockout_k_drift", "10"));
        mInitDone = true;
    }
}
