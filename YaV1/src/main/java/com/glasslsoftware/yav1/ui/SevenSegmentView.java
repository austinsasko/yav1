package com.franckyl.yav1.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * A bogey / counter readout rendered with the shipped 7-segment font
 * (assets/yav1_digib.ttf). We reuse the existing font asset rather than
 * hand-drawing segments. Cached statically so repeated inflation is cheap and
 * so it works even before YaV1.sDigital has been initialized.
 */
public class SevenSegmentView extends TextView
{
    private static Typeface sFont = null;

    public SevenSegmentView(Context c)                        { super(c); init(c); }
    public SevenSegmentView(Context c, AttributeSet a)        { super(c, a); init(c); }
    public SevenSegmentView(Context c, AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context c)
    {
        try
        {
            if(sFont == null)
                sFont = Typeface.createFromAsset(c.getApplicationContext().getAssets(), "yav1_digib.ttf");
            setTypeface(sFont);
        }
        catch(Exception e)
        {
            // Fall back to a monospace face; never crash on a missing asset.
            setTypeface(Typeface.MONOSPACE);
        }
        setIncludeFontPadding(false);
    }
}
