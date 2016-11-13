package com.franckyl.yav1lib;

/**
 * Created by franck on 6/28/13.
 */

import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

// import com.valentine.esp.data.AlertData;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class YaV1Alert implements Parcelable
{
    // alert img from resources
    public static int ALERT_FRONT = 0;
    public static int ALERT_REAR  = 1;
    public static int ALERT_SIDE  = 2;

    // the theme used

    public static String sTheme   = "X";

    // images for direction and signal
    public static int[][] DIR_SIGNAL;

    // Used for GPS as we load all static resources here

    public static int GPS_ON      = R.drawable.gps_on;
    public static int GPS_OFF     = R.drawable.gps_off;

    // define for bands
    public static int BAND_LASER  = 0;
    public static int BAND_KA     = 1;
    public static int BAND_K      = 2;
    public static int BAND_X      = 3;
    public static int BAND_KU     = 4;

    // string for band names
    public static String BAND_STR[] = {"LASER", "Ka", "K", "X", "Ku"};

    // property of and alert

    public static final int PROP_PRIORITY     = 1;
    public static final int PROP_LOCKOUT_M    = 2; /* Manual only lockout */
    public static final int PROP_INBOX        = 4;
    public static final int PROP_WHITE        = 8;
    public static final int PROP_LOCKOUT      = 16;
    public static final int PROP_MUTE         = 32;
    //public static final int PROP_FILTER       = 64;
    public static final int PROP_LOG          = 128;
    public static final int PROP_CHECKABLE    = 256;
    public static final int PROP_TRUE         = 512;
    public static final int PROP_FALSE        = 1024;
    public static final int PROP_MOVING       = 2048;
    public static final int PROP_STATIC       = 4096;
    public static final int PROP_IO           = 8192;

    // load the Images resources

    public static void loadDirImg(Context context, String pref)
    {
        // initialize the arrow - signal array
        DIR_SIGNAL = new int[3][8];
        DIR_SIGNAL[0][0] = R.drawable.fr_1;
        DIR_SIGNAL[0][1] = R.drawable.fr_2;
        DIR_SIGNAL[0][2] = R.drawable.fr_3;
        DIR_SIGNAL[0][3] = R.drawable.fr_4;
        DIR_SIGNAL[0][4] = R.drawable.fr_5;
        DIR_SIGNAL[0][5] = R.drawable.fr_6;
        DIR_SIGNAL[0][6] = R.drawable.fr_7;
        DIR_SIGNAL[0][7] = R.drawable.fr_7;

        if(pref.isEmpty())
        {
            DIR_SIGNAL[1][0] = R.drawable.rr_1;
            DIR_SIGNAL[1][1] = R.drawable.rr_2;
            DIR_SIGNAL[1][2] = R.drawable.rr_3;
            DIR_SIGNAL[1][3] = R.drawable.rr_4;
            DIR_SIGNAL[1][4] = R.drawable.rr_5;
            DIR_SIGNAL[1][5] = R.drawable.rr_6;
            DIR_SIGNAL[1][6] = R.drawable.rr_7;
            DIR_SIGNAL[1][7] = R.drawable.rr_8;

            DIR_SIGNAL[2][0] = R.drawable.sr_1;
            DIR_SIGNAL[2][1] = R.drawable.sr_2;
            DIR_SIGNAL[2][2] = R.drawable.sr_3;
            DIR_SIGNAL[2][3] = R.drawable.sr_4;
            DIR_SIGNAL[2][4] = R.drawable.sr_5;
            DIR_SIGNAL[2][5] = R.drawable.sr_6;
            DIR_SIGNAL[2][6] = R.drawable.sr_7;
            DIR_SIGNAL[2][7] = R.drawable.sr_8;
        }
        else
        {
            if(pref.equals("u"))
            {
                DIR_SIGNAL[1][0] = R.drawable.urr_1;
                DIR_SIGNAL[1][1] = R.drawable.urr_2;
                DIR_SIGNAL[1][2] = R.drawable.urr_3;
                DIR_SIGNAL[1][3] = R.drawable.urr_4;
                DIR_SIGNAL[1][4] = R.drawable.urr_5;
                DIR_SIGNAL[1][5] = R.drawable.urr_6;
                DIR_SIGNAL[1][6] = R.drawable.urr_7;
                DIR_SIGNAL[1][7] = R.drawable.urr_8;
            }
            else
            {
                DIR_SIGNAL[1][0] = R.drawable.yrr_1;
                DIR_SIGNAL[1][1] = R.drawable.yrr_2;
                DIR_SIGNAL[1][2] = R.drawable.yrr_3;
                DIR_SIGNAL[1][3] = R.drawable.yrr_4;
                DIR_SIGNAL[1][4] = R.drawable.yrr_5;
                DIR_SIGNAL[1][5] = R.drawable.yrr_6;
                DIR_SIGNAL[1][6] = R.drawable.yrr_7;
                DIR_SIGNAL[1][7] = R.drawable.yrr_8;
            }

            DIR_SIGNAL[2][0] = R.drawable.ysr_1;
            DIR_SIGNAL[2][1] = R.drawable.ysr_2;
            DIR_SIGNAL[2][2] = R.drawable.ysr_3;
            DIR_SIGNAL[2][3] = R.drawable.ysr_4;
            DIR_SIGNAL[2][4] = R.drawable.ysr_5;
            DIR_SIGNAL[2][5] = R.drawable.ysr_6;
            DIR_SIGNAL[2][6] = R.drawable.ysr_7;
            DIR_SIGNAL[2][7] = R.drawable.ysr_8;
        }

        sTheme = pref;
    }

    // enum for sorting

    public static enum AlertComparator implements Comparator<YaV1Alert>
    {
        ID_BAND
        {
            public int compare(YaV1Alert o1, YaV1Alert o2)
            {
                //Log.d("Valentine", "Band Comparing " + o1.getBand() + " To " + o2.getBand() + " Return " + Integer.valueOf(o1.getBand()).compareTo(o2.getBand()));
                return Integer.valueOf(o1.getBand()).compareTo(o2.getBand());
            }
        },

        ID_SIGNAL
        {
            public int compare(YaV1Alert o1, YaV1Alert o2)
            {
                //Log.d("Valentine", "Signal Comparing " + o1.getSignal() + " To " + o2.getSignal() + " Return " + -1 * Integer.valueOf(o1.getBand()).compareTo(o2.getBand()));
                return -1 * Integer.valueOf(o1.getSignal()).compareTo(o2.getSignal());
            }
        },

        // for having lockouts at the end
        ID_EX_LOCKOUT
        {
            public int compare(YaV1Alert o1, YaV1Alert o2)
            {
                return Integer.valueOf(o1.getOrderForLockout()).compareTo(o2.getOrderForLockout());
            }
        },

        // for having in box first
        ID_INBOX
        {
            public int compare(YaV1Alert o1, YaV1Alert o2)
            {
                //Log.d("Valentine", "InBox Comparing " + o1.getOrderForInbox() + " To " + o2.getOrderForInbox() + " Return " + Integer.valueOf(o1.getOrderForInbox()).compareTo(o2.getOrderForInbox()));
                return Integer.valueOf(o1.getOrderForInbox()).compareTo(o2.getOrderForInbox());
            }
        };
    };

    // Chained comparator insertion
    public static class ChainedComparator<T> implements Comparator<T>
    {
        private List<Comparator<T>> simpleComparators;
        public ChainedComparator(Comparator<T>... simpleComparators)
        {
            this.simpleComparators = Arrays.asList(simpleComparators);
        }

        public ChainedComparator(List<Comparator<T>> simpleComparators)
        {
            this.simpleComparators = simpleComparators;
        }

        public int compare(T o1, T o2)
        {
            for (Comparator<T> comparator : simpleComparators)
            {
                int result = comparator.compare(o1, o2);
                //Log.d("Valentine", "Comparator " + comparator + " result " + result);

                if (result != 0)
                {
                    return result;
                }
            }
            return 0;
        }
    }

    private int     frequency;
    private int     arrow_dir;
    private int     signal;
    private int     property;
    private int     color        = Color.TRANSPARENT;
    private int     text_color   = Color.TRANSPARENT;
    private int     band;
    private int     tn;
    private int     order;
    private int     deltaSignal = 0;
    private long    timestamp   = 0;
    private int     persistentId = 0;

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
        parcel.writeInt(frequency);
        parcel.writeInt(arrow_dir);
        parcel.writeInt(signal);
        parcel.writeInt(property);
        parcel.writeInt(band);
        parcel.writeInt(color);
        parcel.writeInt(tn);
        parcel.writeInt(order);
        parcel.writeInt(deltaSignal);
        parcel.writeLong(timestamp);
        parcel.writeInt(persistentId);
    }

    public void readFromParcel(Parcel parcel)
    {
        frequency = parcel.readInt();
        arrow_dir = parcel.readInt();
        signal    = parcel.readInt();
        property  = parcel.readInt();
        band      = parcel.readInt();
        color     = parcel.readInt();
        tn        = parcel.readInt();
        order     = parcel.readInt();
        deltaSignal  = parcel.readInt();
        timestamp    = parcel.readLong();
        persistentId = parcel.readInt();
    }

    public static final Creator<YaV1Alert> CREATOR = new Creator<YaV1Alert>()
    {
        public YaV1Alert createFromParcel(Parcel in)
        {
            return new YaV1Alert(in);
        }

        public YaV1Alert[] newArray(int size)
        {
            return new YaV1Alert[size];
        }
    };

    public YaV1Alert(Parcel in)
    {
        readFromParcel(in);
    }

    public YaV1Alert()
    {
        super();
    }

    public YaV1Alert(int frequency, int arrow_dir, int signal, int property, int band,
                     int color, int tn, int order, int delta, long timestamp, int persistentId)
    {
        super();
        this.frequency = frequency;
        this.arrow_dir = arrow_dir;
        this.signal    = signal;
        this.property  = property;
        this.band      = band;
        this.color     = color;
        this.tn        = tn;
        this.order     = order;
        this.deltaSignal = delta;
        this.timestamp   = timestamp;
        this.persistentId = persistentId;
    }

    public int getPersistentId()
    {
        return persistentId;
    }

    public void setPersistentId(int persistentId)
    {
        this.persistentId = persistentId;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(long l)
    {
        this.timestamp = l;
    }

    public void setNow()
    {
        this.timestamp = SystemClock.elapsedRealtime();
    }

    public void setTn(int tn)
    {
        this.tn = tn;
    }
    public int getTn()
    {
        return tn;
    }

    public void setOrder(int order)
    {
        this.order = order;
    }
    public int getOrder()
    {
        return order;
    }

    public int getBand()
    {
        return band;
    }

    public String getBandStr()
    {
        return BAND_STR[band];
    }

    public static String getBandStr(int b)
    {
        return BAND_STR[b];
    }

    public void setBand(int band)
    {
        this.band = band;
    }

    public int getFrequency()
    {
        return frequency;
    }

    public void setFrequency(int frequency)
    {
        this.frequency = frequency;
    }

    public int getArrowDir()
    {
        return arrow_dir;
    }

    public void setArrowDir(int arrow_dir)
    {
        this.arrow_dir = arrow_dir;
    }

    public int getDirSignal()
    {
        if(signal < 1)
            return R.drawable.j_out;

        return DIR_SIGNAL[arrow_dir][signal-1];
    }

    public void setDeltaSignal(int delta)
    {
        this.deltaSignal = delta;
    }

    public int getDeltaSignal()
    {
        return deltaSignal;
    }

    public int getSignal()
    {
        return signal;
    }

    public void setSignal(int signal)
    {
        this.signal = signal;
    }

    public void setSignal(int front, int rear)
    {
        this.signal      = Math.max(front, rear);
        this.deltaSignal = front - rear;
    }

    public int getFrontSignal()
    {
        if(deltaSignal >= 0)
            return signal;
        return signal + deltaSignal;
    }

    public int getRearSignal()
    {
        if(deltaSignal >= 0)
            return signal - deltaSignal;
        return signal;
    }

    // experimental
    public boolean isJout()
    {
        return this.signal < 1;
    }

    public int getProperty()
    {
        return property;
    }

    public void setProperty(int property)
    {
        this.property = property;
    }

    public int getOrderForInbox()
    {
        if( (property & PROP_INBOX) > 0 || band == BAND_LASER)
            return 0;
        return 1;
    }

    public int getOrderForLockout()
    {
        if( (property & PROP_LOCKOUT) > 0)
            return 1;
        if( (property & PROP_WHITE) > 0)
            return -1;
        return 0;
    }

    public int getColor()
    {
        return color;
    }

    public void setColor(int color)
    {
        this.color = color;
    }

    public int getTextColor()
    {
        return text_color;
    }

    public void setTextColor(int color)
    {
        this.text_color = color;
    }

    public boolean isLaser()
    {
        return band == BAND_LASER;
    }

    //experimental

    public boolean isPop()
    {
        //experimental
        return (band == BAND_KA && frequency <= 33900);
    }

    public boolean isK()
    {
        return band == BAND_K;
    }

    public boolean isKa()
    {
        return band == BAND_KA;
    }

    public boolean isKu()
    {
        return band == BAND_KU;
    }

    public boolean isX()
    {
        return band == BAND_X;
    }

    public void setMoving(boolean m)
    {
        if(m)
        {
            property = (property & (~PROP_STATIC));
            property |= PROP_MOVING;
        }
        else
        {
            property = (property & (~PROP_MOVING));
            property |= PROP_STATIC;
        }
    }

    public void setTrue(boolean m)
    {
        if(m)
        {
            property = (property & (~PROP_FALSE));
            property |= PROP_TRUE;
        }
        else
        {
            property = (property & (~PROP_TRUE));
            property = (property & (~PROP_IO));
            property |= PROP_FALSE;
        }
    }

    public void setIO(boolean m)
    {
        if(m)
            property |= PROP_IO;
        else
            property = (property & (~PROP_IO));
    }

    public boolean isIO()
    {
        return  (property & PROP_IO) > 0;
    }
}
