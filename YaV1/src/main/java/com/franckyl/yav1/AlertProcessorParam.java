package com.franckyl.yav1;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.franckyl.yav1.utils.BandRangeList;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by franck on 4/3/15.
 * Holds the parameters for alert processing (boxes, muting, logging, lockout property)
 */
public class AlertProcessorParam
{
    public static final int MAX_BOXES = 4;

    public  static String[] sPrefString = {"laser", "ka", "k", "x", "ku"};

    private static int      mLastColor;
    private static int      mLastBox;
    public  static boolean  mInitDone = false;

    // Box definition

    public static class BoxDefinition
    {
        int high;
        int low;
        int color = Color.TRANSPARENT;
    }

    // the parameters per band definition
    // parameters per band

    public static class BandParam
    {
        int mLockout                                     = 0;
        int mLog                                         = 0;
        int mMute                                        = 0;
        int mLogMinSignal                                = 0;
        int mMaxLockoutSignal                            = 10;
        boolean mExcludeLogLockout                       = false;
        boolean mExcludeLogOutBox                        = false;
        ArrayList<BoxDefinition> mBoxes                  = null;
        ArrayList<Pair<Integer, Integer>> excludeLockout = null;
    }

    // our band parameters for Laser / Ka / K / X / Ku

    public static BandParam mBandsParam[] = {new BandParam(),
                                             new BandParam(),
                                             new BandParam(),
                                             new BandParam(),
                                             new BandParam()};

    // get the last color processed
    public static int getLastColor()
    {
        return mLastColor;
    }

    // get the last box found
    public static int getLastBox()
    {
        return mLastBox;
    }

    // get the band param of the box

    public static BandParam getBandParam(int band)
    {
        return mBandsParam[band];
    }

    // return a property as lockout, used in AlertHistory

    public static int isLockoutAble(int band, int frequency, int signal)
    {
        int rc = 0;

        if(mBandsParam[band].mLockout > 0)
        {
            rc |= YaV1Alert.PROP_CHECKABLE;
            if(mBandsParam[band].mLockout > 1 && signal >= mBandsParam[band].mMaxLockoutSignal)
                rc |= YaV1Alert.PROP_LOCKOUT_M;
            else
            {
                // search in exclusion
                if(mBandsParam[band].excludeLockout != null)
                {
                    for(Pair<Integer, Integer> p: mBandsParam[band].excludeLockout)
                    {
                        if(frequency >= p.first && frequency <= p.second)
                        {
                            rc |= YaV1Alert.PROP_LOCKOUT_M;
                            break;
                        }
                    }
                }
            }
        }

        return rc;
    }

    // get a box color for a band / frequency used in AlertHistory

    public static int getBoxColor(int band, int frequency)
    {
        int rc = Color.TRANSPARENT;
        if(mBandsParam[band].mBoxes != null)
        {
            for(BoxDefinition b: mBandsParam[band].mBoxes)
            {
                if(frequency >= b.low && frequency <= b.high) {
                    rc = b.color;
                    break;
                }
            }
        }

        return rc;
    }

    // process a signal
    public static int applyParam(YaV1Alert a, boolean lockoutAvailable)
    {
        // get the bandParam
        BandParam param = mBandsParam[a.getBand()];

        mLastColor = Color.TRANSPARENT;
        mLastBox   = -1;

        // maybe a J out ?
        int signal   = a.getSignal();
        int property = a.getProperty();

        if(signal < 1)
            return property;

        boolean hasBox    = false;
        int     inbox     = 1;
        int     frequency = a.getFrequency();

        if(param.mBoxes != null)
        {
            hasBox = true;
            int i = 0;
            for(BoxDefinition b: param.mBoxes)
            {
                if(frequency >= b.low && frequency <= b.high)
                {
                    property  |= YaV1Alert.PROP_INBOX;
                    inbox      = -1;
                    mLastColor = b.color;
                    mLastBox   = i;
                    break;
                }
                i++;
            }

            // check for muting
            if(inbox == param.mMute)
                property |= YaV1Alert.PROP_MUTE;
        }

        if(param.mLockout > 0 && lockoutAvailable && YaV1CurrentPosition.isValid)
        {
            property |= YaV1Alert.PROP_CHECKABLE;
            if(param.mLockout > 1 || signal >= param.mMaxLockoutSignal)
                property |= YaV1Alert.PROP_LOCKOUT_M;
            else
            {
                // check excluded frequency range
                if(param.excludeLockout != null)
                {
                    for(Pair<Integer, Integer> p: param.excludeLockout)
                    {
                        if(frequency >= p.first && frequency <= p.second)
                        {
                            property |= YaV1Alert.PROP_LOCKOUT_M;
                            break;
                        }
                    }
                }
            }
        }

        // logging
        if(!YaV1.mV1Client.isLibraryInDemoMode() && param.mLog > 0 && signal >= param.mLogMinSignal)
        {
            //if we are outbox and we exclude out box, do set the flag
            if( !hasBox || !(inbox == 1 && param.mExcludeLogOutBox))
                property |= YaV1Alert.PROP_LOG;
        }

        return property;
    }

