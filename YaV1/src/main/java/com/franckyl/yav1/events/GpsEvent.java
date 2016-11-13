package com.franckyl.yav1.events;

/**
 * Created by franck on 11/14/14.
 */
public class GpsEvent extends YaV1Event
{
    public enum Type
    {
        UPDATE,
        FROMSAVVY,
        TIMEOUT
    }

    public GpsEvent(Type type)
    {
        super(type);
    }
}
