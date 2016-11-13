package com.franckyl.yav1;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Created by franck on 9/17/13.
 */

public class YaV1SoundManager
{
    public static final int LED_NOTIFICATION_ID = 8864;
    public static final int FOR_SYSTEM          = 4;
    public static final int FORCE_SPEAKER       = 1;
    public static final int FORCE_NONE          = 0;

    // the sound pool
    private SoundPool mSoundPool = null;

    // Last signal
    private int       mLastSignal[]  = {0, 0, 0, 0, 0};

    // Laser would a stream
    private int       mLaserStream   = 0;

    // playing flag
    private boolean   mIsPlaying[]   = {false, false, false, false, false};

    // current signal strength per band
    public static int mSignalBand[]  = {0, 0, 0, 0, 0};

    // current volume is 1
    private float     mCurrentVolume = 1.0f;

    // volume according to strength
    public static float volumeSignal[] = {0.0f, 0.5f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f, 1f};

    // counter for number of alerts
    public static int   mAlertCount[]       = {0, 0, 0, 0, 0};
    public static int   mLastAlertCount[]   = {0, 0, 0, 0, 0};

    // the delay to play
    public static int   mDelayPlay[][] = { {0, 2000, 1500, 1000, 900, 700, 500, 300, 200},
                                           {0, 1800, 1300, 910, 810, 610, 410, 210, 120},
                                         };

    // the current box voice alert played

    public static int   mCurrentBoxAlert[][] = { {0,0,0,0},
                                                 {0,0,0,0},
                                                 {0,0,0,0},
                                                 {0,0,0,0}};
    public static int   mCurrentLaserVoice   = 0;

    // count of current playing sound

    public int          mCurrentPlaying = 0;

    private int       mStrongestBand    = -1;
    public  int       mMaxSignal        = 0;
    public  int       mTotalAlert       = 0;
    public  int       mLastTotalAlert   = 0;
    public  int       mInBox            = 0;

    // Handler for sound delay
    private Handler   mHandler;

    // Led notification

    private NotificationManager mNM          = null;
    private Notification mLedNotification    = new Notification();
    private boolean      mLedIsRunning       = false;
    private int          mCurrentLed         = 0;

    // Vibrator

    private Vibrator     mVibrator           = null;

    private long     mVibratorPattern[][]    = { {0, 100, 100, 100, 100, 0, 0, 200, 700},
                                                 {0, 100, 100, 100, 100, 0, 0, 200, 300}};
    private long     mVibratorSPattern[][]   = { {0, 200, 700},
                                                 {0, 200, 300}};
    private boolean  mVibratorIsRunning      = false;
    private int      mCurrentPattern         = 0;

    private final    AudioManager  mAudioManager;

    // constructor

    public YaV1SoundManager()
    {
        mAudioManager = (AudioManager) YaV1.sContext.getSystemService(Context.AUDIO_SERVICE);
    }

    // initialize the pool

    public void init(SharedPreferences pref)
    {
        mHandler        = YaV1.sLooper.getHandler();

        if(SoundParam.mLedEnabled > SoundParam.LED_DISABLED)
            mNM       = (NotificationManager) YaV1.sContext.getSystemService(Context.NOTIFICATION_SERVICE);
        else
            stopLed(true);

        if(SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED)
        {
            mVibrator = (Vibrator) YaV1.sContext.getSystemService(Context.VIBRATOR_SERVICE);
            if(!mVibrator.hasVibrator())
            {
                Log.d("Valentine Sound", "Vibrator can't be enabled, no vibrator on device");
                stopVibrator(true);
            }
        }
        else
            stopVibrator(true);

        // we have changed parameters for soundPool

        if(SoundParam.mIsDifferent)
        {
            mSoundPool = SoundParam.getSoundPool();
            SoundParam.mIsDifferent = false;
        }
    }

    // play the early laser

    public void playEarlyLaser(boolean mute)
    {
        if(SoundParam.phoneAlertEnabled && SoundParam.mPhoneAlertOn && !mute && SoundParam.soundLoaded && SoundParam.mSoundId[0] != 0)
        {
            if(mLaserStream == 0)
            {
                mLaserStream = mSoundPool.play(SoundParam.mSoundId[0], mCurrentVolume, mCurrentVolume, 1, -1, 1.0f);
                mSignalBand[0] = 8;
                mCurrentPlaying++;
            }
        }
    }

