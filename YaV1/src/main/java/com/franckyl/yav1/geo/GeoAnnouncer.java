package com.franckyl.yav1.geo;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

/**
 * [P3-GEO] Announces an automatic profile switch with a toast and, when the
 * "geo_tts" preference is enabled, a spoken phrase.
 *
 * Owns its own small TextToSpeech instance (created lazily on the first
 * spoken announcement) so it does not interfere with the alert TTS feature.
 */
public class GeoAnnouncer
{
    private static final String LOG_TAG = "Valentine GEO";

    private final Context mContext;
    private final Handler mMainHandler;

    private TextToSpeech     mTts      = null;
    private volatile boolean mTtsReady = false;

    public GeoAnnouncer(Context context)
    {
        mContext     = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /** Show a toast and optionally speak the phrase. Any-thread safe. */
    public void announce(final String phrase, boolean speak)
    {
        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Toast.makeText(mContext, phrase, Toast.LENGTH_LONG).show();
                }
                catch(Exception e)
                {
                    Log.d(LOG_TAG, "Toast failed: " + e);
                }
            }
        });

        if(speak)
            speakOnMain(phrase);
    }

    private void speakOnMain(final String phrase)
    {
        mMainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if(mTts == null)
                    createTts(phrase);
                else
                    speakNow(phrase);
            }
        });
    }

    private void createTts(final String firstPhrase)
    {
        try
        {
            mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener()
            {
                @Override
                public void onInit(int status)
                {
                    if(status == TextToSpeech.SUCCESS && mTts != null)
                    {
                        try
                        {
                            mTts.setLanguage(Locale.getDefault());
                        }
                        catch(Exception e)
                        {
                            // keep the engine default
                        }
                        mTtsReady = true;
                        speakNow(firstPhrase);
                    }
                    else
                    {
                        Log.d(LOG_TAG, "TTS init failed, status " + status);
                        release();
                    }
                }
            });
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "Unable to create TextToSpeech: " + e);
            mTts = null;
        }
    }

    private void speakNow(String phrase)
    {
        if(!mTtsReady || mTts == null)
            return;

        try
        {
            if(Build.VERSION.SDK_INT >= 21)
                mTts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "yav1_geo_" + System.nanoTime());
            else
                mTts.speak(phrase, TextToSpeech.QUEUE_ADD, null);
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "TTS speak failed: " + e);
        }
    }

    /** Release the TTS engine (feature disabled). */
    public void release()
    {
        mTtsReady = false;

        final TextToSpeech tts = mTts;
        mTts = null;

        if(tts != null)
        {
            mMainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        tts.stop();
                        tts.shutdown();
                    }
                    catch(Exception e)
                    {
                        // nothing to do
                    }
                }
            });
        }
    }
}
