package com.apiculture.simulator.presentation.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.repository.ProfileRepository;

public class ProfileSetupViewModel extends ViewModel {

    private final ProfileRepository profileRepository;
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public ProfileSetupViewModel(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public LiveData<Boolean> saving() {
        return saving;
    }

    public LiveData<String> error() {
        return error;
    }

    public void submit(String uid, String honeyBrand, String playerName, Runnable onSuccess) {
        if (uid == null || uid.isEmpty()) {
            error.setValue("NO_UID");
            return;
        }
        saving.setValue(true);
        profileRepository.saveProfile(uid, honeyBrand, playerName, () -> {
            saving.postValue(false);
            onSuccess.run();
        }, msg -> {
            saving.postValue(false);
            error.postValue(msg);
        });
    }
}
