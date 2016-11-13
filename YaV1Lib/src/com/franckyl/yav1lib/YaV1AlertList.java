package com.franckyl.yav1lib;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;

public class YaV1AlertList extends ArrayList<YaV1Alert> implements Parcelable
{
    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    //public void writeToParcel(Parcel parcel, int i)
    public void writeToParcel(Parcel parcel, int i)
    {
        int size = this.size();

        parcel.writeInt(size);

        for(int j = 0; j < size; j++)
        {
            YaV1Alert r = this.get(j);

            parcel.writeInt(r.getFrequency());
            parcel.writeInt(r.getArrowDir());
            parcel.writeInt(r.getSignal());
            parcel.writeInt(r.getProperty());
            parcel.writeInt(r.getBand());
            parcel.writeInt(r.getColor());
            parcel.writeInt(r.getTn());
            parcel.writeInt(r.getOrder());
            parcel.writeInt(r.getDeltaSignal());
            parcel.writeLong(r.getTimestamp());
            parcel.writeInt(r.getPersistentId());
        }
    }

    public YaV1AlertList()
    {
        // an array list should not exceed 16 - we set 20
        super(20);
    }

    public YaV1AlertList(Parcel in)
    {
        this();
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in)
    {
        this.clear();
        //Log.d("Valentine", "In parcel read data " + in.dataAvail());
        int size = in.readInt();

        for (int i = 0; i < size; i++)
        {
            YaV1Alert r = new YaV1Alert(in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                                        in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                                        in.readInt(), in.readLong(), in.readInt());
            this.add(r);
        }
    }

    // Creator from list

    public final Creator<YaV1AlertList> CREATOR = new Creator<YaV1AlertList>()
    {
        @Override
        public YaV1AlertList createFromParcel(Parcel parcel)
        {
            return new YaV1AlertList(parcel);
        }

        public YaV1AlertList[] newArray(int size)
        {
            return new YaV1AlertList[size];
        }
    };
}
