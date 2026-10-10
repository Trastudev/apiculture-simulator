package com.apiculture.simulator.presentation.workshop;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.apiculture.simulator.R;
import com.apiculture.simulator.data.repository.HoneyLogistics;
import com.apiculture.simulator.domain.workshop.WorkshopRules.Machine;
import com.apiculture.simulator.presentation.tutorial.TutorialChapter;
import com.apiculture.simulator.presentation.tutorial.TutorialProgress;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Textos y accesos del obrador que comparten varias pantallas. */
public final class WorkshopUi {

    private WorkshopUi() {
    }

    @NonNull
    public static String machineName(@NonNull Context c, @NonNull Machine m) {
        switch (m) {
            case RECEPTION: return c.getString(R.string.workshop_machine_reception);
            case UNCAPPER: return c.getString(R.string.workshop_machine_uncapper);
            case EXTRACTOR: return c.getString(R.string.workshop_machine_extractor);
            case MATURER: return c.getString(R.string.workshop_machine_maturer);
            default: return c.getString(R.string.workshop_machine_packer);
        }
    }

    /**
     * La recogida exige obrador, salvo en el capítulo 1 del tutorial, donde se cosecha antes de
     * tener almacén.
     */
    public static boolean blocksHarvest(@NonNull Context c, @Nullable String ownerId) {
        if (ownerId == null || ownerId.isEmpty() || !HoneyLogistics.harvestNeedsWorkshop(c, ownerId)) {
            return false;
        }
        return new TutorialProgress(c).isDone(ownerId, TutorialChapter.FIRST_APIARY);
    }

    public static void showNeedWorkshop(@NonNull Fragment fragment) {
        if (!fragment.isAdded() || fragment.getContext() == null) {
            return;
        }
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.workshop_need_title)
                .setMessage(R.string.workshop_need_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.workshop_need_go, (d, w) -> open(fragment))
                .show();
    }

    public static final String ARG_HEX = "hexId";

    public static void open(@NonNull Fragment fragment) {
        open(fragment, null);
    }

    /** Abre el obrador de ese almacén; sin terreno, el primero del jugador. */
    public static void open(@NonNull Fragment fragment, @Nullable String hexId) {
        if (!fragment.isAdded()) {
            return;
        }
        android.os.Bundle args = new android.os.Bundle();
        if (hexId != null) {
            args.putString(ARG_HEX, hexId);
        }
        try {
            NavHostFragment.findNavController(fragment).navigate(R.id.workshopFragment, args);
        } catch (RuntimeException ignored) {
            // Fragmento fuera del NavHost (diálogo suelto): no hay a dónde ir.
        }
    }
}
