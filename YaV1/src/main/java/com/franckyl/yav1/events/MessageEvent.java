package com.franckyl.yav1.events;

/**
 * Created by franck on 11/15/14.
 */
public class MessageEvent extends YaV1Event
{
    public enum Type
    {
        MESSAGE_TOAST
    }
    private String mMessage;

    public MessageEvent(Type type, String message)
    {
        super(type);
        this.mMessage = message;
    }

    public String getMessage()
    {
        return mMessage;
    }
}
