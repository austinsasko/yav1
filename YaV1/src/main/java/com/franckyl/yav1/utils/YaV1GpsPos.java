package com.franckyl.yav1.utils;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by franck on 1/20/14.
 */
public class YaV1GpsPos implements Parcelable
{
    public double  speed     = 0;
    public double  lat       = 0;
    public double  lon       = 0;
    public int     bearing   = 0;
    public long    timestamp = 0;

    public YaV1GpsPos()
    {
        speed     = 0;
        bearing   = 0;
        lat       = 0;
        lon       = 0;
        timestamp = 0;
    }

    public YaV1GpsPos(Parcel in)
    {
        readFromParcel(in);
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i)
    {
        parcel.writeDouble(speed);
        parcel.writeDouble(lat);
        parcel.writeDouble(lon);
        parcel.writeInt(bearing);
        parcel.writeLong(timestamp);
    }

    public void readFromParcel(Parcel parcel)
    {
        speed     = parcel.readDouble();
        lat       = parcel.readDouble();
        lon       = parcel.readDouble();
        bearing   = parcel.readInt();
        timestamp = parcel.readLong();
    }

    public static final Creator<YaV1GpsPos> CREATOR = new Creator<YaV1GpsPos>()
    {
        public YaV1GpsPos createFromParcel(Parcel in)
        {
            return new YaV1GpsPos(in);
        }

        public YaV1GpsPos[] newArray(int size)
        {
            return new YaV1GpsPos[size];
        }
    };
}
