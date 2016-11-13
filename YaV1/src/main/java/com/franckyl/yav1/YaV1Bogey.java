/**
 * Created by franck on 9/8/13.
 */

package com.franckyl.yav1;

import android.util.Log;
import android.util.SparseArray;

public class YaV1Bogey
{
    static final int mDot[] = {R.drawable.no_dot, R.drawable.red_dot};
    static final int mImg[] = {R.drawable.bogey_,
                               // 1 - 3
                               R.drawable.bogey_0,  R.drawable.bogey_1, R.drawable.bogey_2,
                               // 4 - 6
                               R.drawable.bogey_3,  R.drawable.bogey_4, R.drawable.bogey_5,
                               // 7 - 9
                               R.drawable.bogey_6,  R.drawable.bogey_7, R.drawable.bogey_8,
                               // 10 - 12
                               R.drawable.bogey_9,  R.drawable.bogey_a, R.drawable.bogey_b,
                               // 13 - 15
                               R.drawable.bogey_cc, R.drawable.bogey_c, R.drawable.bogey_dd,
                               // 16 - 18
                               R.drawable.bogey_d,  R.drawable.bogey_e, R.drawable.bogey_f,
                               // 19 - 21
                               R.drawable.bogey_j,  R.drawable.bogey_l, R.drawable.bogey_ll,
                               // 22 - 24
                               R.drawable.bogey_u,  R.drawable.bogey_uu, R.drawable.bogey_3l};

    private byte mBog = 0;

    public YaV1Bogey()
    {
    }

    public void setBogey(byte bog)
    {
        mBog = bog;
    }

    // get the dot

    public int getDot()
    {

        if ((mBog & 128) > 0)
            return mDot[1];

        return mDot[0];
    }

    // check if it's a bogey for mode

    public static boolean isModeBogey(byte bog)
    {
        int l  = (byte) (bog & 0x7f);

        if(l == 119 || l == 124 || l == 57 || l == 88 || l == 63 || l == 63 || l == 34 || l == 113 ||
           l == 24 || l == 56 || l == 28 || l == 62)
            return true;
        return false;
    }

    // return Image id for the Bogey (without dot)

    public int getImageNotDot()
    {
        int l  = (byte) (mBog & 0x7f);

        if(l == 0)
            return mImg[0];
        else if(l == 63)
            // 0
            return mImg[1];
        else if(l == 6)
            // 1
           return mImg[2];
        else if(l == 91)
            // 2
            return mImg[3];
        else if(l == 79)
            // 3
            return mImg[4];
        else if(l == 102)
            // 4
            return mImg[5];
        else if(l == 109)
            // 5
            return mImg[6];
        else if(l == 125)
            // 6
            return mImg[7];
        else if(l == 7)
            // 7
            return mImg[8];
        else if(l == 127)
            // 8
            return mImg[9];
        else if(l == 111)
            // 9
            return mImg[10];
        else if(l == 119)
            // A
            return mImg[11];
        else if(l == 124)
            // b
            return mImg[12];
        else if(l == 57)
            // C
            return mImg[13];
        else if(l == 88)
            // c
            return mImg[14];
        else if(l == 63)
            // D
            return mImg[15];
        else if(l == 94)
            // d
            return mImg[16];
        else if(l == 121)
            // E
            return mImg[17];
        else if(l == 113)
            // F
            return mImg[18];
        else if(l == 14)
            // J
            return mImg[19];
        else if(l == 24)
            // l
            return mImg[20];
        else if(l == 56)
            // L
            return mImg[21];
        else if(l == 28)
            // u
            return mImg[22];
        else if(l == 62)
            // U
            return mImg[23];
        else if(l ==  73)
            // 3 lines
            return mImg[24];

        return mImg[0];
    }

    private boolean isSegA()
    {
        return ( (mBog & 1) > 0 ? true : false);

    }

    private boolean isSegB()
    {
        return ( (mBog & 2 ) > 0 ? true : false);

    }

    private boolean isSegC()
    {
        return ( (mBog & 4) > 0 ? true : false);

    }

    private boolean isSegD()
    {
        return ( (mBog & 8)  > 0? true : false);

    }

    private boolean isSegE()
    {
        return ( (mBog & 16) > 0 ? true : false);

    }

    private boolean isSegF()
    {
        return ( (mBog & 32) > 0 ? true : false);

    }

    private boolean isSegG()
    {
        return ( (mBog & 64) > 0 ? true : false);

    }
}

