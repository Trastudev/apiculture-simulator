package com.apiculture.simulator.data.repository;

import androidx.annotation.NonNull;

import com.apiculture.simulator.data.remote.PlayerScore;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class MultiplayerRepository {
    private final FirebaseFirestore firestore;

    public MultiplayerRepository() {
        FirebaseFirestore instance;
        try {
            instance = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            instance = null;
        }
        firestore = instance;
    }

    public interface RankingCallback {
        void onSuccess(List<PlayerScore> scores);
        void onError(String message);
    }

    public void fetchRanking(@NonNull RankingCallback callback) {
        if (firestore == null) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        firestore.collection("players")
                .orderBy("totalHoneyKg")
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<PlayerScore> scores = new ArrayList<>();
                    for (var doc : snapshot.getDocuments()) {
                        String nickname = doc.getString("nickname");
                        Double totalHoneyKg = doc.getDouble("totalHoneyKg");
                        scores.add(new PlayerScore(
                                nickname == null ? "Jugador" : nickname,
                                totalHoneyKg == null ? 0.0 : totalHoneyKg
                        ));
                    }
                    callback.onSuccess(scores);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}