    // play the bogey sound

    private void playBogey()
    {
        if(SoundParam.mBogeyId > 0)
        {
            //Log.d("Valentine", " *** playing bogey ** ");
            mSoundPool.play(SoundParam.mBogeyId, mCurrentVolume, mCurrentVolume, 1, 0, 1.0f);
        }
    }

    // reset some values before process

    public void resetBeforeProcess()
    {
        for(int i=0; i <5; i++)
        {
            mLastAlertCount[i] = mAlertCount[i];
            mLastSignal[i]     = mSignalBand[i];
            mSignalBand[i]     = 0;
            mAlertCount[i]     = 0;
        }

        mLastTotalAlert = mTotalAlert;
        mTotalAlert     = 0;
        mStrongestBand  = -1;
        mInBox          = 0;
        mMaxSignal      = 0;
    }

    // set the alert band signal

    public void setAlertBand(int band, int signal)
    {
        mAlertCount[band]++;
        mSignalBand[band] = Math.max(mSignalBand[band], signal);

        if(mStrongestBand < 0)
        {
            mStrongestBand = band;
            mMaxSignal     = signal;
        }
        else
        {
            // if signal stronger or equal but band "stronger"
            if(signal > mMaxSignal || (signal == mMaxSignal && band < mStrongestBand))
            {
                mMaxSignal      = signal;
                mStrongestBand  = band;
            }
        }
        mTotalAlert++;
        Log.d("Valentine Lockout", "Alert for band " + band + " Signal " + signal + " Set " + mTotalAlert);
    }

    // play the current requested sound

    public void playAll(boolean mute)
    {
        boolean localOff = (mAudioManager.isMusicActive() && SoundParam.mNoSoundOnMusic ? true : false);

        if(!SoundParam.phoneAlertEnabled || !SoundParam.soundLoaded || !SoundParam.mPhoneAlertOn || localOff)
        {
            // check if would play the voice
            if(SoundParam.sVoiceCount > 0)
            {
                // if we are in savvy mode and we want always to play voice, we override the mute
                if(mute && SoundParam.mPlaySavvy && YaV1AlertService.getMuteSpeed())
                    mute = false;
                playVoice(mute);
            }

            // play the vibrator

            if(SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED)
            {
                if(SoundParam.mVibratorEnabled == SoundParam.VIB_ALWAYS /* || */)
                    playVibrator(mute);
                else
                    stopVibrator(false);
            }

            if(SoundParam.mLedEnabled > SoundParam.LED_DISABLED)
            {
                if(SoundParam.mLedEnabled == SoundParam.LED_ALWAYS ||
                        (SoundParam.mLedEnabled == SoundParam.LED_SCREEN_OFF &&
                        (!YaV1.sScreenOn || YaV1.sIsLocked)) || (SoundParam.mLedEnabled == SoundParam.LED_APP_BACKGROUND && YaV1.isInBackground()))
                    playLed(mute);
                else
                    stopLed(false);
            }

            return;
        }

        int     gap    = 0;
        int     nbr    = 0;
        int     i      = 0;

        for(; i<5; i++)
        {
            if(mSignalBand[i] > 0 && SoundParam.mSoundId[i] > 0)
                nbr++;
        }

        for(i=0; i < 5; i++)
        {
            if(SoundParam.mSoundId[i] == 0)
                continue;

            // mute or no signal on this band and playing, we stop
            int st = mSignalBand[i];
            // particular case for laser
            if(i == 0)
            {
                if(mute || st < 1)
                {
                    if(mLaserStream != 0)
                    {
                        mSoundPool.stop(mLaserStream);
                        mLaserStream = 0;
                        mCurrentPlaying--;
                    }
                }
                else
                {
                    // if laser not on, we start it
                    if(mLaserStream == 0)
                    {
                        mLaserStream = mSoundPool.play(SoundParam.mSoundId[i], mCurrentVolume, mCurrentVolume, 1, -1, 1.0f);
                        mCurrentPlaying++;
                    }
                    //++nbr;
                }
            }
            else
            {
                //
                // if we mute, or no more signal on this band or single sound and laser is playing we stop all
                // || mSingleSound && mSignalBand[i];
                //

                if(mute || st < 1 || (SoundParam.mSingleSound && mSignalBand[0] > 0))
                {
                    if(mIsPlaying[i])
                    {
                        //Log.d("Valentine", " Mute " + mute + " Signal " + st + " Stopping " + YaV1Alert.getBandStr(i));
                        stopPlay(i);
                    }
                }
                else
                {
                    // play bogey if new band and not first, or same band but number of alerts > lastOne
                    if(((SoundParam.mSingleSound || SoundParam.mBogeyMulti) && nbr > 1 && mLastSignal[i] == 0) ||
                       (mLastAlertCount[i] > 0 && mAlertCount[i] > mLastAlertCount[i]))
                    {
                        playBogey();
                    }

                    gap = mLastSignal[i] - st;
                    // in single mode, we would play only the strongest signal
                    if(SoundParam.mSingleSound)
                    {
                        if(i == mStrongestBand)
                        {
                            if(!mIsPlaying[i] || gap >= 1)
                                startPlay(i, st);
                        }
                        else
                            stopPlay(i);
                    }
                    else
                    {
                        // we check if we have to start or not, if we are playing but with very big difference, we stop and restart
                        if(!mIsPlaying[i] || gap >= 1)
                            startPlay(i, st);
                    }
                }
            }
        }

        // we could play the voice

        if(SoundParam.sVoiceCount > 0)
        {
            // if we are in savvy mode and we want always to play voice, we override the mute
            if(mute && SoundParam.mPlaySavvy && YaV1AlertService.getMuteSpeed())
                mute = false;
            playVoice(mute);
        }

        // play the vibrator

        if(SoundParam.mVibratorEnabled > SoundParam.VIB_DISABLED)
        {
            if(SoundParam.mVibratorEnabled == SoundParam.VIB_ALWAYS /* || */)
                playVibrator(mute);
            else
                stopVibrator(false);
        }

        // play the led

        if(SoundParam.mLedEnabled > SoundParam.LED_DISABLED)
        {
            if(SoundParam.mLedEnabled == SoundParam.LED_ALWAYS ||
               (SoundParam.mLedEnabled == SoundParam.LED_SCREEN_OFF &&
                 (!YaV1.sScreenOn || YaV1.sIsLocked)) || (SoundParam.mLedEnabled == SoundParam.LED_APP_BACKGROUND && YaV1.isInBackground()))
                playLed(mute);
            else
                stopLed(false);
        }
    }

