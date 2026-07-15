package com.glasslsoftware.yav1.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/**
 * Android Auto entry point. Declared in the manifest with the NAVIGATION
 * category (the only category with a drawable Surface, which the radar
 * display needs). YaV1 never claims navigation focus - it complements
 * Google Maps / Waze, it does not replace them (see V1AlertScreen).
 */
public final class YaV1CarAppService extends CarAppService
{
    @NonNull
    @Override
    public HostValidator createHostValidator()
    {
        // Sideload / personal-use distribution: accept every host (DHU and
        // the production Android Auto host both work with self-signed builds
        // once the AA developer setting "Unknown sources" is enabled).
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession()
    {
        return new V1CarSession();
    }
}
