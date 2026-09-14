package com.apiculture.simulator.presentation.admin;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.GlobalEventRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.domain.admin.AdminRoles;

import java.util.List;
import java.util.function.Consumer;

public class AdminEventsViewModel extends ViewModel {

    private final GlobalEventRepository events;
    private final ProfileRepository profiles;
    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>(null);

    public AdminEventsViewModel(GlobalEventRepository events, ProfileRepository profiles) {
        this.events = events;
        this.profiles = profiles;
    }

    public LiveData<Boolean> isAdmin() {
        return isAdmin;
    }

    public LiveData<GlobalEventRepository.Snapshot> events() {
        return events.snapshot();
    }

    public GlobalEventRepository.Snapshot cached() {
        return events.cached();
    }

    public void checkAdmin(@Nullable String uid) {
        if (uid == null) {
            isAdmin.postValue(false);
            return;
        }
        profiles.fetchDisplayProfile(uid, p ->
                isAdmin.postValue(AdminRoles.isAdminPlayerName(p.playerName)));
    }

    public void activateSurge(String flora, int days, String uid, Consumer<String> cb) {
        events.activateDemandSurge(flora, days, uid, cb);
    }

    public void deactivateSurge(Consumer<String> cb) {
        events.deactivateDemandSurge(cb);
    }

    public void activateShift(boolean all, List<String> floras, int demandPct, int pricePct,
            int days, String uid, Consumer<String> cb) {
        events.activateMarketShift(all, floras, demandPct, pricePct, days, uid, cb);
    }

    public void deactivateShift(Consumer<String> cb) {
        events.deactivateMarketShift(cb);
    }

    public void activateVelutina(double loss, List<String> climates, int days, String uid,
            Consumer<String> cb) {
        events.activateVelutina(loss, climates, days, uid, cb);
    }

    public void deactivateVelutina(Consumer<String> cb) {
        events.deactivateVelutina(cb);
    }
}
