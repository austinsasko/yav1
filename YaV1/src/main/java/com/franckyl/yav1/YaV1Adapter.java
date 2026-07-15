package com.franckyl.yav1;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.franckyl.yav1.ui.BandPalette;
import com.franckyl.yav1.ui.DirectionArrowView;
import com.franckyl.yav1.ui.SignalBarView;
import com.franckyl.yav1lib.YaV1Alert;
import com.franckyl.yav1lib.YaV1AlertList;

/**
 * Alert board adapter. Rows are cards with three stable view types:
 * hero (priority) / standard / locked-or-muted. Colour encodes band, bar length
 * encodes strength, glyph encodes direction; locked/muted recede to grey with a
 * cause. Rows are recycled per view type and updated in place (no re-inflation)
 * so the ~70ms refresh under alert load does not thrash.
 */
public class YaV1Adapter extends BaseAdapter
{
    private static final int TYPE_STANDARD = 0;
    private static final int TYPE_HERO     = 1;
    private static final int TYPE_LOCKED   = 2;

    private static final int LOCKED_MASK =
            YaV1Alert.PROP_LOCKOUT | YaV1Alert.PROP_MUTE |
            YaV1Alert.PROP_JUNK    | YaV1Alert.PROP_BSM;

    private final Context        context;
    private final YaV1AlertList  mList;
    private final LayoutInflater mLayoutInflater;
    private       float          sFontSize = -1f;

    static class ViewHolder
    {
        LinearLayout        card;
        View                accent;
        TextView            tvBand;
        TextView            tvFreq;
        TextView            tvCause;
        DirectionArrowView  arrow;
        SignalBarView       bars;
        int                 styledType = -1;
    }

    public YaV1Adapter(Context context, YaV1AlertList alertList)
    {
        this.context    = context;
        mList           = alertList;
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override public int getCount()            { return mList.size(); }
    @Override public Object getItem(int p)     { return mList.get(p); }
    @Override public long getItemId(int p)     { return p; }
    @Override public int getViewTypeCount()    { return 3; }

    @Override
    public int getItemViewType(int position)
    {
        YaV1Alert a = safeGet(position);
        if(a == null) return TYPE_STANDARD;
        int prop = a.getProperty();
        if((prop & LOCKED_MASK) != 0)            return TYPE_LOCKED;
        if((prop & YaV1Alert.PROP_PRIORITY) != 0) return TYPE_HERO;
        return TYPE_STANDARD;
    }

    private YaV1Alert safeGet(int position)
    {
        try { return mList.get(position); }
        catch(IndexOutOfBoundsException e)
        {
            Log.d("Valentine AlertAdapter", "position " + position + " out of bounds");
            return null;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        ViewHolder h;
        if(convertView == null)
        {
            convertView = mLayoutInflater.inflate(R.layout.alert_row, parent, false);
            h            = new ViewHolder();
            h.card       = (LinearLayout)       convertView.findViewById(R.id.alert_card);
            h.accent     =                      convertView.findViewById(R.id.BAND_ACCENT);
            h.tvBand     = (TextView)           convertView.findViewById(R.id.BAND);
            h.tvFreq     = (TextView)           convertView.findViewById(R.id.FREQUENCY);
            h.tvCause    = (TextView)           convertView.findViewById(R.id.CAUSE);
            h.arrow      = (DirectionArrowView) convertView.findViewById(R.id.DIR_ARROW);
            h.bars       = (SignalBarView)      convertView.findViewById(R.id.SIGNAL_BARS);
            convertView.setTag(h);
        }
        else
        {
            h = (ViewHolder) convertView.getTag();
        }

        YaV1Alert alert = safeGet(position);
        if(alert == null)
            return convertView;

        final int type = getItemViewType(position);

        // Apply the per-type chrome only when the recycled view was styled for a
        // different type - avoids redundant work on the hot path.
        if(h.styledType != type)
        {
            styleForType(h, type);
            h.styledType = type;
        }

        bind(h, alert, type);
        return convertView;
    }

    // ---- static per-type chrome (sizes, background) ----------------------

    private void styleForType(ViewHolder h, int type)
    {
        switch(type)
        {
            case TYPE_HERO:
                h.card.setBackgroundResource(R.drawable.bg_alert_card_hero);
                h.card.setMinimumHeight(dimenPx(R.dimen.alert_hero_min_height));
                h.tvBand.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.band_letter_hero));
                h.tvFreq.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.freq_hero));
                h.tvCause.setVisibility(View.GONE);
                break;
            case TYPE_LOCKED:
                h.card.setBackgroundResource(R.drawable.bg_alert_card_locked);
                h.card.setMinimumHeight(dimenPx(R.dimen.alert_locked_min_height));
                h.tvBand.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.band_letter_standard));
                h.tvFreq.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.freq_standard));
                h.tvCause.setVisibility(View.VISIBLE);
                break;
            case TYPE_STANDARD:
            default:
                h.card.setBackgroundResource(R.drawable.bg_alert_card);
                h.card.setMinimumHeight(dimenPx(R.dimen.alert_standard_min_height));
                h.tvBand.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.band_letter_standard));
                h.tvFreq.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimenPx(R.dimen.freq_standard));
                h.tvCause.setVisibility(View.GONE);
                break;
        }
    }

    // ---- dynamic per-alert binding ---------------------------------------

    private void bind(ViewHolder h, YaV1Alert alert, int type)
    {
        try
        {
            final boolean locked    = (type == TYPE_LOCKED);
            final int     bandColor = BandPalette.colorForBand(alert.getBand());
            final int     accent    = locked ? BandPalette.COLOR_LOCKED : bandColor;

            h.accent.setBackgroundColor(accent);

            if(alert.isLaser())
            {
                // Laser has no band letter; frequency slot carries the laser text.
                h.tvBand.setVisibility(View.GONE);
                h.tvFreq.setText(alert.getBandStr());
            }
            else
            {
                h.tvBand.setVisibility(View.VISIBLE);
                h.tvBand.setText(alert.getBandStr());
                h.tvBand.setTextColor(locked ? BandPalette.COLOR_LOCKED : bandColor);
                h.tvFreq.setText(String.format("%.3f", alert.getFrequency() / 1000.0));
            }

            // Preserve user frequency-box text colour when set; otherwise ink.
            int textColor = alert.getTextColor();
            if(locked)
                h.tvFreq.setTextColor(BandPalette.COLOR_LOCKED);
            else if(textColor != Color.TRANSPARENT)
                h.tvFreq.setTextColor(textColor);
            else
                h.tvFreq.setTextColor(context.getResources().getColor(R.color.ink));

            if(locked)
                h.tvCause.setText(causeFor(alert.getProperty()));

            h.arrow.setColor(bandColor);
            h.arrow.setMuted(locked);
            h.arrow.setDirection(alert.getArrowDir());

            h.bars.setBandColor(bandColor);
            h.bars.setMuted(locked);
            h.bars.setStrength(alert.getSignal());
        }
        catch(Exception e)
        {
            Log.d("Valentine AlertAdapter", "bind error: " + e.toString());
        }
    }

    private int causeFor(int prop)
    {
        if((prop & YaV1Alert.PROP_MUTE)   != 0) return R.string.cause_muted;
        if((prop & YaV1Alert.PROP_JUNK)   != 0) return R.string.cause_junk;
        if((prop & YaV1Alert.PROP_BSM)    != 0) return R.string.cause_filtered;
        return R.string.cause_locked;
    }

    private int dimenPx(int dimenRes)
    {
        return (int) context.getResources().getDimension(dimenRes);
    }
}
