package com.franckyl.yav1.car;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/**
 * One car session == one head-unit connection. v1 scope is "phone connects,
 * car displays": the session never initiates the V1 connection itself; if the
 * phone-side client is not connected the screen shows instructions instead.
 */
public final class V1CarSession extends Session
{
    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent)
    {
        return new V1AlertScreen(getCarContext());
    }
}
