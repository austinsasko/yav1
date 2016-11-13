package com.franckyl.yav1;

import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import com.franckyl.yav1.utils.YaV1GpsPos;
import com.franckyl.yav1lib.YaV1Alert;

/**
 * Created by franck on 2/24/14.
 */
public class YaV1PersistentAlert
{
    public static final int PERSISTENT_ACTIVE    = 1;
    public static final int PERSISTENT_CANDIDATE = 2;
    public static final int PERSISTENT_CLOSED    = 4;

    public static final int RESILIENT_PROPERTY   = YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_PRIORITY | YaV1Alert.PROP_INBOX;

    // some values used for comparing

    public static final int THRESHOLD_BAND[]  = {0, 6, 6, 2, 6};
    public static final int PERSISTENT_TTL    = 10000;

    public static int       mRefId            = 0;
    // members

    public long   mFirstTime                  = 0;
    public long   mLastTime                   = 0;
    public int    mFlag                       = 0;
    public int    mBand                       = 0;
    public int    mFrequency                  = 0;
    public int    mRefFrequency               = 0;
    public int    mOrder                      = 0;
    // public int    mAreaId                     = 0;
    public int    mLockoutId                  = 0;
    public int    mNbHit                      = 0;
    //public double mSignature                  = 0;
    public int    mPersistentProperty         = 0;
    public int    mColor;
    public int    mTextColor;
    public int    mBoxNumber                  = -1;
    public int    mDisplayProperty            = 0;
    // not used for now, we keep the variable for a future usage
    //public int    mMinMaxDrift                = Integer.MAX_VALUE;
    //public int    mMinMaxDrift                = 0;
    //public int    mMinFrequency;
    //public int    mMaxFrequency;
    public int    mSignal;
    public int    mId                          = 0;

    public int    mMissed                      = 0;
    public int    mLastSignal;
    public int    mLastArrow;

    public int     mPreviousCap                 = -1;
    public int     mNextCap                     = -1;
    public int     mAreaRevision                = 0;

    // the initial position of the alert
    public YaV1GpsPos mInitialPosition;

    // a matching alert candidate class

    // public List<CandidateAlert> mCandidateList= new ArrayList<CandidateAlert>();

    //
    // constructor
    //

    public YaV1PersistentAlert(YaV1Alert a, int prop)
    {
        mBand         = a.getBand();
        //mMinFrequency = mMaxFrequency = mFrequency  = a.getFrequency();
        mRefFrequency = mFrequency  = a.getFrequency();
        mOrder        = a.getOrder();
        mNbHit        = 1;
        mFirstTime    = mLastTime = SystemClock.elapsedRealtime();
        // signal combination
        mSignal       = (a.getFrontSignal() + 1)*10 + a.getRearSignal()+1;

        mLastArrow    = a.getArrowDir();
        mLastSignal   = a.getSignal();

        // suppose active
        mFlag         = PERSISTENT_ACTIVE;

        // compute display property
        mDisplayProperty    = mOrder * 10000000 + mFrequency * 100 + a.getSignal() * 10 + a.getArrowDir() + 1;
        mInitialPosition    = YaV1CurrentPosition.getPos();
        // mSignature          = 0;
        mPersistentProperty = prop;
        mLockoutId          = 0;
        mColor              = Color.TRANSPARENT;
        mTextColor          = Color.TRANSPARENT;

        // unique id during the session
        mId                 = ++mRefId;
    }

    // check for previous Cap
    /*
    public void checkPreviousCap()
    {
        mPreviousCap = YaV1.sAutoLockout.getLastCap(mInitialPosition);
        mAreaRevision = YaV1.sAutoLockout.mCurrentAreaRevision;

        Log.d("Valentine Lockout", "Persistent id " + mId + " lockout id " + mLockoutId + " Init Cap " + mInitialPosition.bearing + " Previous cap " + mPreviousCap + " Current area " + mAreaRevision);
    }

    // check for next cap

    public void checkNexCap()
    {
        mNextCap = YaV1.sAutoLockout.getLastCap(mInitialPosition);
        Log.d("Valentine Lockout", "Persistent id " + mId + " lockout id " + mLockoutId + " Init Cap " + mInitialPosition.bearing + " Previous cap " + mPreviousCap + " Next cap " + mNextCap);
    }
    */
    // reset this object to be candidate

    public void resetCandidate(long lastCall)
    {
        if(mLastTime + YaV1PersistentAlert.PERSISTENT_TTL < lastCall)
            mFlag = PERSISTENT_CLOSED;
        else
            mFlag |= PERSISTENT_CANDIDATE;

        //mCandidateList.clear();
    }

    // check if candidate Alert

    public boolean isCandidate(YaV1Alert a)
    {
        if( (mFlag & PERSISTENT_CANDIDATE) > 0 && a.getBand() == mBand && Math.abs(a.getFrequency() - mRefFrequency) <= THRESHOLD_BAND[mBand])
            return true;
        else
            return false;
    }

    // check if same alert

    public int isSame(YaV1Alert a)
    {
        int  m  = a.getFrequency();
        long l  = SystemClock.elapsedRealtime();
        int  rc = 0;

        //if(a.getBand() == mBand && Math.abs(m-mFrequency) <= THRESHOLD_BAND[mBand] && mLastTime + PERSISTENT_TTL > l)
        if(a.getBand() == mBand && Math.abs(m-mRefFrequency) <= THRESHOLD_BAND[mBand] && mLastTime + PERSISTENT_TTL > l)
        {
            mRefFrequency = m;
            mLastTime     = SystemClock.elapsedRealtime();
            mOrder        = a.getOrder();
            mFlag        |= PERSISTENT_ACTIVE;
            mFlag         = mFlag & (~(PERSISTENT_CANDIDATE));
            mLastArrow    = a.getArrowDir();
            mLastSignal   = a.getSignal();

            int n = a.getOrder() * 10000000 + a.getFrequency() * 100 + a.getSignal() * 10 + a.getArrowDir() + 1;

            if(n != mDisplayProperty)
            {
                mDisplayProperty = n;
                rc = 1;
            }
            ++mNbHit;
            ++rc;
            mMissed = 0;
        }

        return rc;
    }

    // check if we should close the alert

    public boolean shouldClose(long l)
    {
        if( (mFlag & PERSISTENT_CANDIDATE) > 0 && mLastTime + PERSISTENT_TTL < l)
            return true;

        return false;
    }
}
