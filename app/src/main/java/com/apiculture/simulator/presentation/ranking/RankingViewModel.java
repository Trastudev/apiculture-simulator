package com.apiculture.simulator.presentation.ranking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.remote.PlayerScore;
import com.apiculture.simulator.data.repository.MultiplayerRepository;

import java.util.List;

public class RankingViewModel extends ViewModel {
    private final MultiplayerRepository repository;
    private final MutableLiveData<List<PlayerScore>> scores = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public RankingViewModel(MultiplayerRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<PlayerScore>> scores() {
        return scores;
    }

    public LiveData<String> error() {
        return error;
    }

    public void fetchRanking() {
        repository.fetchRanking(new MultiplayerRepository.RankingCallback() {
            @Override
            public void onSuccess(List<PlayerScore> result) {
                scores.postValue(result);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }
}
