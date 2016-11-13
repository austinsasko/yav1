package com.franckyl.yav1.lockout;

import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import com.franckyl.yav1.R;
import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.YaV1PersistentAlert;

/**
 * Created by franck on 2/27/14.
 */

public class Lockout
{
    // some static members

    public  static final  int LOCKOUT_NEW          = 1;
    public  static final  int LOCKOUT_UPDATE       = 2;
    public  static final  int LOCKOUT_REMOVE       = 4;
    public  static final  int LOCKOUT_MANUAL       = 8;
    public  static final  int LOCKOUT_RESETFLAG    = 16;
    public  static final  int LOCKOUT_TMP_KEEP     = 32;
    public  static final  int LOCKOUT_WHITE        = 64;

    // easier usage

    public  static final  int LOCKOUT_CHECK_MANUAL = (LOCKOUT_MANUAL | LOCKOUT_WHITE);

    public  static final  int sKRange[]            = {24030, 24250};
    public  static final  int MANUAL_LOCKOUT_COUNT = 20;

    private int    mId;
    public  int    mFrequency;
    public  int    mSeen;
    public  int    mMissed;
    public  int    mFlag;
    public  int    mBearing;
    public  int    mParam1;
    public  int    mParam2;

    public  int    mShouldBe;
    public  int    mLocalSeen;
    public  int    mNbCheck;
    public  int    mAngle;
    public  int    mDistance;

    public  double mLat;
    public  double mLon;

    // public  double mSignature;
    public  float    mSpeed;

    // if this lockout is taken
    public  int mBusy = 0;

    // front signal / rear signal
    public int  mFs;
    public int  mRs;

    public  int     mUpdateFlag = 0;
    public  long    mTimeStamp;

    // default constructor

    public Lockout(int id)
    {
        mId        = id;
        mFrequency = 0;
        mSeen      = 0;
        mMissed    = 0;
        mFlag      = 0;
        mBearing   = 0;
        mSpeed     = 0;
        mParam1    = 0;
        mParam2    = 0;
        mShouldBe  = 0;
        mLocalSeen = 0;

        mAngle     = 0;
        mDistance  = 0;
        mNbCheck   = 0;
    }

    // constructor from Db read

    public Lockout(int id, int flag, double lat, double lon, int bearing, int frequency, float speed, int seen,
                   int missed, int param1, int param2, long timestamp)
    {
        mId        = id;
        mFrequency = frequency;
        mSeen      = seen;
        mMissed    = missed;
        mFlag      = flag;
        mBearing   = bearing;
        mSpeed     = speed;
        mLat       = lat;
        mLon       = lon;
        mBusy      = 0;
        mParam1    = param1;
        mParam2    = param2;

        mBusy      = 0;
        mShouldBe  = 0;
        mLocalSeen = 0;
        mAngle     = 0;
        mDistance  = 0;
        mNbCheck   = 0;
        mTimeStamp = timestamp;
    }

    // constructor from current frequency and position

    public Lockout(int id, YaV1PersistentAlert a)
    {
        mId        = id;
        mFrequency = a.mFrequency;
        mSeen      = 1;
        mMissed    = 0;
        mFlag      = (LOCKOUT_NEW | LOCKOUT_UPDATE);
        mBearing   = a.mInitialPosition.bearing;
        mSpeed     = (float) a.mInitialPosition.speed;
        mLat       = a.mInitialPosition.lat;
        mLon       = a.mInitialPosition.lon;
        mBusy      = 1;
        mParam1    = a.mSignal;

        mParam2    = Math.max(a.mPreviousCap, a.mNextCap);
        mShouldBe  = 1;
        mLocalSeen = 1;
        mNbCheck   = 1;

        // compute the distance and Angle
        mAngle     = YaV1CurrentPosition.getAngle(a.mInitialPosition.bearing, mBearing);
        Location.distanceBetween(a.mInitialPosition.lat, a.mInitialPosition.lon, mLat, mLon, LockoutArea.sResults);
        mDistance  = (int) LockoutArea.sResults[0];
        mTimeStamp = System.currentTimeMillis()/1000;
    }

    // reset some values when adding the lockout in the current area

    public void resetOnEnterCurrentArea(int sB, int angle, int distance)
    {
        mFs               = getFrontSignal(mParam1);
        mRs               = getRearSignal(mParam1);

        // update the distance / angle
        mAngle    = angle;
        mDistance = distance;

        // the lockout might still be busy (we leave it this way)

        if(mBusy > 0)
        {
            mShouldBe += sB;
            mNbCheck++;
        }
        else
        {
            mLocalSeen = 0;
            mShouldBe  = sB;
            mLocalSeen = 0;
            mNbCheck   = 1;
        }
    }

