package com.apiculture.simulator.presentation.ranking;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.remote.RankingEntry;
import com.apiculture.simulator.data.repository.LeaderboardRepository;

import java.util.Collections;
import java.util.List;

public class RankingViewModel extends ViewModel {

    private final LeaderboardRepository repository;
    private final MutableLiveData<List<RankingEntry>> rows = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<LeaderboardRepository.Metric> metric =
            new MutableLiveData<>(LeaderboardRepository.Metric.LEVEL);
    /** null = global */
    private final MutableLiveData<String> regionFilter = new MutableLiveData<>(null);
    /** null = miel vendida total */
    private final MutableLiveData<String> floraFilter = new MutableLiveData<>(null);

    public RankingViewModel(LeaderboardRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<RankingEntry>> rows() {
        return rows;
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<String> error() {
        return error;
    }

    public LiveData<LeaderboardRepository.Metric> metric() {
        return metric;
    }

    public LiveData<String> regionFilter() {
        return regionFilter;
    }

    public LiveData<String> floraFilter() {
        return floraFilter;
    }

    public void setMetric(LeaderboardRepository.Metric m) {
        metric.setValue(m);
        if (m != LeaderboardRepository.Metric.HONEY_SOLD) {
            floraFilter.setValue(null);
        }
        fetchRanking();
    }

    public void setRegionFilter(@Nullable String region) {
        regionFilter.setValue(region);
        fetchRanking();
    }

    public void setFloraFilter(@Nullable String floraKey) {
        floraFilter.setValue(floraKey);
        fetchRanking();
    }

    public void fetchRanking() {
        LeaderboardRepository.Metric m = metric.getValue();
        if (m == null) {
            m = LeaderboardRepository.Metric.LEVEL;
        }
        loading.setValue(true);
        LeaderboardRepository.Metric chosen = m;
        String region = regionFilter.getValue();
        String flora = chosen == LeaderboardRepository.Metric.HONEY_SOLD
                ? floraFilter.getValue()
                : null;
        repository.fetchLeaderboard(chosen, region, flora, new LeaderboardRepository.FetchCallback() {
            @Override
            public void onSuccess(List<RankingEntry> result) {
                loading.postValue(false);
                rows.postValue(result != null ? result : Collections.emptyList());
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }
}
