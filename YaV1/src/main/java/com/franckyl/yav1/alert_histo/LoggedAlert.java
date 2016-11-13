package com.franckyl.yav1.alert_histo;

import android.util.Log;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1lib.YaV1Alert;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by franck on 1/29/14.
*/

public class LoggedAlert extends YaV1Alert
{
    public static final int LGA_INVALID  = 1;
    public static final int LGA_MARK_IT  = 2;
    public static final int LGA_STANDARD = 4;
    public static final int LGA_GPSLOST  = 8;
    public static final int LGA_GPSGAIN  = 16;
    public static final int LGA_RECORDED = 32;

    public static final int COLLECTED    = PROP_FALSE | PROP_TRUE |
                                           PROP_MOVING | PROP_STATIC |
                                           PROP_IO;

    public static String sAlertDir[]     = {"Front", "Rear", "Side"};

    // used for recognition

    private static Pattern          sPattern[] = {null, null};
    public  static SimpleDateFormat sDateFmt[] = {new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), new SimpleDateFormat("yyyy-MM-dd hh:mm:ss aa")};

    // member variables

    public long   timeStamp;
    public double lat;
    public double lon;
    public double speed;
    public int    bearing;
    public int    mOptions;
    public int    mNbMissed;
    public int    mPxId;
    // marker on map

    private Marker        mMarker        = null;
    private MarkerOptions mMarkerOptions = null;

    // constructor

    public LoggedAlert()
    {
        mOptions = 0;
        mNbMissed = 0;
        mPxId     = 0;

        if(sPattern[0] == null)
        {
            sPattern[0] = Pattern.compile("(LASER|K|Ka|X|Ku)\\s(\\d+)\\s(Front|Side|Rear)\\s(\\d)");
            sPattern[1] = Pattern.compile("(LASER)\\s+(Front|Side|Rear)\\s+(\\d)");
        }
    }

    // set the option

    public void setOption(int i)
    {
        mOptions |= i;
    }

    // check options

    public boolean isOption(int i)
    {
        return (mOptions & i) > 0;
    }

    // number of missed packet

    public void setMissed(int nb)
    {
        mNbMissed = nb;
    }

    // get the color index

    public int getColorIndex()
    {
        if(getBand() == 0)
            return 0;
        int prop = getProperty();
        if( (prop & PROP_LOCKOUT) > 0)
            return 1;
        if( (prop & PROP_INBOX) > 0)
            return 2;

        return 3;
    }

    // base marker

    public void setBaseMark()
    {
        mOptions |= (LGA_STANDARD | LGA_MARK_IT);
    }

    // is valid ?

    public boolean isValid()
    {
        return ( mOptions & LGA_INVALID) < 1;
    }

    public boolean gpsLost()
    {
        return ( mOptions & LGA_GPSLOST) > 0;
    }

    public boolean gpsGain()
    {
        return ( mOptions & LGA_GPSGAIN) > 0;
    }

    // need marker ?

    public boolean isMarked()
    {
        if(AlertHistoryActivity.getCurrentMode() == AlertHistoryActivity.MODE_ALL)
            return isValid();

        return isBasedMarked();
    }

    // need marker ?

    public boolean isBasedMarked()
    {
        return ( mOptions & (LGA_MARK_IT | LGA_STANDARD)) > 0;
    }

    public boolean needOptions()
    {
        return mMarkerOptions == null;

    }

    public boolean needMarker()
    {
        return mMarker == null;
    }

    //set the marker visible or not

    public void setMarkerVisible(boolean v)
    {
        if(mMarker != null)
            mMarker.setVisible(v);
    }

    // set the Marker

    public void setMarkerOptions(MarkerOptions m)
    {
        mMarkerOptions = m;
    }

    // get the marker options

    public MarkerOptions getMarkerOptions()
    {
        return mMarkerOptions;
    }

    // store the marker

    public void setMarker(Marker m)
    {
        mMarker = m;
    }

    // get the marker

    public Marker getMarker()
    {
        return mMarker;
    }

    // remove the marker (hide it)

    public void removeMark()
    {
        if(mMarker != null)
        {
            Log.d("Valentine", "Hiding marker");
            mMarker.setVisible(false);
        }
    }

    // create an alert from a csv record

    public static LoggedAlert createFromCsv(String str)
    {
        List<String> items;
        List<String> parts;

        str = str.replaceAll(",,", ", ,");
        items = Arrays.asList(str.split(","));

        if(items.size() < 9)
        {
            Log.d("Valentine", "Invalid number of Item " + items.size() + " Str " + str);
            return null;
        }

        LoggedAlert  alert = new LoggedAlert();

        try
        {
            // got Gps ?
            if(items.get(1).trim().isEmpty())
                alert.setOption(LGA_INVALID);
            else
            {
                try
                {
                    alert.lat     = YaV1.sNF.parse(items.get(1)).doubleValue();
                    alert.lon     = YaV1.sNF.parse(items.get(2)).doubleValue();
                    alert.speed   = YaV1.sNF.parse(items.get(8)).doubleValue();
                    alert.bearing = YaV1.sNF.parse(items.get(9)).intValue();
                }
                catch(ParseException ex)
                {
                    Log.d("Valentine Map", "Exception parsing number " + ex.toString());
                    alert.setOption(LGA_INVALID);
                }
            }

            int prop = Integer.valueOf(items.get(5));

            if((prop  & COLLECTED) > 0)
                alert.setOption(LGA_RECORDED);

            alert.setProperty(prop);
            alert.setTn(Integer.valueOf(items.get(6)));
            alert.setOrder(Integer.valueOf(items.get(7)));
            alert.setPersistentId(0);

            // check if we have delta
            if(items.size() > 11)
                alert.setDeltaSignal(Integer.valueOf(items.get(11)));

            // have we got pxId and lockoutid
            if(items.size() > 12)
            {
                alert.mPxId = Integer.valueOf(items.get(12));
                alert.setPersistentId(Integer.valueOf(items.get(13)));
            }

            // The description is the trick
            parts = Arrays.asList(items.get(3).split("\\<br\\/\\>"));

            if(parts.size() == 3)
            {
                // timestamp is first, we need to rebuild from string !!
                alert.timeStamp = getStampFromString(parts.get(0));

                //compo = Arrays.asList(parts.get(2).split(" "));
                Matcher m = sPattern[0].matcher(parts.get(2));

                if(m.find() && m.groupCount() == 4)
                {
                    alert.setBand(getBandFromString(m.group(1)));
                    alert.setFrequency(Integer.valueOf(m.group(2)));
                    alert.setArrowDir(getAlertDirectionFromString(m.group(3)));
                    alert.setSignal(Integer.valueOf(m.group(4)));
                }
                else
                {
                    m.reset();
                    m = sPattern[1].matcher(parts.get(2));

                    if(m.find() && m.groupCount() == 3)
                    {
                        alert.setBand(getBandFromString(m.group(1)));
                        alert.setFrequency(0);
                        alert.setArrowDir(getAlertDirectionFromString(m.group(2)));
                        alert.setSignal(Integer.valueOf(m.group(3)));
                    }
                    else
                    {
                        Log.d("Valentine", "Cant match parts 2 " + parts.get(2) + " Count " + m.groupCount());
                        return null;
                    }
                }
            }
        }
        catch(NumberFormatException ex)
        {
            Log.d("Valentine", "Logged Alert, create from Csv exception " + ex.toString());
            alert = null;
        }

        return alert;
    }

    // create from a raw format record

    public static LoggedAlert createFromRaw(String str)
    {
        List<String> items = Arrays.asList(str.split("\\s*,\\s*"));

        str = str.replaceAll(",,", ", ,");
        items = Arrays.asList(str.split(","));

        LoggedAlert  alert = new LoggedAlert();
        alert.timeStamp = Long.valueOf(items.get(0));

        try
        {
            // no Gps position ?
            if(items.get(6).trim().isEmpty())
            {
                alert.setOption(LGA_INVALID);
            }
            else
            {
                try
                {
                    alert.lat       = YaV1.sNF.parse(items.get(6)).doubleValue();
                    alert.lon       = YaV1.sNF.parse(items.get(7)).doubleValue();
                    alert.speed     = YaV1.sNF.parse(items.get(9)).doubleValue();
                    alert.bearing   = YaV1.sNF.parse(items.get(8)).intValue();
                }
                catch(ParseException ex)
                {
                    Log.d("Valentine Map", "Exception parsing raw file " + ex.toString());
                    alert.setOption(LGA_INVALID);
                }
            }

            int prop = Integer.valueOf(items.get(5));

            if( (prop  & COLLECTED) > 0)
                alert.setOption(LGA_RECORDED);

            alert.setTn(Integer.valueOf(items.get(10)));
            alert.setOrder(Integer.valueOf(items.get(11)));
            alert.setBand(getBandFromString(items.get(1)));
            alert.setArrowDir(Integer.valueOf(items.get(3)));
            alert.setFrequency(Integer.valueOf(items.get(2)));
            alert.setSignal(Integer.valueOf(items.get(4)));
            alert.setProperty(prop);
            if(items.size() > 12)
            {
                alert.setDeltaSignal(Integer.valueOf(items.get(12)));
                if(items.size() > 13)
                {
                    alert.mPxId = Integer.valueOf(items.get(13));
                    alert.setPersistentId(Integer.valueOf(items.get(14)));
                }
            }
        }
        catch(NumberFormatException ex)
        {
            Log.d("Valentine", "Logged Alert, create from Raw exception " + ex.toString());
            alert = null;
        }

        return alert;
    }

    // get the numeric alert direction from String

    public static int getAlertDirectionFromString(String str)
    {
        if(str.equals("Front"))
            return YaV1Alert.ALERT_FRONT;
        else if(str.equals("Rear"))
            return YaV1Alert.ALERT_REAR;
        else if(str.equals("Side"))
            return YaV1Alert.ALERT_SIDE;

        return YaV1Alert.ALERT_FRONT;
    }

    // get the numeric band from String

    public static int getBandFromString(String str)
    {
        if(str.equals("LASER"))
            return YaV1Alert.BAND_LASER;
        else if(str.equals("Ka"))
            return YaV1Alert.BAND_KA;
        else if(str.equals("K"))
            return YaV1Alert.BAND_K;
        else if(str.equals("X"))
            return YaV1Alert.BAND_X;
        else if(str.equals("Ku"))
            return YaV1Alert.BAND_KU;

        return YaV1Alert.BAND_K;
    }

    // get a time stamp from a string

    public static long getStampFromString(String s)
    {
        // check if we have Am / Pm
        int c = 0;

        if(s.contains("AM") || s.contains("am") || s.contains("PM") || s.contains("pm"))
            c = 1;

        try
        {
            java.util.Date parsedDate = sDateFmt[c].parse(s);
            long l = parsedDate.getTime();
            java.util.Date jv = new java.util.Date();
            jv.setTime(l);
            // Log.d("Valentine", "Date time " + s + " Gives " + DateFormat.format(sDateFmt[0], jv));
            return (parsedDate.getTime() / 1000);
        }

        catch(ParseException Exc)
        {
            Log.d("Valentine", "Error parsing string " + s + " Exc" + Exc.toString());
        }

        return 0;
    }

}
