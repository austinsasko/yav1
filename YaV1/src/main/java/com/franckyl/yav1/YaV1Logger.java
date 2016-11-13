package com.franckyl.yav1;

import android.text.format.DateFormat;
import android.util.Log;

import com.franckyl.yav1.alert_histo.LoggedAlert;
import com.franckyl.yav1.utils.YaV1GpsPos;
import com.franckyl.yav1lib.YaV1Alert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by franck on 7/14/13.
 * FOR MTP In case of adb -d shell "am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///mnt/sdcard/com.franckyl.yav1"
 */

public class YaV1Logger extends Thread
{
    private boolean     mLoggerActive    = false;
    private BlockingQueue<logRec> mItems =  new ArrayBlockingQueue<logRec>(200);
    private PrintStream mLogFile         = null;

    private int         mLogFormat      = 0;
    private long        mPurgeMilli     = 0;
    private File        mFileDisk       = null;
    static  String      mDirection[]     = {"Front", "Rear", "Side"};
    static  String      mDtFormat[]      = {"yyyy-MM-dd kk:mm:ss", "yyyy-MM-dd hh:mm:ss aa"};
    public static String sLegend[]       = {
                                            "timestamp,band,frequency,direction,strength,flag,lat,lon,bearing,speed,Tn,order,delta,pxid,lockoutid",
                                            "type,latitude,longitude,name,color,flag,Tn,order,speed,bearing,timestamp,delta,pxid,lockoutid",
                                            "timestamp,band,frequency,direction,strength,flag,lat,lon,bearing,speed,Tn,order,delta",
                                            "type,latitude,longitude,name,color,flag,Tn,order,speed,bearing,timestamp,delta",
                                            "timestamp,band,frequency,direction,strength,flag,lat,lon,bearing,speed,Tn,order",
                                            "type,latitude,longitude,name,color,flag,Tn,order,speed,bearing",
                                            };
    // start logging

    public boolean startLogging()
    {
        if(!mLoggerActive)
        {
            // read the format
            mLogFormat = Integer.valueOf(YaV1.sPrefs.getString("log_format", "0"));

            int d       = Integer.valueOf(YaV1.sPrefs.getString("log_purge", "0"));
            mPurgeMilli = d * 24 * 60 * 60 * 1000;

            if(mPurgeMilli > 0)
                purgeLogFiles();

            File sDir = new File(YaV1.sStorageDir + "/" + "logs" + "/");

            if(sDir.isDirectory() || sDir.mkdirs())
            {
                // we will open file and use Gson
                try
                {
                    String fName  = (String) DateFormat.format("yyyy-MM-dd", new java.util.Date()) + "_alert." + (mLogFormat == 0 ? "csv" : "log");
                    // check if file exists
                    boolean setLegend = false;
                    mFileDisk = new File(sDir, fName);

                    if(!mFileDisk.exists())
                        setLegend = true;

                    FileOutputStream  fo = new FileOutputStream(mFileDisk, true);
                    mLogFile      = new PrintStream(fo);

                    if(setLegend)
                    {
                        if(mLogFormat == 1)
                            mLogFile.append(sLegend[0]+"\n");
                        else
                            mLogFile.append(sLegend[1]+"\n");
                    }

                    mLoggerActive = true;
                    if(!this.isAlive())
                        start();
                    Log.d("Valentine", "Logger started");
                    return true;
                }
                catch(IOException e)
                {
                    Log.d("Valentine", "Error opening log file");
                }
            }
        }

        Log.d("Valentine", "Logger already active");
        return false;
    }

    public void stopLogging()
    {
        if(mLoggerActive)
        {
            logRec rec = new logRec();
            rec.lon = Double.NaN;
            log(rec);
        }
    }

    // log from a YaV1alert

    public void log(YaV1Alert alert)
    {
        if(!mLoggerActive)
            return;

        logRec rec    = new logRec();
        rec.timestamp = System.currentTimeMillis();
        rec.band      = alert.getBand();
        rec.frequency = alert.getFrequency();
        rec.strength  = alert.getSignal();
        rec.direction = alert.getArrowDir();
        rec.property  = alert.getProperty();
        rec.tn        = alert.getTn();
        rec.order     = alert.getOrder();
        rec.delta     = alert.getDeltaSignal();

        rec.pxid      = 0;
        rec.lockoutid = 0;

        if(YaV1CurrentPosition.isValid)
        {
            rec.lat     = YaV1CurrentPosition.lat;
            rec.lon     = YaV1CurrentPosition.lon;
            rec.bearing = YaV1CurrentPosition.bearing;
            rec.speed   = YaV1CurrentPosition.speed;
        }
        else
            rec.lat = Double.NaN;

        log(rec);
    }

