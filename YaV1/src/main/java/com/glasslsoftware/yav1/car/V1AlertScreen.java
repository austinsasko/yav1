package com.glasslsoftware.yav1.car;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.glasslsoftware.yav1.R;
import com.glasslsoftware.yav1.YaV1;
import com.glasslsoftware.yav1.YaV1CurrentPosition;
import com.glasslsoftware.yav1.crowd.CrowdMonitor;

/**
 * The single car screen: a NavigationTemplate whose background surface is
 * drawn by {@link V1SurfaceRenderer} (surface drawing is not host-rate-limited)
 * and whose ActionStrip carries only a Mute toggle. The ActionStrip is kept
 * stable and invalidate() is called only on mute / connection state
 * transitions, because template refreshes ARE host-rate-limited.
 *
 * Deliberately absent (so Maps/Waze always keep navigation focus, voice
 * guidance and their dashboard nav card): no NavigationManager use, no
 * navigationStarted()/navigationEnded(), no Trip/turn-by-turn updates, no
 * cluster APIs, no androidx.car.app.action.NAVIGATE intent handling.
 */
public final class V1AlertScreen extends Screen implements DefaultLifecycleObserver, V1AlertRepository.Listener
{
    private static final String LOG_TAG = "V1CarScreen";

    private final V1SurfaceRenderer mRenderer;

    // template-affecting state; changes to these are the only invalidate() triggers
    private V1AlertRepository.ConnState mLastState    = null;
    private boolean                     mLastUserMute = false;

    public V1AlertScreen(@NonNull CarContext carContext)
    {
        super(carContext);
        mRenderer = new V1SurfaceRenderer(carContext);
        getLifecycle().addObserver(this);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner)
    {
        getCarContext().getCarService(AppManager.class).setSurfaceCallback(mRenderer);
        mLastUserMute = V1AlertRepository.get().isUserMuted();
        mLastState    = V1AlertRepository.get().current().state;
        V1AlertRepository.get().addListener(this);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner)
    {
        V1AlertRepository.get().removeListener(this);
        try
        {
            getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
        }
        catch(Exception e)
        {
            // host may already be gone; nothing to clean up
            Log.d(LOG_TAG, "setSurfaceCallback(null) failed: " + e);
        }
    }

    // repository listener - main thread
    @Override
    public void onChanged(V1AlertRepository.Snapshot s)
    {
        mRenderer.setSnapshot(s);

        boolean userMute = V1AlertRepository.get().isUserMuted();

        // template refreshes are host-rate-limited: only invalidate on
        // mute-state / connection-state transitions, never per alert packet
        if(s.state != mLastState || userMute != mLastUserMute)
        {
            mLastState    = s.state;
            mLastUserMute = userMute;
            invalidate();
        }
    }

    @NonNull
    @Override
    public Template onGetTemplate()
    {
        boolean userMute = V1AlertRepository.get().isUserMuted();

        Action muteAction = new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(
                        getCarContext(), userMute ? R.drawable.mute_on_user : R.drawable.mute_off)).build())
                .setTitle(userMute ? "Unmute" : "Mute")
                .setOnClickListener(new OnClickListener()
                {
                    @Override
                    public void onClick()
                    {
                        boolean on = !V1AlertRepository.get().isUserMuted();
                        Log.d(LOG_TAG, "mute action -> " + on);
                        V1AlertRepository.get().setUserMute(on);
                        mLastUserMute = on;
                        invalidate();
                    }
                })
                .build();

        ActionStrip.Builder strip = new ActionStrip.Builder().addAction(muteAction);

        // "Report police here" — only offered when a crowd relay is configured,
        // since the report is posted to the user's self-hosted relay. Tapping
        // is a one-shot anonymous {police, rounded location} report, mirroring
        // the phone board's report button and the iOS relay button.
        if(isCrowdReportingAvailable())
            strip.addAction(reportPoliceAction());

        return new NavigationTemplate.Builder()
                .setActionStrip(strip.build())
                .setBackgroundColor(CarColor.SECONDARY)
                .build();
    }

    private boolean isCrowdReportingAvailable()
    {
        return CrowdMonitor.getInstance() != null
                && YaV1.sPrefs != null
                && !YaV1.sPrefs.getString("csa_relay_url", "").trim().isEmpty();
    }

    private Action reportPoliceAction()
    {
        return new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(
                        getCarContext(), R.drawable.ic_hdr_pin)).build())
                .setTitle("Report")
                .setOnClickListener(new OnClickListener()
                {
                    @Override
                    public void onClick()
                    {
                        CrowdMonitor monitor = CrowdMonitor.getInstance();
                        if(monitor == null)
                            return;

                        // mirror the phone chip: no location fix, no report
                        if(!YaV1CurrentPosition.isValid)
                        {
                            CarToast.makeText(getCarContext(), "Waiting for GPS location",
                                              CarToast.LENGTH_SHORT).show();
                            return;
                        }

                        // the post is async with soft failures, so the wording
                        // is optimistic; false means the cooldown swallowed a
                        // repeated tap (invalidate() is host-rate-limited, so
                        // the action can't be visibly disabled like the chip)
                        if(monitor.reportPoliceHere())
                            CarToast.makeText(getCarContext(), "Reporting police…",
                                              CarToast.LENGTH_SHORT).show();
                        else
                            CarToast.makeText(getCarContext(), "Report already sent",
                                              CarToast.LENGTH_SHORT).show();
                    }
                })
                .build();
    }
}
