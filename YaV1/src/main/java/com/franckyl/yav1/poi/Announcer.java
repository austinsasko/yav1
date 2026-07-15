package com.franckyl.yav1.poi;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

/**
 * [P2-POI][P2-ADSB] Shared alert output for the data services (POI + aircraft).
 *
 * Keeps its own TextToSpeech engine (independent from the radar-alert TTS
 * preference) plus a simple tone fallback and a Toast heads-up banner. All
 * failures are soft: worst case the alert is log-only.
 */
public final class Announcer
{
    private static final String LOG_TAG = "Valentine POI";

    public static final int STYLE_TTS_AND_SOUND = 0;
    public static final int STYLE_TTS_ONLY      = 1;
    public static final int STYLE_SOUND_ONLY    = 2;

    private static TextToSpeech     sTts   = null;
    private static volatile boolean sReady = false;

    private static final Handler sMain = new Handler(Looper.getMainLooper());

    private Announcer()
    {
    }

    /**
     * Announce an alert.
     *
     * @param context context for engine / toast creation
     * @param spoken  phrase for TTS
     * @param banner  short text for the heads-up Toast (null: no banner)
     * @param style   STYLE_* constant
     */
    public static void announce(Context context, String spoken, String banner, int style)
    {
        boolean wantTts   = (style != STYLE_SOUND_ONLY);
        boolean wantSound = (style != STYLE_TTS_ONLY);

        boolean spokeIt = false;

        if(wantTts)
            spokeIt = speak(context, spoken);

        // beep when requested, or as fallback when TTS is not available yet
        if(wantSound || (wantTts && !spokeIt))
            beep();

        if(banner != null)
            toast(context, banner);
    }

    // ------------------------------------------------------------------ tts

    private static synchronized boolean speak(Context context, String phrase)
    {
        if(phrase == null || phrase.isEmpty())
            return false;

        if(sTts == null && context != null)
        {
            try
            {
                sTts = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener()
                {
                    @Override
                    public void onInit(int status)
                    {
                        if(status == TextToSpeech.SUCCESS && sTts != null)
                        {
                            try
                            {
                                sTts.setLanguage(Locale.getDefault());
                            }
                            catch(Exception ignored)
                            {
                            }
                            sReady = true;
                            Log.d(LOG_TAG, "announcer TTS ready");
                        }
                        else
                        {
                            Log.d(LOG_TAG, "announcer TTS init failed: " + status);
                            releaseTts();
                        }
                    }
                });
            }
            catch(Exception e)
            {
                Log.d(LOG_TAG, "announcer TTS create failed: " + e);
                sTts = null;
            }
        }

        if(!sReady || sTts == null)
            return false;

        try
        {
            if(Build.VERSION.SDK_INT >= 21)
                sTts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "yav1_p2_" + System.nanoTime());
            else
                sTts.speak(phrase, TextToSpeech.QUEUE_ADD, null);
            return true;
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "announcer TTS speak failed: " + e);
            return false;
        }
    }

    /** Release the TTS engine (app shutdown). */
    public static synchronized void releaseTts()
    {
        sReady = false;
        if(sTts != null)
        {
            try
            {
                sTts.stop();
                sTts.shutdown();
            }
            catch(Exception ignored)
            {
            }
            sTts = null;
        }
    }

    // ----------------------------------------------------------------- beep

    private static void beep()
    {
        try
        {
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400);
            sMain.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    try { tg.release(); } catch(Exception ignored) {}
                }
            }, 700);
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "beep failed: " + e);
        }
    }

    // ---------------------------------------------------------------- toast

    private static void toast(final Context context, final String text)
    {
        if(context == null)
            return;

        sMain.post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_LONG).show();
                }
                catch(Exception e)
                {
                    Log.d(LOG_TAG, "toast failed: " + e);
                }
            }
        });
    }
}
