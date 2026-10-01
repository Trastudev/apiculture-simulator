package com.apiculture.simulator.presentation.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.content.Intent;

import com.apiculture.simulator.data.repository.AuthRepository;
import com.apiculture.simulator.data.session.SignedInUser;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class AuthViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> isLoggedIn = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AuthViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
        isLoggedIn.setValue(authRepository.getCurrentUser() != null);
    }

    public LiveData<Boolean> isLoggedIn() {
        return isLoggedIn;
    }

    public LiveData<String> error() {
        return error;
    }

    public boolean isGoogleSignInConfigured() {
        return authRepository.isGoogleSignInConfigured();
    }

    public Intent googleSignInIntent() {
        return authRepository.googleSignInIntent();
    }

    public void loginWithGoogle(GoogleSignInAccount account) {
        authRepository.signInWithGoogle(account, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(SignedInUser user) {
                isLoggedIn.postValue(true);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }
}
