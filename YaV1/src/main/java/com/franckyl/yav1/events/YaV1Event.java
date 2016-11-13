package com.franckyl.yav1.events;

/**
 * Created by franck on 11/14/14.
 */
public abstract class YaV1Event
{
    private Enum _type;

    protected YaV1Event(Enum type)
    {
        this._type = type;
    }

    public Enum getType()
    {
        return this._type;
    }
}
