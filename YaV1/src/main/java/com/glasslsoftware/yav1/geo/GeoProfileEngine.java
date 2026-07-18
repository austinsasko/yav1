package com.glasslsoftware.yav1.geo;

import java.util.HashMap;
import java.util.Map;

/**
 * [P3-GEO] Rule engine for location based V1 profile switching.
 *
 * Fed with the state resolved from each GPS fix, it decides when to push a
 * mapped V1 settings profile:
 *
 *  - only on a state change (entering a state with a configured rule),
 *  - never when disconnected from a real V1 or while in demo mode,
 *  - never while the alert list is not quiet,
 *  - debounced: at least {@link #getDebounceMs()} between two pushes,
 *  - manual override wins: if the user pushes a profile by hand, automatic
 *    switching is suspended until the next state change.
 *
 * When a push cannot happen right away (alert on screen, debounce window,
 * V1 not connected yet) it stays pending and is retried on subsequent
 * position updates while still in the same state.
 *
 * All Android/V1 specifics live behind {@link Gateway} so the logic is unit
 * testable on the JVM.
 */
public class GeoProfileEngine
{
    public static final long DEFAULT_DEBOUNCE_MS = 60000L;

    /** Abstraction of the V1 push machinery and app state. */
    public interface Gateway
    {
        /** True when a real V1 is connected. */
        boolean isConnected();

        /** True while the app runs in demo mode. */
        boolean isDemo();

        /** True when no alert is currently displayed. */
        boolean isAlertQuiet();

        /**
         * Push the named profile to the V1 using the existing settings
         * machinery. Returns false when the profile does not exist or the
         * push could not be initiated.
         */
        boolean pushProfile(String profileName);

        /** Id of the profile currently on the V1, or -1 when unknown. */
        int getCurrentSettingId();

        /** Announce a completed switch to the user (TTS / toast). */
        void announce(String profileName, String stateName);

        /** Decision logging (tag "Valentine GEO"). */
        void log(String msg);
    }

    /** Time source, injectable for tests. */
    public interface Clock
    {
        long now();
    }

    private final Gateway mGateway;
    private final Clock   mClock;

    private Map<String, String> mRules = new HashMap<String, String>();

    private long    mDebounceMs        = DEFAULT_DEBOUNCE_MS;

    private boolean mHasState          = false;
    private String  mState             = null;

    private String  mPendingProfile    = null;
    private String  mPendingStateName  = null;

    private boolean mManualOverride    = false;
    private long    mLastPushTime      = 0;
    private boolean mHasPushed         = false;
    private int     mLastAutoPushedId  = -1;

    // last logged skip reason, to avoid spamming the same line on every fix
    private String  mLastSkipReason    = null;

    public GeoProfileEngine(Gateway gateway, Clock clock)
    {
        mGateway = gateway;
        mClock   = clock;
    }

    /** Replace the state code to profile name mapping. */
    public synchronized void setRules(Map<String, String> rules)
    {
        mRules = (rules != null ? rules : new HashMap<String, String>());
    }

    public long getDebounceMs()
    {
        return mDebounceMs;
    }

    public void setDebounceMs(long ms)
    {
        mDebounceMs = ms;
    }

    public synchronized String getCurrentState()
    {
        return mState;
    }

    public synchronized boolean isManualOverride()
    {
        return mManualOverride;
    }

    public synchronized boolean hasPendingPush()
    {
        return mPendingProfile != null;
    }

    /** Forget everything (feature toggled off/on). */
    public synchronized void reset()
    {
        mHasState         = false;
        mState            = null;
        mPendingProfile   = null;
        mPendingStateName = null;
        mManualOverride   = false;
        mLastSkipReason   = null;
        // keep mLastPushTime so toggling the feature cannot bypass debounce
    }

