package com.apiculture.simulator.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.apiculture.simulator.ApicultureApp;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/**
 * Provee una coordenada "representativa" del apiario del jugador actual
 * a partir de sus colmenas almacenadas localmente.
 */
public class ApiaryLocationProvider {

    public interface Callback {
        void onLocationAvailable(double lat, double lng, String apiaryLabel);

        void onNoLocation();
    }

    private final Context appContext;

    public ApiaryLocationProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void resolveApiaryLocation(Callback callback) {
        if (!(appContext instanceof ApicultureApp)) {
            callback.onNoLocation();
            return;
        }
        ApicultureApp app = (ApicultureApp) appContext;

        String ownerId = FirebaseAuth.getInstance().getUid();
        if (ownerId == null) {
            callback.onNoLocation();
            return;
        }

        HiveRepository hiveRepository = app.getHiveRepository();
        LiveData<List<HiveEntity>> live = hiveRepository.getLocalHives(ownerId);

        live.observeForever(new Observer<List<HiveEntity>>() {
            @Override
            public void onChanged(@Nullable List<HiveEntity> hives) {
                live.removeObserver(this);
                if (hives == null || hives.isEmpty()) {
                    callback.onNoLocation();
                    return;
                }
                HiveEntity selected = hives.get(0);
                String label = selected.name != null && !selected.name.isEmpty()
                        ? selected.name
                        : "Apiario " + selected.id;
                callback.onLocationAvailable(selected.lat, selected.lng, label);
            }
        });
    }
}

