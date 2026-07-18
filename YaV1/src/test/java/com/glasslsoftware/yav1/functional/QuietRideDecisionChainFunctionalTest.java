package com.glasslsoftware.yav1.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.glasslsoftware.yav1.YaV1BsmFilter;
import com.glasslsoftware.yav1.YaV1Tts;
import com.glasslsoftware.yav1.psl.PslMuteDecider;
import com.glasslsoftware.yav1lib.YaV1Alert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * [QA-FUNC] The quiet-ride decision chain, walked as one scenario: posted
 * speed limit muting (PslMuteDecider) + the K-band BSM filter (YaV1BsmFilter)
 * + lockout/whitelist properties + the voice announcement gate and phrase
 * (YaV1Tts), asserting the final outcome the driver experiences at each step
 * of a simulated drive.
 *
 * The announce gate mirrors the one production applies in
 * YaV1AlertProcessor.newProcess: a new / released alert is spoken only when
 * TTS is enabled and the alert carries none of MUTE / BSM / LOCKOUT / WHITE
 * and no app-level mute is active.
 */
public class QuietRideDecisionChainFunctionalTest
{
    // 50 km/h zone, 5 km/h over-limit grace
    private static final int    LIMIT_KPH  = 50;
    private static final double OFFSET_KPH = 5.0;

    private PslMuteDecider mDecider;

    @Before
    public void setUp()
    {
        mDecider = new PslMuteDecider();
        TestSeams.setBsmFilterEnabled(true);
        YaV1BsmFilter.sHoldMs     = 1500;
        YaV1BsmFilter.sRampHoldMs = 3500;
        YaV1BsmFilter.sRampLeds   = 3;
        YaV1Tts.init(null, true);   // enabled, no engine on the JVM (announce() no-ops)
    }

    @After
    public void tearDown()
    {
        TestSeams.setBsmFilterEnabled(false);
        YaV1Tts.init(null, false);
    }

    /** The announcement gate from YaV1AlertProcessor.newProcess. */
    private static boolean wouldAnnounce(int property, boolean appMuted)
    {
        return YaV1Tts.isEnabled()
            && (property & (YaV1Alert.PROP_MUTE | YaV1Alert.PROP_BSM
                            | YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_WHITE)) < 1
            && !appMuted;
    }

    @Test
    public void cityDriveUnderTheLimitStaysQuietThenSpeedingUnmutes()
    {
        long t = 0;

        // cruising at 37 km/h in the 50 zone: muted immediately
        assertTrue(mDecider.decide(t, 37.0, LIMIT_KPH, OFFSET_KPH, false));

        // a K-band alert pops while muted: PSL mute alone silences it
        int property = YaV1Alert.PROP_MUTE;
        assertFalse(wouldAnnounce(property, false));

        // speeding up past limit+offset but inside the 2 km/h hysteresis
        // band: still muted
        t += 1000;
        assertTrue(mDecider.decide(t, 56.5, LIMIT_KPH, OFFSET_KPH, false));

        // clearly above the band, but not sustained yet
        t += 1000;
        assertTrue(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));