    /**
     * Feed one resolved position. stateCode/stateName may be null when the
     * position is outside every known state.
     */
    public synchronized void onState(String stateCode, String stateName)
    {
        if(!mHasState || !equals(mState, stateCode))
            onStateChange(stateCode, stateName);
        else
        {
            detectManualPush();
            trySendPending();
        }
    }

    // ------------------------------------------------------------- internal

    private void onStateChange(String stateCode, String stateName)
    {
        String from = (mHasState ? String.valueOf(mState) : "unknown");

        mHasState = true;
        mState    = stateCode;

        // a state change always clears manual override and any stale pending
        mManualOverride   = false;
        mPendingProfile   = null;
        mPendingStateName = null;
        mLastSkipReason   = null;

        if(stateCode == null)
        {
            mGateway.log("State change " + from + " -> none (no state matched), no action");
            return;
        }

        String profile = mRules.get(stateCode);

        if(profile == null || profile.length() == 0)
        {
            mGateway.log("State change " + from + " -> " + stateCode + ", no profile rule, no action");
            return;
        }

        mGateway.log("State change " + from + " -> " + stateCode
                + ", rule maps to profile '" + profile + "'");

        mPendingProfile   = profile;
        mPendingStateName = (stateName != null ? stateName : stateCode);

        trySendPending();
    }

    /**
     * If the profile on the V1 is no longer the one we pushed, the user has
     * pushed one manually: suspend automatic switching until the next state
     * change.
     */
    private void detectManualPush()
    {
        if(mManualOverride || !mHasPushed)
            return;

        int current = mGateway.getCurrentSettingId();

        if(current != -1 && mLastAutoPushedId != -1 && current != mLastAutoPushedId)
        {
            mManualOverride   = true;
            mPendingProfile   = null;
            mPendingStateName = null;
            mGateway.log("Manual profile push detected (setting id " + mLastAutoPushedId
                    + " -> " + current + "), auto switching suspended until next state change");
        }
    }

    private void trySendPending()
    {
        if(mPendingProfile == null)
            return;

        if(mManualOverride)
        {
            skip("manual override active");
            return;
        }

        if(mGateway.isDemo())
        {
            skip("demo mode");
            return;
        }

        if(!mGateway.isConnected())
        {
            skip("V1 not connected");
            return;
        }

        if(!mGateway.isAlertQuiet())
        {
            skip("alert in progress");
            return;
        }

        long now = mClock.now();

        if(mHasPushed && now - mLastPushTime < mDebounceMs)
        {
            skip("debounce (" + (mDebounceMs - (now - mLastPushTime)) + " ms left)");
            return;
        }

        String profile   = mPendingProfile;
        String stateName = mPendingStateName;

        if(mGateway.pushProfile(profile))
        {
            mPendingProfile   = null;
            mPendingStateName = null;
            mLastSkipReason   = null;
            mLastPushTime     = now;
            mHasPushed        = true;
            mLastAutoPushedId = mGateway.getCurrentSettingId();

            mGateway.log("Pushed profile '" + profile + "' for " + stateName);
            mGateway.announce(profile, stateName);
        }
        else
        {
            // profile missing or push machinery refused: don't retry forever
            mPendingProfile   = null;
            mPendingStateName = null;
            mLastSkipReason   = null;
            mGateway.log("Push of profile '" + profile + "' failed (profile missing?), rule dropped for " + stateName);
        }
    }

    /** Log a deferral reason once (not on every GPS fix). */
    private void skip(String reason)
    {
        if(!reason.equals(mLastSkipReason))
        {
            // debounce countdown changes every time; collapse it
            String key = reason.startsWith("debounce") ? "debounce" : reason;

            if(!key.equals(mLastSkipReason))
            {
                mGateway.log("Push of '" + mPendingProfile + "' deferred: " + reason);
                mLastSkipReason = key;
            }
        }
    }

    private static boolean equals(String a, String b)
    {
        return (a == null ? b == null : a.equals(b));
    }
}
