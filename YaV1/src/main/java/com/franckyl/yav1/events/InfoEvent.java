package com.franckyl.yav1.events;

/**
 * Created by franck on 11/14/14.
 */
public class InfoEvent extends YaV1Event
{
    public enum Type
    {
        V1_INFO,
        UI_BUTTON,
        SWEEP_PUSHED
    }

    public InfoEvent(Type type)
    {
        super(type);
    }

}