        // sustained above the band for more than HYSTERESIS_MS: unmuted
        t += PslMuteDecider.HYSTERESIS_MS + 100;
        assertFalse(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));

        // the alert is announced now, with the exact phrase
        property = 0;
        assertTrue(wouldAnnounce(property, false));
        assertEquals("K front, 24.2",
                     YaV1Tts.buildPhrase(YaV1Alert.BAND_K, YaV1Alert.ALERT_FRONT, 24171));
    }

    @Test
    public void dipBackUnderTheLimitRestartsTheUnmuteTimer()
    {
        long t = 0;
        assertTrue(mDecider.decide(t, 40.0, LIMIT_KPH, OFFSET_KPH, false));

        // above the band, timer starts
        t += 1000;
        assertTrue(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));

        // dip back into the band before the timer expires
        t += 1500;
        assertTrue(mDecider.decide(t, 55.0, LIMIT_KPH, OFFSET_KPH, false));

        // above again: the timer restarted, so 1.5 s later we are STILL muted
        t += 500;
        assertTrue(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));
        t += 1500;
        assertTrue(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));

        // only after a full sustained window does the mute drop
        t += PslMuteDecider.HYSTERESIS_MS;
        assertFalse(mDecider.decide(t, 60.0, LIMIT_KPH, OFFSET_KPH, false));
    }

    @Test
    public void mphLimitsGoThroughTheUnitConversion()
    {
        // 65 mph limit + 5 mph offset in an imperial locale
        double limitKph  = PslMuteDecider.toKph(65, PslMuteDecider.UNIT_MPH);
        double offsetKph = PslMuteDecider.toKph(5, PslMuteDecider.UNIT_MPH);
        assertEquals(104.6, limitKph, 0.1);

        // 68 mph indicated (109.4 km/h) is under 65+5: quiet ride
        assertTrue(mDecider.decide(0, 68 * PslMuteDecider.MPH_TO_KPH,
                                   (int) Math.round(limitKph), offsetKph, false));

        // 75 mph sustained is not
        assertTrue(mDecider.decide(1000, 75 * PslMuteDecider.MPH_TO_KPH,
                                   (int) Math.round(limitKph), offsetKph, false));
        assertFalse(mDecider.decide(1000 + PslMuteDecider.HYSTERESIS_MS + 1,
                                    75 * PslMuteDecider.MPH_TO_KPH,
                                    (int) Math.round(limitKph), offsetKph, false));
    }

    @Test
    public void unknownLimitFollowsTheConfiguredBehavior()
    {
        // conservative user: never mute on unknown roads
        assertFalse(mDecider.decide(0, 30.0, null, OFFSET_KPH, false));

        // quiet user: mute whenever the limit is unknown
        assertTrue(mDecider.decide(1000, 30.0, null, OFFSET_KPH, true));

        // GPS lost entirely: never mute, drop state
        assertFalse(mDecider.decide(2000, -1.0, LIMIT_KPH, OFFSET_KPH, true));
    }

    @Test
    public void newKBandAlertIsHeldByTheBsmFilterThenReleasedAndAnnounced()
    {
        long born = 0;

        // brand new K alert while driving fast (not PSL muted)
        assertFalse(mDecider.decide(born, 80.0, LIMIT_KPH, OFFSET_KPH, false));
        assertTrue(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_K));

        int property = YaV1Alert.PROP_BSM;
        assertFalse("held alert must stay silent", wouldAnnounce(property, false));

        // young and steady: still held
        assertTrue(YaV1BsmFilter.shouldStayHeld(1000, 3, 3, false));

        // survives the hold steadily: released, and only then announced
        assertFalse(YaV1BsmFilter.shouldStayHeld(1600, 3, 4, false));
        property &= ~YaV1Alert.PROP_BSM;
        assertTrue(wouldAnnounce(property, false));
        assertEquals("K rear, 24.2",
                     YaV1Tts.buildPhrase(YaV1Alert.BAND_K, YaV1Alert.ALERT_REAR, 24171));
    }

    @Test
    public void bsmSignatureRampNeverGetsHeard()
    {
        // a passing BSM car: signal jumps 1 -> 6 LEDs while young
        assertTrue(YaV1BsmFilter.shouldStayHeld(2000, 1, 6, false));
        assertTrue(YaV1BsmFilter.shouldStayHeld(3400, 1, 6, false));
        // the alert dies before the extended hold expires: it was never
        // announced and never will be (state dropped with the alert)
    }

    @Test
    public void kaBandIsNeverDelayedByTheBsmFilter()
    {
        // Ka is always announced immediately: no BSM hold
        assertFalse(YaV1BsmFilter.shouldHoldNew(YaV1Alert.BAND_KA));
        assertTrue(wouldAnnounce(0, false));
        assertEquals("Ka front, 34.7",
                     YaV1Tts.buildPhrase(YaV1Alert.BAND_KA, YaV1Alert.ALERT_FRONT, 34700));
    }

    @Test
    public void lockedOutAlertStaysSilentUntilManuallyUnlocked()
    {
        // production sets MUTE alongside LOCKOUT (YaV1AlertProcessor.adjustFlagLockout)
        int property = YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_MUTE;
        assertFalse(wouldAnnounce(property, false));

        // manual unlock clears LOCKOUT / WHITE / MUTE
        // (YaV1AlertProcessor.resetManualLockoutProperty)
        property &= ~(YaV1Alert.PROP_WHITE | YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_MUTE);
        assertTrue(wouldAnnounce(property, false));
    }

    @Test
    public void whitelistedAlertIsNeverAnnounced()
    {
        assertFalse(wouldAnnounce(YaV1Alert.PROP_WHITE, false));
    }

    @Test
    public void appLevelMuteBeatsEverything()
    {
        assertFalse(wouldAnnounce(0, true));
    }

    @Test
    public void ttsDisabledSilencesAnnouncementsWithoutAffectingMuting()
    {
        YaV1Tts.init(null, false);
        assertFalse(YaV1Tts.isEnabled());
        assertFalse(wouldAnnounce(0, false));

        // muting decisions are independent of TTS
        assertTrue(mDecider.decide(0, 30.0, LIMIT_KPH, OFFSET_KPH, false));
    }

    @Test
    public void phrasesForEveryBandAndDirection()
    {
        assertEquals("Laser front", YaV1Tts.buildPhrase(YaV1Alert.BAND_LASER, YaV1Alert.ALERT_FRONT, 0));
        assertEquals("Ka side, 33.8", YaV1Tts.buildPhrase(YaV1Alert.BAND_KA, YaV1Alert.ALERT_SIDE, 33800));
        assertEquals("X rear, 10.5", YaV1Tts.buildPhrase(YaV1Alert.BAND_X, YaV1Alert.ALERT_REAR, 10520));
        assertEquals("Ku front, 13.4", YaV1Tts.buildPhrase(YaV1Alert.BAND_KU, YaV1Alert.ALERT_FRONT, 13400));
        // unknown band never produces a phrase
        assertEquals("", YaV1Tts.buildPhrase(99, YaV1Alert.ALERT_FRONT, 34700));
    }
}
