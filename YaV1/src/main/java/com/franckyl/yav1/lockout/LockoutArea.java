package com.franckyl.yav1.lockout;

import android.util.Log;
import android.util.SparseArray;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.YaV1PersistentAlert;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by franck on 2/27/14.
 */
public class LockoutArea extends SparseArray<Lockout>
{
    private List<Candidate> mCandidates   = new ArrayList<Candidate>();

    public  static final    int   DISTANCE_DIVIDER = 10;


    public  static int      mSignalThreshold[]     = {8, 4, 2};
    public  static float    sResults[]             = {0, 0, 0};

    // internal class for candidate

    private static class Candidate
    {
        public int drift;
        public int id;
        public int sig;
        public int seen;
        public int missed;
        public int manual;
        public int distance;
        public int angle;
        public int distanceR;
    };

    // our no direction comparator, first smallest drift then max seen

    private static Comparator<Candidate> compareNoDistance = new Comparator<Candidate>()
    {
        public int compare(Candidate c1, Candidate c2)
        {
            int r = c2.seen - c1.seen;
            if(r == 0)
            {
                r = c1.missed - c2.missed;

                if(r == 0)
                {
                    r = c1.distance - c2.distance;

                    if(r == 0)
                    {
                        r = c1.drift - c2.drift;
                    }
                }
            }
            return r;
        }
    };

    // our no direction comparator, first smallest drift then max seen

    private static Comparator<Candidate> compareDistance = new Comparator<Candidate>()
    {
        public int compare(Candidate c1, Candidate c2)
        {
            int r = c1.distanceR - c2.distanceR;

            if(r == 0)
            {
                r = c2.seen - c1.seen;
                if(r == 0)
                {
                    r = c1.missed - c2.missed;
                    if(r == 0)
                    {
                        //r = c1.angle - c2.angle;

                        //if(r == 0)
                            r = c1.id - c2.id;
                    }
                }
            }
            return r;
        }
    };

    // constructor

    public LockoutArea()
    {
        super(100);
    }

    // search with no direction

    public boolean searchNoDirection(YaV1PersistentAlert a, boolean manual)
    {
        boolean rc = false;
        Lockout l;
        Candidate nA;
        int       nb;

        mCandidates.clear();
        int     f       = a.mFrequency;
        int     diff;

        int     fs        = Lockout.getFrontSignal(a.mSignal);
        int     rs        = Lockout.getRearSignal(a.mSignal);
        int     maxSignal = Math.max(fs, rs);
        int     sSignal;

        int lAngle = LockoutData.sAngle;

        for(int i=0; i < size(); i++)
        {
            l = valueAt(i);

            if(l == null)
                continue;

            if(l.mParam2 >= 25)
                lAngle = 180;
            else
                lAngle = YaV1.sAutoLockout.sAngle;

            if(l.mBusy > 0 || (manual && (l.mFlag & l.LOCKOUT_CHECK_MANUAL) < 1) || l.mAngle > lAngle)
            {
                continue;
            }

            // check if the direction matches

            if((diff = Math.abs(l.mFrequency - f)) <= LockoutParam.mDrift[a.mBand] && l.mAngle <= lAngle)
            {
                sSignal = Math.max(l.mFs, l.mRs);
                if(LockoutParam.mUseSignalFilter > 0)
                {
                    if(Math.abs(maxSignal - sSignal) > LockoutParam.mCurrentSignalThreshold)
                    {
                        continue;
                    }
                }

                nA = new Candidate();
                nA.drift     = diff;
                nA.id        = l.getId();
                nA.seen      = l.mSeen;
                nA.missed    = l.mMissed;
                nA.distance  = l.mDistance;
                nA.distanceR = (int) (l.mDistance / DISTANCE_DIVIDER);
                nA.angle     = l.mAngle;
                //nA.sig       = Math.abs(l.mParam1 - a.mSignal);
                nA.sig       = Math.abs(sSignal - maxSignal);
                nA.manual    = (l.mFlag & l.LOCKOUT_CHECK_MANUAL);

                mCandidates.add(nA);
            }
        }

        if((nb = mCandidates.size()) > 0)
        {
            if(nb > 1)
            {
                Collections.sort(mCandidates, compareDistance);
            }

            // get the first id
            int match = mCandidates.get(0).id;

            // find in the list
            l = this.get(match);

            // set busy
            l.mBusy = 1;
            l.mLocalSeen++;

            a.mLockoutId = l.getId();

            l.mSeen++;

            // check the seen number compare to mMinSeen

            if(l.mSeen >= LockoutParam.mMinSeen)
            {
                // set property to lockout
                if((l.mFlag & l.LOCKOUT_WHITE) > 0)
                {
                    a.mPersistentProperty |= YaV1Alert.PROP_WHITE;
                    a.mColor = LockoutParam.mWhiteLockoutColor;
                }
                else
                {
                    a.mPersistentProperty |= YaV1Alert.PROP_LOCKOUT;

                    if((l.mFlag & l.LOCKOUT_MANUAL) > 0)
                        a.mColor = LockoutParam.mManualLockoutColor;
                    else
                        a.mColor = LockoutParam.mLockoutColor;
                }
            }
            else if(LockoutParam.mLearningSeen > 0 && l.mSeen >= LockoutParam.mLearningSeen)
            {
                a.mColor = LockoutParam.mNearLockoutColor;
            }

            if( LockoutData.mMode != LockoutData.MODE_FROZEN)
            {
                l.mMissed = 0;
                l.mFlag |= l.LOCKOUT_UPDATE;
            }

            rc = true;
        }

        return rc;
    }
}