    // log from Dialog

    public void log(YaV1GpsPos pos, YaV1Alert alert)
    {
        if(!mLoggerActive)
            return;

        logRec rec    = new logRec();
        rec.timestamp = pos.timestamp;
        rec.band      = alert.getBand();
        rec.frequency = alert.getFrequency();
        rec.strength  = alert.getSignal();
        rec.direction = alert.getArrowDir();
        rec.property  = alert.getProperty();
        rec.tn        = alert.getTn();
        rec.order     = alert.getOrder();
        rec.delta     = alert.getDeltaSignal();

        rec.pxid      = 0;
        rec.lockoutid = 0;

        if(YaV1CurrentPosition.isValid)
        {
            rec.lat     = pos.lat;
            rec.lon     = pos.lon;
            rec.bearing = pos.bearing;
            rec.speed   = pos.speed;
        }
        else
            rec.lat = Double.NaN;

        log(rec);
    }

    // log from a Peristent alert and YaV1alert

    public void log(YaV1PersistentAlert persist, YaV1Alert alert)
    {
        if(!mLoggerActive)
            return;

        logRec rec    = new logRec();
        rec.timestamp = System.currentTimeMillis();
        rec.band      = alert.getBand();
        rec.frequency = alert.getFrequency();
        rec.strength  = alert.getSignal();
        rec.direction = alert.getArrowDir();
        rec.property  = alert.getProperty();
        rec.tn        = alert.getTn();
        rec.order     = alert.getOrder();
        rec.delta     = alert.getDeltaSignal();

        rec.pxid      = persist.mId;
        rec.lockoutid = persist.mLockoutId;

        if(YaV1CurrentPosition.isValid)
        {
            rec.lat     = YaV1CurrentPosition.lat;
            rec.lon     = YaV1CurrentPosition.lon;
            rec.bearing = YaV1CurrentPosition.bearing;
            rec.speed   = YaV1CurrentPosition.speed;
        }
        else
            rec.lat = Double.NaN;

        log(rec);
    }

    // push into the log

    public void log(logRec rec)
    {
        if(!mLoggerActive)
            return;
        try
        {
            mItems.put(rec);
        }
        catch(InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            Log.d("Valentine", "Interrupt exception in log, thrown as RuntimeException " + iex.toString());
            throw new RuntimeException("Unexpected interruption");
        }
    }

