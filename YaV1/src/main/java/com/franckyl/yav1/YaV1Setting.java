package com.franckyl.yav1;

import android.util.SparseArray;

import com.valentine.esp.data.UserSettings;

import java.util.ArrayList;

/**
 * Created by franck on 8/5/13.
 */
public class YaV1Setting
{
    private String          mName;
    private int             mId;
    private ArrayList<Byte> mBytes = new ArrayList<Byte>();

    // constructor

    public YaV1Setting(int id, UserSettings iSet)
    {
        mId = id;
        mBytes.clear();
        byte[] b = iSet.buildBytes();

        for(int i=0; i<YaV1SettingSet.SETTING_BYTES; i++)
            mBytes.add(Byte.valueOf(b[i]));
    }

    // copy constructor

    public YaV1Setting(int id, YaV1Setting src)
    {
        mId = id;
        mBytes.addAll(src.getBytes());
        mName = src.getName();
    }
    // Set name

    public void setName(String name)
    {
        mName = name;
    }

    public ArrayList<Byte> getBytes()
    {
        return mBytes;
    }
    // get the name

    public String getName()
    {
        return mName;
    }

    // get the id

    public int getId()
    {
        return mId;
    }

    // reset the Bytes

    public void setBytes(ArrayList<Byte> iByte)
    {
        mBytes.clear();
        mBytes.addAll(iByte);
    }

    public void setBytes(byte[] b)
    {
        mBytes.clear();
        for(int i=0; i<YaV1SettingSet.SETTING_BYTES; i++)
            mBytes.add(Byte.valueOf(b[i]));
    }

    // get a valentine 1 definition from the array list

    public UserSettings getV1Definition()
    {
        UserSettings rc = new UserSettings();
        byte[] b        = new byte[mBytes.size()];
        int i = 0;
        for(Byte s: mBytes)
            b[i++] = s;
        rc.buildFromBytes(b);
        return rc;
    }

    // Apply factory default to this setting

    public void applyFactoryDefault()
    {
        UserSettings v = UserSettings.GenerateFactoryDefaults();
        mBytes.clear();
        byte[] b = v.buildBytes();

        for(int i=0; i<YaV1SettingSet.SETTING_BYTES; i++)
            mBytes.add(Byte.valueOf(b[i]));
    }

    // compare a setting with this one

    public boolean isSame(YaV1Setting is)
    {
        for(int i=0; i<YaV1SettingSet.SETTING_BYTES; i++)
        {
            byte s1 = mBytes.get(i);
            byte s2 = is.getBytes().get(i);

            if(s1 != s2)
                return false;
        }

        return true;
    }

    // is default factory ?

    public boolean isFactoryDefault()
    {
        return (mId == 0 ? true : false);
    }
}
