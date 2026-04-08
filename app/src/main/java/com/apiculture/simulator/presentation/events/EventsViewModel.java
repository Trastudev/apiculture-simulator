package com.apiculture.simulator.presentation.events;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HiveRepository;

import java.util.List;
import java.util.Random;

public class EventsViewModel extends ViewModel {
    private final MutableLiveData<String> eventsText = new MutableLiveData<>("Sin eventos hoy.");
    private final Random random = new Random();
    private final LiveData<List<HiveEntity>> source;
    private final Observer<List<HiveEntity>> observer;

    public EventsViewModel(HiveRepository hiveRepository) {
        source = hiveRepository.getAllLocalHives();
        observer = new Observer<List<HiveEntity>>() {
            @Override
            public void onChanged(List<HiveEntity> hives) {
                if (hives == null || hives.isEmpty()) {
                    eventsText.postValue("Sin eventos: no hay colmenas activas.");
                    return;
                }
                HiveEntity hive = hives.get(random.nextInt(hives.size()));
                String event;
                if (hive.health < 50) {
                    event = "Alerta sanitaria en " + hive.name + ": riesgo de enfermedad.";
                } else if (hive.reserves < 30) {
                    event = "Escasez de reservas en " + hive.name + ". Recomendada alimentacion.";
                } else if (random.nextBoolean()) {
                    event = "Clima extremo: bajada de productividad temporal en " + hive.name + ".";
                } else {
                    event = "Temporada favorable: buena floracion cerca de " + hive.name + ".";
                }
                eventsText.postValue(event);
            }
        };
        source.observeForever(observer);
    }

    public LiveData<String> eventsText() {
        return eventsText;
    }

    @Override
    protected void onCleared() {
        source.removeObserver(observer);
        super.onCleared();
    }
}
