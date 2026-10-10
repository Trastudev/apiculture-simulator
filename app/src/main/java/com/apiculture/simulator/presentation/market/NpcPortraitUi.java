package com.apiculture.simulator.presentation.market;

import androidx.annotation.DrawableRes;

import com.apiculture.simulator.R;

public final class NpcPortraitUi {

    private static final int[] FACES = {
            R.drawable.npc_face_00,
            R.drawable.npc_face_01,
            R.drawable.npc_face_02,
            R.drawable.npc_face_03,
            R.drawable.npc_face_04,
            R.drawable.npc_face_05,
            R.drawable.npc_face_06,
            R.drawable.npc_face_07,
            R.drawable.npc_face_08,
            R.drawable.npc_face_09,
            R.drawable.npc_face_10,
            R.drawable.npc_face_11,
            R.drawable.npc_face_12,
            R.drawable.npc_face_13,
            R.drawable.npc_face_14,
            R.drawable.npc_face_15,
            R.drawable.npc_face_16,
            R.drawable.npc_face_17,
            R.drawable.npc_face_18,
            R.drawable.npc_face_19,
            R.drawable.npc_face_20,
            R.drawable.npc_face_21,
            R.drawable.npc_face_22,
            R.drawable.npc_face_23,
            R.drawable.npc_face_24,
            R.drawable.npc_face_25,
            R.drawable.npc_face_26,
            R.drawable.npc_face_27,
            R.drawable.npc_face_28,
            R.drawable.npc_face_29,
            R.drawable.npc_face_30,
            R.drawable.npc_face_31,
            R.drawable.npc_face_32,
            R.drawable.npc_face_33,
            R.drawable.npc_face_34,
            R.drawable.npc_face_35,
            R.drawable.npc_face_36,
            R.drawable.npc_face_37,
            R.drawable.npc_face_38,
            R.drawable.npc_face_39,
            R.drawable.npc_face_40,
            R.drawable.npc_face_41,
            R.drawable.npc_face_42,
            R.drawable.npc_face_43,
            R.drawable.npc_face_44,
            R.drawable.npc_face_45,
            R.drawable.npc_face_46,
            R.drawable.npc_face_47,
            R.drawable.npc_face_48,
            R.drawable.npc_face_49
    };

    private NpcPortraitUi() {
    }

    @DrawableRes
    public static int faceDrawable(int portraitIndex) {
        int n = FACES.length;
        int i = portraitIndex % n;
        if (i < 0) {
            i += n;
        }
        return FACES[i];
    }
}
