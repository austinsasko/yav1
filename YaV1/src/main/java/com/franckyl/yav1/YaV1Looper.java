package com.franckyl.yav1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Created by franck on 2/21/14.
 */
public class YaV1Looper extends Thread
{
    private Handler mHandler  = null;
    private Looper  mLooper   = null;
    private boolean mRunning = false;

    public YaV1Looper()
    {
        start();
        // wait to be finished ?
        while(mLooper == null)
        {
            try
            {
                sleep(100);
            }
            catch(InterruptedException ex)
            {
                Log.d("Valentine", "Looper waiting interrupted");
                return;
            }
        }
        mHandler = new Handler(mLooper);
        Log.d("Valentine", "looper started");
    }

    @Override
    public void run()
    {
        Looper.prepare();
        mLooper = Looper.myLooper();
        /*
        // wait to be finished ?
        while(mLooper == null)
        {
            try
            {
                sleep(100);
            }
            catch(InterruptedException ex)
            {
                Log.d("Valentine", "Looper waiting interrupted");
                return;
            }
        }
        mHandler = new Handler(mLooper);
        Log.d("Valentine", "looper started");
        */
        mRunning = true;
        Looper.loop();
        mRunning = false;
    }

    public boolean isRunning()
    {
        return mRunning;
    }

    // stop looping

    public void stopLoop()
    {
        if(mLooper != null)
            mLooper.quit();
        Log.d("Valentine", "looper stop");
    }

    public Handler getHandler()
    {
        if(mHandler == null)
            mHandler = new Handler(mLooper);

        return mHandler;
    }

    // get the Looper

    public Looper getUtilLooper()
    {
        return mLooper;
    }
}
