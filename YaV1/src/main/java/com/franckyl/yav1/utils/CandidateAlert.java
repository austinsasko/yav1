package com.franckyl.yav1.utils;

import com.franckyl.yav1.YaV1PersistentAlert;
import com.franckyl.yav1lib.YaV1Alert;

import java.util.Comparator;
import java.util.List;

/**
 * Created by franck on 9/3/14.
 */
public class CandidateAlert
{
    public int mDrift           = 0;
    public int mSignalDrift     = 0;
    public int mDirectionDrift  = 0;
    public int mAlertIndex      = 0;
    public int mMissed          = 0;
    public int mHistoId         = 0;

    // static comparator

    public CandidateAlert(YaV1Alert a, int alertIndex, YaV1PersistentAlert px)
    {
        mDrift          = Math.abs(a.getFrequency() - px.mRefFrequency);
        mSignalDrift    = Math.abs(a.getSignal() - px.mLastSignal);

        if(mSignalDrift <= 1)
            mSignalDrift = 0;

        mDirectionDrift = Math.abs(a.getArrowDir() - px.mLastArrow);
        if(mDirectionDrift == 1)
            mDirectionDrift = 0;
        mAlertIndex     = alertIndex;
        mHistoId        = px.mId;
        mMissed         = px.mMissed;
    }

    public static int baseCompare(CandidateAlert a, CandidateAlert b)
    {
        int r = a.mDrift - b.mDrift;
        if(r == 0)
        {
            r = a.mDirectionDrift - b.mDirectionDrift;
            if(r == 0)
            {
                r = a.mSignalDrift - b.mSignalDrift;
            }
        }

        return r;
    }

    public static Comparator<CandidateAlert> mCompareAlert = new Comparator<CandidateAlert>()
    {
        @Override
        public int compare(CandidateAlert lhs, CandidateAlert rhs)
        {
            int r = baseCompare(lhs, rhs);

            if(r == 0)
            {
                r = lhs.mMissed - rhs.mMissed;

                if(r == 0)
                    r = lhs.mHistoId - rhs.mHistoId;
            }

            return r;
        }
    };

    public static Comparator<CandidateAlert> mCompareHisto = new Comparator<CandidateAlert>()
    {
        @Override
        public int compare(CandidateAlert lhs, CandidateAlert rhs)
        {
            int r = baseCompare(lhs, rhs);

            if(r == 0)
                r = lhs.mAlertIndex - rhs.mAlertIndex;

            return r;
        }
    };

    public static void dumpMatch(YaV1Alert a, List<CandidateAlert> l)
    {

    }
}