    public void run()
    {
        try
        {
            logRec  rec;
            String  s;
            String  color;
            boolean isLaser = false;
            CharSequence  dt;
            java.util.Date jv = new java.util.Date();

            while(true)
            {
                rec = mItems.take();

                // we stop when Nan is in longitude

                if(Double.isNaN(rec.lon))
                    break;

                if(mLogFormat < 1)
                {
                    isLaser = (rec.band == YaV1Alert.BAND_LASER);
                    jv.setTime(rec.timestamp);
                    dt = DateFormat.format(mDtFormat[YaV1.sCurrUnit], jv);

                    if(isLaser)
                        color = "red";
                    else if( (rec.property & YaV1Alert.PROP_LOCKOUT) > 0)
                        color = "blue";
                    else if( (rec.property & YaV1Alert.PROP_INBOX) > 0)
                        color = "cyan";
                    else if( (rec.property & YaV1Alert.PROP_PRIORITY) > 0)
                        color = "yellow";
                    else
                        color = "pink";

                    if(!Double.isNaN(rec.lat))
                    {
                        s = String.format("W,%s,%s,%s<br/>%d %s - %s<br/>%s %s %s %d,%s,%d,%d,%d,%s,%d,%d,%d,%d,%d\n",
                                            YaV1.sNF.format(rec.lat), YaV1.sNF.format(rec.lon), dt.toString(),
                                            (int) (rec.speed * YaV1.sUnits[YaV1.sCurrUnit]), YaV1.sUnitLabel[YaV1.sCurrUnit], YaV1CurrentPosition.bearingToString(rec.bearing),
                                            YaV1Alert.getBandStr(rec.band), (isLaser ? "" : Integer.valueOf(rec.frequency).toString()), mDirection[rec.direction], rec.strength,
                                            color,
                                            rec.property,
                                            rec.tn,
                                            rec.order,
                                            YaV1.sNF.format(rec.speed),
                                            rec.bearing,
                                            rec.timestamp,
                                            rec.delta,
                                            rec.pxid,
                                            rec.lockoutid
                                          );
                    }
                    else
                    {
                        s = String.format("W,,,%s<br/><br/>%s %s %s %d,%s,%d,%d,%d,,,%d,%d,%d,%d\n",
                                dt.toString(),
                                YaV1Alert.getBandStr(rec.band), (isLaser ? "" : Integer.valueOf(rec.frequency).toString()), mDirection[rec.direction], rec.strength,
                                color,
                                rec.property,
                                rec.tn,
                                rec.order,
                                rec.timestamp,
                                rec.delta,
                                rec.pxid,
                                rec.lockoutid
                        );
                    }
                }
                else
                {
                    if(!Double.isNaN(rec.lat))
                        s = String.format("%d,%s,%d,%d,%d,%d,%s,%s,%d,%s,%d,%d,%d,%d,%d\n",
                                        (long) (rec.timestamp/1000),
                                         YaV1Alert.getBandStr(rec.band),
                                         rec.frequency,
                                         rec.direction,
                                         rec.strength,
                                         rec.property,
                                         YaV1.sNF.format(rec.lat),
                                         YaV1.sNF.format(rec.lon),
                                         rec.bearing,
                                         YaV1.sNF.format(rec.speed),
                                         rec.tn,
                                         rec.order,
                                         rec.delta,
                                         rec.pxid,
                                         rec.lockoutid
                                         );
                    else
                        s = String.format("%d,%s,%d,%d,%d,%d,,,,,%d,%d,%d,%d,%d\n",
                                (long) (rec.timestamp/1000),
                                YaV1Alert.getBandStr(rec.band),
                                rec.frequency,
                                rec.direction,
                                rec.strength,
                                rec.property,
                                rec.tn,
                                rec.order,
                                rec.delta,
                                rec.pxid,
                                rec.lockoutid
                        );
                }

                // we will append in log file
                mLogFile.append(s);
                mLogFile.flush();
            }
        }
        catch (InterruptedException iex)
        {
            Log.d("Valentine", "Running logger Interruption " + iex.toString());
        }
        finally
        {
            mLoggerActive = false;
        }

        mLoggerActive = false;
        mLogFile.close();

        YaV1.forceMedia(mFileDisk);
        mLogFile = null;
        Log.d("Valentine", "Logger has been stopped");
    }

    // format a line from logged alert

