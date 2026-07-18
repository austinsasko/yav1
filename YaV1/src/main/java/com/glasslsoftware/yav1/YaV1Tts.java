package com.glasslsoftware.yav1;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.glasslsoftware.yav1lib.YaV1Alert;

import java.util.Locale;

/**
 * Voice announcements for new alerts using the Android TextToSpeech engine.
 *
 * When enabled (preference "tts_alert", default off) every new alert is announced
 * with its band, direction and frequency, e.g. "Ka front, 35.5". Muted alerts and
 * alerts received while the app/user muting is active are not announced.
 */
public class YaV1Tts
{
    private static final String LOG_TAG = "Valentine TTS";

    private static TextToSpeech     sTts      = null;
    private static volatile boolean sReady    = false;
    private static volatile boolean sEnabled  = false;

    private YaV1Tts()
    {
    }

    /**
     * Apply the preference state. Creates the TTS engine when the feature is turned
     * on and releases it when turned off. Safe to call repeatedly.
     */
    public static synchronized void init(Context context, boolean enabled)
    {
        sEnabled = enabled;

        if(!enabled)
        {
            release();
            return;
        }

        if(sTts != null || context == null)
            return;

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
                        catch(Exception e)
                        {
                            // keep the engine's default language
                        }
                        sReady = true;
                        Log.d(LOG_TAG, "Text to speech ready");
                    }
                    else
                    {
                        Log.d(LOG_TAG, "Text to speech init failed, status " + status);
                        release();
                    }
                }
            });
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "Unable to create TextToSpeech: " + e);
            sTts = null;
            sReady = false;
        }
    }

    /** True when announcements are enabled by preference. */
    public static boolean isEnabled()
    {
        return sEnabled;
    }

    /** Release the TTS engine. */
    public static synchronized void release()
    {
        sReady = false;
        if(sTts != null)
        {
            try
            {
                sTts.stop();
                sTts.shutdown();
            }
            catch(Exception e)
            {
                // nothing we can do
            }
            sTts = null;
        }
    }

    /**
     * Announce a new alert. No-op when the feature is off, the engine is not ready
     * yet, or the alert should stay silent.
     *
     * @param band       YaV1Alert.BAND_* value
     * @param arrowDir   YaV1Alert.ALERT_FRONT / ALERT_REAR / ALERT_SIDE
     * @param frequency  frequency in MHz (0 for laser)
     */
    public static void announce(int band, int arrowDir, int frequency)
    {
        if(!sEnabled || !sReady)
            return;

        String phrase = buildPhrase(band, arrowDir, frequency);

        if(phrase.isEmpty())
            return;

        TextToSpeech tts = sTts;
        if(tts == null)
            return;

        Log.d(LOG_TAG, "announce: " + phrase);

        try
        {
            if(Build.VERSION.SDK_INT >= 21)
                tts.speak(phrase, TextToSpeech.QUEUE_ADD, null, "yav1_alert_" + System.nanoTime());
            else
                tts.speak(phrase, TextToSpeech.QUEUE_ADD, null);
        }
        catch(Exception e)
        {
            Log.d(LOG_TAG, "TTS speak failed: " + e);
        }
    }

    /**
     * Build the spoken phrase for an alert, e.g. "Ka front, 35.5" or "Laser rear".
     * Pure function, unit tested.
     *
     * @param band       YaV1Alert.BAND_* value
     * @param arrowDir   YaV1Alert.ALERT_FRONT / ALERT_REAR / ALERT_SIDE
     * @param frequency  frequency in MHz (0 or less to omit)
     *
     * @return the phrase, or "" when the band is unknown.
     */
    public static String buildPhrase(int band, int arrowDir, int frequency)
    {
        if(band < 0 || band >= YaV1Alert.BAND_STR.length)
            return "";

        String bandStr = (band == YaV1Alert.BAND_LASER ? "Laser" : YaV1Alert.BAND_STR[band]);

        String dir;
        if(arrowDir == YaV1Alert.ALERT_FRONT)
            dir = "front";
        else if(arrowDir == YaV1Alert.ALERT_REAR)
            dir = "rear";
        else
            dir = "side";

        StringBuilder sb = new StringBuilder();
        sb.append(bandStr).append(' ').append(dir);

        if(frequency > 0)
        {
            // frequency is in MHz; announce in GHz with a single decimal (35500 -> 35.5)
            sb.append(", ").append(String.format(Locale.US, "%.1f", frequency / 1000.0));
        }

        return sb.toString();
    }
}
