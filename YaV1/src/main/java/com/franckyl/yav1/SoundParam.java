package com.franckyl.yav1;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.SoundPool;
import android.util.Log;

import java.util.Arrays;

/**
 * Created by franck on 4/3/15.
 * Handles the sound parameters
 */
public class SoundParam
{
    // our built in sound
    public static int sRefSound[] = {R.raw.bogey,  R.raw.native_laser, R.raw.native_ka, R.raw.native_k, R.raw.native_x,
            R.raw.pickup, R.raw.splash, R.raw.explosion, R.raw.other_laser, R.raw.barkloud, R.raw.fart,
            R.raw.cat, R.raw.cow, R.raw.choppershot, R.raw.wahwahchopper, R.raw.fastwahchopper,
            R.raw.distortedhorn, R.raw.machinegun, R.raw.shock, R.raw.wetphaser, R.raw.bogey_1, R.raw.escort_k, R.raw.escort_ka,
            R.raw.b_k, R.raw.b_ka, R.raw.b_x};

    // vibrator behavior

    public static final int VIB_DISABLED        = 0;
    public static final int VIB_SOUND_OFF       = 1;
    public static final int VIB_ALWAYS          = 2;

    // define the Led behavior

    public static final int LED_DISABLED        = 0;
    public static final int LED_SCREEN_OFF      = 1;
    public static final int LED_APP_BACKGROUND  = 2;
    public static final int LED_ALWAYS          = 3;


    public static boolean   mInitDone         = false;
    // global parameters
    public static boolean   phoneAlertEnabled = false;
    public static boolean   mVolumeVsSignal   = false;
    public static boolean   mSingleSound      = false;
    public static boolean   mBogeyMulti       = false;
    public static int       mRampEffect       = 0;
    public static boolean   mPlaySavvy        = false;

    public static int       mVibratorEnabled   = VIB_DISABLED;
    public static int       mLedEnabled        = LED_DISABLED;
    public static boolean   mNoSoundOnMusic    = false;
    public static boolean   mNoVoiceOnCall     = false;

    // the voice alerts
    public static Ringtone  sVoiceAlert[][] = { {null, null, null, null},
                                                {null, null, null, null},
                                                {null, null, null, null},
                                                {null, null, null, null}};

    // voice alert for laser
    public static Ringtone  sVoiceLaser     = null;

    // the count of voice
    public static int      sVoiceCount     = 0;

    // the sound loaded
    public  static int      mSoundBand[]   = {0, 0, 0, 0, 0};
    public  static int      mSoundBogey    = 0;
    // sound id loaded
    public  static int       mSoundId[]     = {0, 0, 0, 0, 0};
    public  static int       mBogeyId       = 0;

    private static int      mExpectedLoaded = 0;
    private static int      mNbLoaded       = 0;
    public  static boolean  soundLoaded     = false;


    public  static boolean  mPhoneAlertOn   = true;
    public  static boolean  mVoiceAlertOn   = true;
    public  static boolean  mVibratorOn     = true;

    // a flag to tell if the configuration for soundPool will be different
    public  static boolean   mIsDifferent   = false;

    // init our sound

    public static void init(SharedPreferences sh)
    {
        mPlaySavvy         = sh.getBoolean("play_savvy", false);
        // check if enabled or not
        phoneAlertEnabled  = sh.getBoolean("phone_alert", false);

        // check the led enabled or not
        mLedEnabled        = Integer.valueOf(sh.getString("led_enabled", "0"));

        mVibratorEnabled   = Integer.valueOf(sh.getString("vibrator_enabled", "0"));

        mNoSoundOnMusic = sh.getBoolean("no_sound_music", false);
        mNoVoiceOnCall  = sh.getBoolean("no_voice_on_call", false);

        mVolumeVsSignal = sh.getBoolean("volume_signal", false);
        mSingleSound    = (sh.getBoolean("sound_multiple", false) ? false : true);
        mBogeyMulti     = sh.getBoolean("bogey_multi", false);
        mRampEffect     = (sh.getBoolean("fast_ramp", false) ? 1 : 0);

        // first loop we check if we have got some sounds to check
        String  s;
        int     sound;
        int     i = 0;
        boolean diff = false;
        int     nb   = 0;

        if(phoneAlertEnabled)
        {
            for (String band : AlertProcessorParam.sPrefString)
            {
                s = "sound_" + band;
                // check the preferences
                sound = Integer.valueOf(sh.getString(s, "0"));
                if (sound != mSoundBand[i])
                    diff = true;
                if (sound > 0)
                    nb++;

                i++;
            }
        }
        else
            diff = true;

        // check the bogey sound

        sound = Integer.valueOf(sh.getString("sound_bogey", "0"));
        if(sound > 0)
            nb++;
        if(sound != mSoundBogey)
            diff = true;

        mIsDifferent    = diff;
        mExpectedLoaded = nb;

        mInitDone       = true;
    }

    // this function returns a SoundPool with loaded sound
    public static SoundPool getSoundPool()
    {
        if(phoneAlertEnabled || mExpectedLoaded > 0)
        {
            releaseSound();
            SoundPool mSoundPool = new SoundPool(64, AudioManager.STREAM_MUSIC, 0);
            mSoundPool.setOnLoadCompleteListener(mListener);

            soundLoaded     = false;
            mNbLoaded       = 0;

            int i = 0;
            int sound;
            int z = 0;
            String s;

            for (String band : AlertProcessorParam.sPrefString)
            {
                s = "sound_" + band;
                // check if the current sound is playing, if so we stop it and reset
                z = Integer.valueOf(YaV1.sPrefs.getString(s, "0"));

                if(z > 0)
                {
                    mSoundBand[i] = z;
                    mSoundId[i] = mSoundPool.load(YaV1.sContext, sRefSound[z-1], 1);
                }
                else
                {
                    mSoundBand[i] = 0;
                    mSoundId[i]   = 0;
                }
                i++;
            }

            // load the sound bogey
            sound = Integer.valueOf(YaV1.sPrefs.getString("sound_bogey", "0"));
            if(sound > 0)
            {
                mSoundBogey = sound;
                // load it
                mBogeyId = mSoundPool.load(YaV1.sContext, sRefSound[sound-1], 1);
            }

            return mSoundPool;
        }

        return null;
    }


    // a private completed loaded sound listener
    private static SoundPool.OnLoadCompleteListener mListener = new SoundPool.OnLoadCompleteListener()
    {
        @Override
        public void onLoadComplete(SoundPool soundPool, int i, int i2)
        {
            mNbLoaded++;
            if(mNbLoaded == mExpectedLoaded)
                soundLoaded = true;
        }
    };

    // reset the voiceAlert count (called by AlertProcessorParam)

    public static void resetVoiceCount()
    {
        sVoiceCount = 0;
    }

    // set a voice alert for band - 1, box number (called by AlertProcessorParam)

    public static void setVoiceAlert(int band, int nBox, Ringtone ring)
    {
        if(band == 0)
        {
            // laser voice
            sVoiceLaser = ring;
        }
        else
        {
            sVoiceAlert[band-1][nBox] = ring;
        }

        if(ring != null)
            sVoiceCount++;
    }

    // return the number of voice alert (called by AlertProcessorParam)

    public static int getVoiceNumber()
    {
        return sVoiceCount;
    }

    // release all sound
    public static void releaseSound()
    {
        for(int i=0; i<5; i++)
        {
            mSoundBand[i]       = 0;
            mSoundId[i]         = 0;
        }

        mBogeyId    = 0;
        mSoundBogey = 0;
    }
}