    public static boolean addLine(String fileName, int format, LoggedAlert alert)
    {
        // we log from logged alert
        try
        {
            FileOutputStream fo = new FileOutputStream(fileName, true);
            PrintStream mTmpLog = new PrintStream(fo);

            // create the record from the logged alert

            logRec rec    = new logRec();
            rec.timestamp = alert.timeStamp;
            rec.band      = alert.getBand();
            rec.frequency = alert.getFrequency();
            rec.strength  = alert.getSignal();
            rec.direction = alert.getArrowDir();
            rec.property  = alert.getProperty();
            rec.tn        = alert.getTn();
            rec.order     = alert.getOrder();
            rec.delta     = alert.getDeltaSignal();

            rec.pxid      = alert.mPxId;
            rec.lockoutid = 0;

            rec.lat       = alert.lat;
            rec.lon       = alert.lon;
            rec.bearing   = alert.bearing;
            rec.speed     = alert.speed;

            // format the record to add up to the file

            String s;

            if(format < 1)
            {
                boolean isLaser = (rec.band == YaV1Alert.BAND_LASER);
                java.util.Date jv = new java.util.Date(rec.timestamp);
                CharSequence dt = DateFormat.format(mDtFormat[YaV1.sCurrUnit], jv);
                String color;

                if(isLaser)
                    color = "red";
                else if( (rec.property & YaV1Alert.PROP_LOCKOUT) > 0)
                    color = "blue";
                else if( (rec.property & YaV1Alert.PROP_INBOX) > 0)
                    color = "cyan";
                else if( (rec.property & YaV1Alert.PROP_PRIORITY) > 0)
                    color = "yellow";
                else
                    color = "pink";

                if(!Double.isNaN(rec.lat))
                {
                    s = String.format("W,%s,%s,%s<br/>%d %s - %s<br/>%s %s %s %d,%s,%d,%d,%d,%s,%d,%d,%d,%d,%d\n",
                            YaV1.sNF.format(rec.lat), YaV1.sNF.format(rec.lon), dt.toString(),
                            (int) (rec.speed * YaV1.sUnits[YaV1.sCurrUnit]), YaV1.sUnitLabel[YaV1.sCurrUnit], YaV1CurrentPosition.bearingToString(rec.bearing),
                            YaV1Alert.getBandStr(rec.band), (isLaser ? "" : Integer.valueOf(rec.frequency).toString()), mDirection[rec.direction], rec.strength,
                            color,
                            rec.property,
                            rec.tn,
                            rec.order,
                            YaV1.sNF.format(rec.speed),
                            rec.bearing,
                            rec.timestamp,
                            rec.delta,
                            rec.pxid,
                            rec.lockoutid
                    );
                }
                else
                {
                    s = String.format("W,,,%s<br/><br/>%s %s %s %d,%s,%d,%d,%d,,,%d,%d,%d,%d\n",
                            dt.toString(),
                            YaV1Alert.getBandStr(rec.band), (isLaser ? "" : Integer.valueOf(rec.frequency).toString()), mDirection[rec.direction], rec.strength,
                            color,
                            rec.property,
                            rec.tn,
                            rec.order,
                            rec.timestamp,
                            rec.delta,
                            rec.pxid,
                            rec.lockoutid
                    );
                }
            }
            else
            {
                if(!Double.isNaN(rec.lat))
                    s = String.format("%d,%s,%d,%d,%d,%d,%s,%s,%d,%s,%d,%d,%d,%d,%d\n",
                            (long) (rec.timestamp/1000),
                            YaV1Alert.getBandStr(rec.band),
                            rec.frequency,
                            rec.direction,
                            rec.strength,
                            rec.property,
                            YaV1.sNF.format(rec.lat),
                            YaV1.sNF.format(rec.lon),
                            rec.bearing,
                            YaV1.sNF.format(rec.speed),
                            rec.tn,
                            rec.order,
                            rec.delta,
                            rec.pxid,
                            rec.lockoutid
                    );
                else
                    s = String.format("%d,%s,%d,%d,%d,%d,,,,,%d,%d,%d,%d,%d\n",
                            (long) (rec.timestamp/1000),
                            YaV1Alert.getBandStr(rec.band),
                            rec.frequency,
                            rec.direction,
                            rec.strength,
                            rec.property,
                            rec.tn,
                            rec.order,
                            rec.delta,
                            rec.pxid,
                            rec.lockoutid
                    );
            }

            // we will append in log file
            mTmpLog.append(s);
            mTmpLog.flush();

            // close and exit
            mTmpLog.close();
            return true;
        }
        catch(FileNotFoundException e)
        {

        }
        catch(IOException i)
        {

        }

        return false;
    }

    public boolean isActive()
    {
        return mLoggerActive;
    }

    private void purgeLogFiles()
    {
        File dir = new File(YaV1.sStorageDir + "/" + "logs" + "/");
        String s;

        if(dir.isDirectory())
        {
            File[] files = dir.listFiles();
            for(int i = 0; i < files.length; ++i)
            {
                s = files[i].getName();
                if(!s.matches("[\\d]{4}-[\\d]{2}-[\\d]{2}_alert\\.(csv|log)"))
                    continue;
                // check if it's a log file
                //Log.d("Valentine", "File " + files[i].getName());
                long lastTime = files[i].lastModified();
                Date nowDate = new Date();
                long nowTime = nowDate.getTime();
                if (nowTime - lastTime > mPurgeMilli)
                {
                    //Log.d("Valentine", "File " + s + " tool old deleted");
                    files[i].delete();
                }
            }
        }
    }

    private static class logRec
    {
        long    timestamp;
        int     band;
        int     frequency;
        int     strength;
        int     direction;
        int     property;
        double  lat;
        double  lon;
        int     bearing;
        double  speed;
        int     tn;
        int     order;
        int     delta;
        int     pxid;
        int     lockoutid;
    }
}