    // get the max Signal

    public int getMaxSignal()
    {
        return Math.max(getFrontSignal(mParam1), getRearSignal(mParam1));

    }

    // reset the flag before update

    public void resetFlag()
    {
        mFlag             = (mFlag & (~(LOCKOUT_NEW | LOCKOUT_UPDATE | LOCKOUT_REMOVE)));
    }

    // is in K range

    private boolean isInKRange()
    {
        return mFrequency >= sKRange[0] && mFrequency <= sKRange[1];
    }

    // reset busy out of current area

    public void resetBusyNotInArea()
    {
        mBusy       = 0;
        mShouldBe   = 0;
        mLocalSeen  = 0;
        mNbCheck    = 0;
    }

    // reset busy in current area

    public void resetBusyInArea()
    {
        mBusy       = 0;
    }

    // flag the reset when out of area

    public void setResetOnOut()
    {
        mFlag |= LOCKOUT_RESETFLAG;
    }

    public void resetOnOut()
    {
        mBusy       = 0;
        mShouldBe   = 0;
        mLocalSeen  = 0;
        mNbCheck    = 0;

        mFlag       = mFlag & (~(LOCKOUT_RESETFLAG));
    }

    // check if the lockout need update / remove

    // public boolean checkForUpdate(boolean gpsLost, boolean hadEuroLogic)
    public boolean checkForUpdate()
    {
        boolean rc = (mFlag & LOCKOUT_UPDATE) > 0;

        if(!rc)
        {
            if(mLocalSeen < 1 && mShouldBe > 0)
            {
                int sRatio = (mNbCheck < 1 ? 0 : (int) (mShouldBe / mNbCheck * 100));

                // increment the missing count
                ++mMissed;
                int limit = LockoutParam.mMaxUnseen;
                if( (mFlag & LOCKOUT_MANUAL) > 0)
                    limit = LockoutParam.mMaxManualUnseen;
                else if( (mFlag & LOCKOUT_WHITE) > 0)
                {
                    if(LockoutParam.mMaxWhiteUnseen > 0)
                        limit = LockoutParam.mMaxWhiteUnseen;
                    else
                        limit = mMissed+1;
                }

                if(mMissed >= limit)
                {
                    mFlag |= LOCKOUT_REMOVE;
                }
                else
                {
                    mFlag |= LOCKOUT_UPDATE;
                }
                rc = true;
            }
        }

        // shall we reset the busy flag
        if( (mFlag & LOCKOUT_RESETFLAG) > 0)
        {
            resetOnOut();
        }

        return rc;
    }

    // force a lockout

    public void forceLockout()
    {
        mSeen   = MANUAL_LOCKOUT_COUNT;
        mMissed = 0;
        mFlag   = (mFlag & ~LOCKOUT_WHITE);
        mFlag  |= (LOCKOUT_UPDATE | LOCKOUT_MANUAL);
    }

    // force white

    public void forceWhite()
    {
        mSeen   = MANUAL_LOCKOUT_COUNT;
        mMissed = 0;
        mFlag   = (mFlag & ~LOCKOUT_MANUAL);
        mFlag  |= (LOCKOUT_UPDATE | LOCKOUT_WHITE);
    }

    // static function that reverse the signal param (param1)

    public static int reverseParamSignal(int p)
    {
        return (p % 10) * 10 + (int) Math.floor(p/10);
    }

    // get the front signal from param1

    public static int getFrontSignal(int param1)
    {
        return (int) Math.floor(param1/10) - 1;
    }

    // return the rear signal

    public static int getRearSignal(int param1)
    {
        return (param1 % 10) - 1;
    }

    public int getId()
    {
        return mId;
    }

    // get the status String used in map

    public String getStatusString()
    {
        if( (mFlag & LOCKOUT_MANUAL) > 0)
            return YaV1.sContext.getString(R.string.lockout_status_lockout_manual);
        else if( (mFlag & LOCKOUT_WHITE) > 0)
            return YaV1.sContext.getString(R.string.lockout_status_white);
        else if(mSeen >= LockoutParam.mMinSeen)
            return YaV1.sContext.getString(R.string.lockout_status_lockout);
        else
            return YaV1.sContext.getString(R.string.lockout_status_learning);
    }

    // get the status color

    public int getStatusColor()
    {
        if( (mFlag & LOCKOUT_MANUAL) > 0)
            return LockoutParam.mManualLockoutColor;
        else if( (mFlag & LOCKOUT_WHITE) > 0)
            return LockoutParam.mWhiteLockoutColor;
        else if(mSeen >= LockoutParam.mMinSeen)
            return LockoutParam.mNearLockoutColor;
        else
            return LockoutParam.mLockoutColor;
    }
}
