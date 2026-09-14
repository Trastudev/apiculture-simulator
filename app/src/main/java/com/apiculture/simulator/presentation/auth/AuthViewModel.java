package com.apiculture.simulator.presentation.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import android.content.Intent;

import com.apiculture.simulator.data.repository.AuthRepository;
import com.google.firebase.auth.FirebaseUser;

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

    public void login(String email, String password) {
        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                isLoggedIn.postValue(true);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }

    public void register(String email, String password) {
        authRepository.register(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                isLoggedIn.postValue(true);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }

    public boolean isGoogleSignInConfigured() {
        return authRepository.isGoogleSignInConfigured();
    }

    public Intent googleSignInIntent() {
        return authRepository.googleSignInIntent();
    }

    public void loginWithGoogleIdToken(String idToken) {
        authRepository.signInWithGoogleIdToken(idToken, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isLoggedIn.postValue(true);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }
}