    // initialize from shared preferences

    public static void init(SharedPreferences sh)
    {
        boolean boxEnabled   = sh.getBoolean("v1box", false);
        boolean logEnabled   = sh.getBoolean("log", false);
        int     logMinSignal = Integer.valueOf(sh.getString("log_min_signal", "1"));

        boolean  excludeLogLockout = sh.getBoolean("exclude_lockout", true);
        boolean  excludeLogOutBox  = sh.getBoolean("exclude_outbox", false);
        int      maxLockoutSignal  = Integer.valueOf(sh.getString("lockout_max_signal", "0"));

        ArrayList<Pair<Integer, Integer>>[] excluded = BandRangeList.parseString(sh.getString("lockout_range_list", ""));

        // reset the soundParam voice
        SoundParam.resetVoiceCount();

        // check per band
        for(int i=0; i < sPrefString.length; i++)
        {
            BandParam newBand = new BandParam();

            newBand.mExcludeLogLockout = excludeLogLockout;
            newBand.mExcludeLogOutBox  = excludeLogOutBox;

            if(!boxEnabled)
                newBand.mBoxes = null;
            else
            {
                newBand.mBoxes = checkBoxes(sh, i);
                if(newBand.mBoxes.size() < 1)
                {
                    newBand.mBoxes = null;
                    newBand.mMute = 0;
                }
                else
                    newBand.mMute = Integer.valueOf(sh.getString("mute_band"+sPrefString[i], "0"));
            }

            // check for the logging
            newBand.mLog = 0;
            if(logEnabled)
            {
                if(sh.getBoolean("log_" + sPrefString[i], false))
                {
                    newBand.mLog = 1;
                    newBand.mLogMinSignal = logMinSignal;
                }
            }

            // lockout

            newBand.mLockout          = 0;
            newBand.excludeLockout    = null;
            newBand.mMaxLockoutSignal = (maxLockoutSignal == 0 ? 10 : maxLockoutSignal);

            if(YaV1.sPrefs.getBoolean("enable_lockout", false) && i > YaV1Alert.BAND_LASER && i <= YaV1Alert.BAND_X)
            {
                // special case for Ka Band
                if(i == YaV1Alert.BAND_KA)
                {
                    if(sh.getBoolean("ka_lockout", false))
                    {
                        newBand.mLockout = 1;
                        if(sh.getBoolean("ka_manual_only", true))
                            newBand.mLockout++;
                    }
                }
                else
                {
                    newBand.mLockout = 1;
                }

                // check for exclusion
                if(excluded[i] != null)
                    newBand.excludeLockout = excluded[i];
            }

            mBandsParam[i] = newBand;
        }

        mInitDone = true;
    }

    // check the box parameters

    private static ArrayList<BoxDefinition> checkBoxes(SharedPreferences sh, int band)
    {
        ArrayList<BoxDefinition> rc = new ArrayList<BoxDefinition>();
        String sb = "band_" + sPrefString[band];

        Ringtone voiceAlert;

        List<String> items;
        String st;

        if(band == YaV1Alert.BAND_LASER)
        {
            // check for the voice alerts
            st = sh.getString("laser_voice", "");
            if(!st.isEmpty())
            {
                voiceAlert = getVoiceFromString(st);
                // initialize the voice in sound manager
                SoundParam.setVoiceAlert(0, 0, voiceAlert);
            }
        }
        else
        {
            for(int i=1; i<= MAX_BOXES; i++)
            {
                st = sh.getString(sb+i, "");
                if(!st.isEmpty())
                {
                    // split
                    items = Arrays.asList(st.split("\\s*,\\s*"));
                    if(items.size() >= 3)
                    {
                        BoxDefinition vb = new BoxDefinition();
                        vb.low   = Integer.valueOf(items.get(0));
                        vb.high  = Integer.valueOf(items.get(1));

                        // make sure opacity
                        vb.color = Integer.valueOf(items.get(2));
                        if(vb.color != Color.TRANSPARENT)
                            vb.color |= 0XFF000000;

                        if(items.size() > 3)
                            voiceAlert = getVoiceFromString(items.get(3));
                        else
                            voiceAlert = null;

                        // initialize the voice in sound manager
                        SoundParam.setVoiceAlert(band, rc.size(), voiceAlert);
                        rc.add(vb);
                    }
                }
            }
        }

        return rc;
    }

    // get ring tone for the box

    private static Ringtone getVoiceFromString(String str)
    {
        if(!str.isEmpty())
        {
            Ringtone ringtone;
            Uri ringtoneUri = Uri.parse(str);
            if(ringtoneUri != null && (ringtone = RingtoneManager.getRingtone(YaV1.sContext, ringtoneUri)) != null)
                return ringtone;
        }

        return null;
    }
}
