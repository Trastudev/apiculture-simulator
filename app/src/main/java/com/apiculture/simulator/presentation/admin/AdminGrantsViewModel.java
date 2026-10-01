package com.apiculture.simulator.presentation.admin;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.PlayerProgressRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.data.repository.UserGameStateRepository;
import com.apiculture.simulator.domain.admin.AdminRoles;
import com.apiculture.simulator.domain.game.LevelSystem;
import com.apiculture.simulator.domain.parcel.HexFlora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class AdminGrantsViewModel extends ViewModel {

    public static final class Snapshot {
        public final double balance;
        public final double honeyKg;
        public final int level;
        public final int xp;
        public final int maxXp;

        Snapshot(double balance, double honeyKg, int level, int xp, int maxXp) {
            this.balance = balance;
            this.honeyKg = honeyKg;
            this.level = level;
            this.xp = xp;
            this.maxXp = maxXp;
        }
    }

    public static final class Target {
        public final boolean all;
        @Nullable
        public final String uid;
        public final String label;

        public static Target all(String label) {
            return new Target(true, null, label);
        }

        public static Target player(String uid, String label) {
            return new Target(false, uid, label);
        }

        private Target(boolean all, @Nullable String uid, String label) {
            this.all = all;
            this.uid = uid;
            this.label = label != null ? label : "";
        }
    }

    private final EconomyRepository economy;
    private final ProfileRepository profiles;
    private final PlayerProgressRepository progress;
    private final UserGameStateRepository gameState;
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(null);
    private final MutableLiveData<Snapshot> snapshot = new MutableLiveData<>();
    private final MutableLiveData<List<ProfileRepository.PlayerOption>> players =
            new MutableLiveData<>(Collections.emptyList());
    @Nullable
    private String uid;
    @Nullable
    private Target target;

    public AdminGrantsViewModel(EconomyRepository economy, ProfileRepository profiles,
                                PlayerProgressRepository progress, UserGameStateRepository gameState) {
        this.economy = economy;
        this.profiles = profiles;
        this.progress = progress;
        this.gameState = gameState;
        refresh();
    }

    public LiveData<Boolean> isAdmin() {
        return isAdmin;
    }

    public LiveData<Snapshot> snapshot() {
        return snapshot;
    }

    public LiveData<List<ProfileRepository.PlayerOption>> players() {
        return players;
    }

    public void setTarget(@Nullable Target target) {
        this.target = target;
    }

    @Nullable
    public Target target() {
        return target;
    }

    public void checkAdmin(@Nullable String uid) {
        this.uid = uid;
        if (target == null && uid != null) {
            target = Target.player(uid, "Yo");
        }
        refresh();
        reloadPlayers();
        if (uid == null) {
            isAdmin.postValue(false);
            return;
        }
        profiles.fetchDisplayProfile(uid, p ->
                isAdmin.postValue(AdminRoles.isAdminPlayerName(p.playerName)));
    }

    public void reloadPlayers() {
        profiles.listPlayers(list -> players.setValue(list != null ? list : Collections.emptyList()));
    }

    public void refresh() {
        int level = progress.getLevel(uid);
        int xp = (int) Math.floor(progress.getXp(uid) + 1e-9);
        snapshot.setValue(new Snapshot(
                economy.getBalance(),
                economy.getHoneyStock(),
                level,
                xp,
                LevelSystem.xpForLevel(level)));
    }

    public boolean grantCoins(double amount, Consumer<String> onMain) {
        return dispatch(amount, 0, null, 0, false, onMain);
    }

    public boolean grantHoney(@Nullable String floraType, double kg, Consumer<String> onMain) {
        String flora = floraType != null && !floraType.trim().isEmpty() ? floraType : HexFlora.MIL_FLORES;
        return dispatch(0, kg, flora, 0, false, onMain);
    }

    public boolean grantXp(int amount, Consumer<String> onMain) {
        return dispatch(0, 0, null, amount, false, onMain);
    }

    public boolean grantLevel(Consumer<String> onMain) {
        return dispatch(0, 0, null, 0, true, onMain);
    }

    private boolean dispatch(double coins, double honeyKg, @Nullable String flora, int xp,
            boolean levelUp, Consumer<String> onMain) {
        if (!Boolean.TRUE.equals(isAdmin.getValue())) {
            return false;
        }
        Target dest = target;
        if (dest == null) {
            onMain.accept("Elige un jugador.");
            return true;
        }
        if (dest.all) {
            gameState.listAllUserIds(ids -> applyToUids(ids, coins, honeyKg, flora, xp, levelUp, onMain));
            return true;
        }
        if (dest.uid == null || dest.uid.isEmpty()) {
            onMain.accept("Elige un jugador.");
            return true;
        }
        applyToUids(Collections.singletonList(dest.uid), coins, honeyKg, flora, xp, levelUp, onMain);
        return true;
    }

    private void applyToUids(List<String> ids, double coins, double honeyKg, @Nullable String flora,
            int xp, boolean levelUp, Consumer<String> onMain) {
        List<String> remote = new ArrayList<>();
        boolean local = false;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (uid != null && uid.equals(id)) {
                local = true;
            } else {
                remote.add(id);
            }
        }
        if (local) {
            if (coins > 0) {
                economy.addToBalance(coins, "Ingreso de administración");
            }
            if (honeyKg > 0) {
                economy.addHoney(flora, honeyKg);
            }
            if (levelUp) {
                progress.addXp(uid, xpToNextLevel());
            } else if (xp > 0) {
                progress.addXp(uid, xp);
            }
            gameState.enqueuePush(uid);
            refresh();
        }
        if (remote.isEmpty()) {
            onMain.accept(null);
            return;
        }
        if (coins > 0) {
            gameState.grantCoinsToUsers(remote, coins, onMain);
        } else if (honeyKg > 0) {
            gameState.grantHoneyToUsers(remote, flora, honeyKg, onMain);
        } else if (levelUp) {
            gameState.grantLevelUpToUsers(remote, onMain);
        } else if (xp > 0) {
            gameState.grantXpToUsers(remote, xp, onMain);
        } else {
            onMain.accept("Indica una cantidad mayor que 0.");
        }
    }

    /** XP que falta para el siguiente nivel (al menos 1). */
    public int xpToNextLevel() {
        Snapshot snap = snapshot.getValue();
        if (snap == null) {
            refresh();
            snap = snapshot.getValue();
        }
        if (snap == null) {
            return LevelSystem.xpForLevel(0);
        }
        return Math.max(1, (int) Math.ceil(snap.maxXp - snap.xp));
    }
}