    // play the Voice alert

    private void playVoice(boolean mute)
    {
        if(SoundParam.sVoiceCount < 1 || !SoundParam.mVoiceAlertOn)
            return;

        if(SoundParam.mNoVoiceOnCall && mAudioManager.getMode() == AudioManager.MODE_IN_CALL)
            return;

        // laser
        if(!mute && SoundParam.sVoiceLaser != null && mCurrentLaserVoice > 0)
            SoundParam.sVoiceLaser.play();
        mCurrentLaserVoice = 0;

        int i = 0;
        int j;

        for(; i<4; i++)
        {
            for(j=0; j<AlertProcessorParam.MAX_BOXES; j++)
            {
                if(!mute && SoundParam.sVoiceAlert[i][j] != null && mCurrentBoxAlert[i][j] > 0)
                    SoundParam.sVoiceAlert[i][j].play();
                mCurrentBoxAlert[i][j] = 0;
            }
        }
    }

    // play the led

    private void playLed(boolean mute)
    {
        if(mNM == null)
            return;

        if((mute || mTotalAlert < 1))
        {
            if(mLedIsRunning)
                stopLed(false);
            return;
        }

        if(mTotalAlert > mLastTotalAlert)
        {
            mLedNotification.defaults = 0;
            mLedNotification.ledARGB  = Color.GREEN;
            mLedNotification.ledOnMS  = 50;
            mLedNotification.ledOffMS = 50;
            mLedNotification.flags = Notification.FLAG_SHOW_LIGHTS /* | Notification.FLAG_ONLY_ALERT_ONCE*/;
            mNM.notify(LED_NOTIFICATION_ID, mLedNotification);
            Log.d("Valentine Lockout", "Led notified");
            mHandler.postDelayed(mLedCurrent, 250);
            mLedIsRunning = true;
        }
        else
        {
            // we check the current led settings
            int c = (mMaxSignal >= 5 || mInBox > 0 ? 50 : 1000);
            c +=  (mStrongestBand <= 1 || mInBox > 0 ? 10000 : 20000);

            // we change the led blinking

            if(c != mCurrentLed)
                adjustLedCurrent();
        }
    }

