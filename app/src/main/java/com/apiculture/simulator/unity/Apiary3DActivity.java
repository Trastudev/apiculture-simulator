package com.apiculture.simulator.unity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.apiculture.simulator.presentation.MainActivity;
import com.unity3d.player.UnityPlayerActivity;

import java.lang.ref.WeakReference;

/**
 * Patio e inspección en 3D. Se abre con {@link #open} tras {@link UnityBridge#prepare}.
 * Unity comparte proceso con la app y destruir su actividad mata el proceso entero, así que al salir
 * no se cierra: se pone el mapa delante y Unity queda en pausa detrás hasta la próxima visita.
 */
public class Apiary3DActivity extends UnityPlayerActivity {

    private static WeakReference<Apiary3DActivity> current = new WeakReference<>(null);
    /** El jugador quiere estar en el 3D; si la actividad aparece sin pedirla (atrás desde el mapa), se cierra. */
    private static boolean wanted;

    public static void open(@NonNull Context ctx) {
        wanted = true;
        ctx.startActivity(new Intent(ctx, Apiary3DActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        UnityBridge.reopen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!wanted) {
            // Se cerró el mapa que estaba delante: el jugador sale de la app.
            moveTaskToBack(true);
            finish();
        } else {
            UnityReceipts.attach();
        }
    }

    @Override
    public void onUnityPlayerUnloaded() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) {
            current = new WeakReference<>(null);
        }
        super.onDestroy();
    }

    /** Vuelve al mapa dejando Unity cargado en segundo plano. */
    static void leaveToMap() {
        wanted = false;
        Apiary3DActivity a = current.get();
        if (a != null && !a.isFinishing()) {
            a.startActivity(new Intent(a, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    }
}
