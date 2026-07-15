package com.glasslsoftware.yav1.car;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
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

        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(muteAction).build())
                .setBackgroundColor(CarColor.SECONDARY)
                .build();
    }
}