    // to post to change the Led frequency

    private Runnable mLedCurrent = new Runnable()
    {
        @Override
        public void run()
        {
            adjustLedCurrent();
        }
    };

    private void adjustLedCurrent()
    {
        mLedNotification.defaults = 0;
        mLedNotification.ledARGB  = (mStrongestBand <= 1 || mInBox > 0 ? Color.RED : Color.YELLOW);
        mCurrentLed = mLedNotification.ledOffMS =  (mMaxSignal >= 5 || mInBox > 0 ? 50 : 1000);
        mLedNotification.ledOnMS = 50;
        mLedNotification.flags = Notification.FLAG_SHOW_LIGHTS /* | Notification.FLAG_ONLY_ALERT_ONCE*/;
        if(mNM != null)
            mNM.notify(LED_NOTIFICATION_ID, mLedNotification);
        mCurrentLed += (mStrongestBand <= 1 || mInBox > 0 ? 10000 : 20000);
        mLedIsRunning = true;
    }

    // play the vibrator

    private void playVibrator(boolean mute)
    {
        if(mVibrator == null || !SoundParam.mVibratorOn)
            return;

        if(mute || mTotalAlert < 1)
        {
            if(mVibratorIsRunning)
            {
                Log.d("Valentine", "Vibrator stop");
                stopVibrator(false);
            }
            return;
        }

        if(mTotalAlert > mLastTotalAlert)
        {
            mCurrentPattern = (mMaxSignal >= 5 || mInBox > 0 ? 1 : 0);
            mVibrator.vibrate(mVibratorPattern[mCurrentPattern], 6);
            mVibratorIsRunning = true;
        }
        else
        {
            // check if we have to change pattern
            int p  = (mMaxSignal >= 5 || mInBox > 0 ? 1 : 0);
            if(p != mCurrentPattern)
            {
                mCurrentPattern = p;
                mVibrator.vibrate(mVibratorSPattern[p], 0);
            }
        }
    }

    // start to play a band

    private void startPlay(int band, int signal)
    {
        if(mIsPlaying[band])
            mHandler.removeCallbacks(mReplaySound[band-1]);
        else
            mCurrentPlaying++;

        if(SoundParam.mVolumeVsSignal)
            mSoundPool.play(SoundParam.mSoundId[band], volumeSignal[signal], volumeSignal[signal], 1, 0, 1.0f);
        else
            mSoundPool.play(SoundParam.mSoundId[band], mCurrentVolume, mCurrentVolume, 1, 0, 1.0f);

        mHandler.postDelayed(mReplaySound[band-1], mDelayPlay[SoundParam.mRampEffect][signal]);
        mIsPlaying[band] = true;
    }

    // stop to play a band

    private void stopPlay(int band)
    {
        if(mIsPlaying[band])
        {
            mHandler.removeCallbacks(mReplaySound[band-1]);
            mIsPlaying[band] = false;
            mCurrentPlaying--;
        }
    }

    // 4 runnable per bands

