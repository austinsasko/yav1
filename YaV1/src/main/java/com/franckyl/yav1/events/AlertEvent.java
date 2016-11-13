package com.franckyl.yav1.events;

/**
 * Created by franck on 11/15/14.
 */
public class AlertEvent extends YaV1Event
{
    public enum Type
    {
        V1_ALERT,
        V1_ALERT_OVERLAY
    }

    public AlertEvent(Type type)
    {
        super(type);
    }
}
