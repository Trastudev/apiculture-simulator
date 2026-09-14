package com.apiculture.simulator.presentation.admin;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.domain.admin.AdminRoles;
import com.apiculture.simulator.domain.parcel.HexFlora;

public class AdminGrantsViewModel extends ViewModel {

    public static final class Snapshot {
        public final double balance;
        public final double honeyKg;

        Snapshot(double balance, double honeyKg) {
            this.balance = balance;
            this.honeyKg = honeyKg;
        }
    }

    private final EconomyRepository economy;
    private final ProfileRepository profiles;
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(null);
    private final MutableLiveData<Snapshot> snapshot = new MutableLiveData<>();

    public AdminGrantsViewModel(EconomyRepository economy, ProfileRepository profiles) {
        this.economy = economy;
        this.profiles = profiles;
        refresh();
    }

    public LiveData<Boolean> isAdmin() {
        return isAdmin;
    }

    public LiveData<Snapshot> snapshot() {
        return snapshot;
    }

    public void checkAdmin(@Nullable String uid) {
        if (uid == null) {
            isAdmin.postValue(false);
            return;
        }
        profiles.fetchDisplayProfile(uid, p ->
                isAdmin.postValue(AdminRoles.isAdminPlayerName(p.playerName)));
    }

    public void refresh() {
        snapshot.setValue(new Snapshot(economy.getBalance(), economy.getHoneyStock()));
    }

    public boolean grantCoins(double amount) {
        if (!Boolean.TRUE.equals(isAdmin.getValue()) || amount <= 0.0 || amount > 1_000_000_000.0) {
            return false;
        }
        economy.addToBalance(amount);
        refresh();
        return true;
    }

    public boolean grantHoney(@Nullable String floraType, double kg) {
        if (!Boolean.TRUE.equals(isAdmin.getValue()) || kg <= 0.0 || kg > 1_000_000.0) {
            return false;
        }
        String flora = floraType != null && !floraType.trim().isEmpty() ? floraType : HexFlora.MIL_FLORES;
        economy.addHoney(flora, kg);
        refresh();
        return true;
    }
}
