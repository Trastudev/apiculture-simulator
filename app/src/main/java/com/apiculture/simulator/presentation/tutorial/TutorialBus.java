package com.apiculture.simulator.presentation.tutorial;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Puente entre las pantallas y el tutorial.
 * Cada llamada en el juego lleva el comentario del capítulo y la viñeta.
 */
public final class TutorialBus {

    public interface Listener {
        void onEvent(TutorialEvent event, @Nullable String detail);

        void onDialog(TutorialEvent event, @Nullable Dialog dialog, @Nullable View highlight);

        void noteHandoff(@Nullable Dialog dialog);

        void skipFromDialog();

        boolean enterApiaryByMarker();

        boolean wantsWarehouseChoice();

        boolean expectsShop();

        boolean onlyTruck();

        /** Capítulo 1, viñetas 10 y 11. Solo Apiarios, el apiario y la colmena. */
        boolean firstHivePath();

        /** Capítulo 1, viñeta 11. En el apiario solo se puede pulsar la colmena. */
        boolean firstHiveOpen();

        void replayFirstChapter();

        void replayContractsChapter();
    }

    @Nullable
    private static Listener listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TutorialBus() {
    }

    public static void setListener(@Nullable Listener next) {
        listener = next;
    }

    public static void emit(TutorialEvent event) {
        emit(event, null);
    }

    public static void emit(TutorialEvent event, @Nullable String detail) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver(event, detail);
        } else {
            MAIN.post(() -> deliver(event, detail));
        }
    }

    /** Capítulo 1, viñetas 7b y 8. El diálogo ya está en pantalla y hay que resaltar un control. */
    public static void emitDialog(TutorialEvent event, @Nullable Dialog dialog, @Nullable View highlight) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliverDialog(event, dialog, highlight);
        } else {
            MAIN.post(() -> deliverDialog(event, dialog, highlight));
        }
    }

    /** El diálogo se cierra para abrir el siguiente, no porque el jugador cancele. */
    public static void handoff(@Nullable Dialog dialog) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (listener != null) {
                listener.noteHandoff(dialog);
            }
        } else {
            MAIN.post(() -> handoff(dialog));
        }
    }

    public static void skipFromDialog() {
        if (listener != null) {
            listener.skipFromDialog();
        }
    }

    /** Capítulo 1, viñeta 9. Hay que entrar pulsando la imagen del apiario. */
    public static boolean enterApiaryByMarker() {
        return listener != null && listener.enterApiaryByMarker();
    }

    /** Capítulo 1, viñeta 16. El diálogo debe resaltar Instalar un almacén. */
    public static boolean wantsWarehouseChoice() {
        return listener != null && listener.wantsWarehouseChoice();
    }

    /** Capítulo 1. Hay que abrir la tienda del mapa. */
    public static boolean expectsShop() {
        return listener != null && listener.expectsShop();
    }

    /** Capítulo 1. En la tienda solo se puede comprar el camión. */
    public static boolean onlyTruck() {
        return listener != null && listener.onlyTruck();
    }

    /** Capítulo 1, viñetas 10 y 11. El jugador solo puede abrir su apiario y la colmena. */
    public static boolean firstHivePath() {
        return listener != null && listener.firstHivePath();
    }

    public static boolean firstHiveOpen() {
        return listener != null && listener.firstHiveOpen();
    }

    /** Menú del panel. Vuelve a abrir el capítulo 1. */
    public static void replayFirstChapter() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (listener != null) {
                listener.replayFirstChapter();
            }
        } else {
            MAIN.post(TutorialBus::replayFirstChapter);
        }
    }

    /** Menú de admin. Vuelve a abrir el capítulo de contratos. */
    public static void replayContractsChapter() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (listener != null) {
                listener.replayContractsChapter();
            }
        } else {
            MAIN.post(TutorialBus::replayContractsChapter);
        }
    }

    private static void deliver(TutorialEvent event, @Nullable String detail) {
        Listener current = listener;
        if (current != null && event != null) {
            current.onEvent(event, detail);
        }
    }

    private static void deliverDialog(TutorialEvent event, @Nullable Dialog dialog, @Nullable View highlight) {
        Listener current = listener;
        if (current != null && event != null) {
            current.onDialog(event, dialog, highlight);
        }
    }
}