    private final Runnable mReplaySound[] = {
            // replay for Ka
            new Runnable()
            {
                public void run()
                {
                    if(mSignalBand[1] > 0)
                    {
                        if(SoundParam.mVolumeVsSignal)
                            mSoundPool.play(SoundParam.mSoundId[1], volumeSignal[mSignalBand[2]], volumeSignal[mSignalBand[1]], 1, 0, 1);
                        else
                            mSoundPool.play(SoundParam.mSoundId[1], 1.0f, 1.0f, 1, 0, 1);
                        // we re-launch a new update in according to the signal
                        mHandler.postDelayed(mReplaySound[0], mDelayPlay[SoundParam.mRampEffect][mSignalBand[1]]);
                        mIsPlaying[1] = true;
                        mLastSignal[1] = mSignalBand[1];
                    }
                    else
                    {
                        mIsPlaying[1] = false;
                        mLastSignal[1] = 0;
                    }
                }
            },
            // replay for K Band

            new Runnable()
            {
                public void run()
                {
                    if(mSignalBand[2] > 0)
                    {
                        if(SoundParam.mVolumeVsSignal)
                            mSoundPool.play(SoundParam.mSoundId[2], volumeSignal[mSignalBand[2]], volumeSignal[mSignalBand[2]], 1, 0, 1);
                        else
                            mSoundPool.play(SoundParam.mSoundId[2], 1.0f, 1.0f, 1, 0, 1);
                        // we re-launch a new update in according to the signal
                        mHandler.postDelayed(mReplaySound[1], mDelayPlay[SoundParam.mRampEffect][mSignalBand[2]]);
                        mIsPlaying[2] = true;
                        mLastSignal[2] = mSignalBand[2];
                    }
                    else
                    {
                        mIsPlaying[2] = false;
                        mLastSignal[2] = 0;
                    }
                }
            },
            // replay for X Band

            new Runnable()
            {
                public void run()
                {
                    if(mSignalBand[3] > 0)
                    {
                        if(SoundParam.mVolumeVsSignal)
                            mSoundPool.play(SoundParam.mSoundId[3], volumeSignal[mSignalBand[2]], volumeSignal[mSignalBand[3]], 1, 0, 1);
                        else
                            mSoundPool.play(SoundParam.mSoundId[3], 1.0f, 1.0f, 1, 0, 1);
                        // we re-launch a new update in according to the signal
                        mHandler.postDelayed(mReplaySound[2], mDelayPlay[SoundParam.mRampEffect][mSignalBand[3]]);
                        mIsPlaying[3] = true;
                        mLastSignal[3] = mSignalBand[3];
                    }
                    else
                    {
                        mIsPlaying[3] = false;
                        mLastSignal[3] = 0;
                    }
                }
            },

            // replay for Ku Band

            new Runnable()
            {
                public void run()
                {
                    if(mSignalBand[4] > 0)
                    {
                        if(SoundParam.mVolumeVsSignal)
                            mSoundPool.play(SoundParam.mSoundId[4], volumeSignal[mSignalBand[2]], volumeSignal[mSignalBand[4]], 1, 0, 1);
                        else
                            mSoundPool.play(SoundParam.mSoundId[4], 1.0f, 1.0f, 1, 0, 1);
                        // we re-launch a new update in according to the signal
                        mHandler.postDelayed(mReplaySound[3], mDelayPlay[SoundParam.mRampEffect][mSignalBand[4]]);
                        mIsPlaying[4] = true;
                        mLastSignal[4] = mSignalBand[4];
                    }
                    else
                    {
                        mIsPlaying[4] = false;
                        mLastSignal[4] = 0;
                    }
                }
            }
    };

    // we stop the led blinking

    private void stopLed(boolean total)
    {
        if(mNM != null)
        {
            mNM.cancel( LED_NOTIFICATION_ID );
        }

        mLedIsRunning = false;
        mCurrentLed   = 0;

        if(total)
        {
            mNM = null;
            SoundParam.mLedEnabled = SoundParam.LED_DISABLED;
        }
    }

    // we stop the vibrator

    private void stopVibrator(boolean total)
    {
        if(mVibrator != null)
        {
            mVibrator.cancel();
        }

        mVibratorIsRunning = false;
        mCurrentPattern    = 0;

        if(total)
        {
            mVibrator = null;
            SoundParam.mVibratorEnabled = SoundParam.VIB_DISABLED;
        }
    }

    // release all sound's

    public void releaseAll()
    {
        // reset the Led
        stopLed(true);

        // stop the vibrator
        stopVibrator(true);

        // reset all the values
        for(int i=0; i<5; i++)
        {
            if(mLaserStream > 0 && mSoundPool != null)
                mSoundPool.stop(mLaserStream);

            if(mIsPlaying[i])
                mHandler.removeCallbacks(mReplaySound[i-1]);

            mSignalBand[i]      = 0;
            mIsPlaying[i]       = false;
            mLastSignal[i]      = 0;
            mLastAlertCount[i]  = 0;
        }

        // release the Sound Pool

        if(mSoundPool != null)
            mSoundPool.release();
        SoundParam.releaseSound();
    }
}
