package com.apiculture.simulator;

import android.app.Application;

import com.apiculture.simulator.data.local.AppDatabase;
import com.apiculture.simulator.data.repository.AuthRepository;
import com.apiculture.simulator.data.repository.EconomyRepository;
import com.apiculture.simulator.data.repository.HexFloraRepository;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.LeaderboardRepository;
import com.apiculture.simulator.data.repository.PlayerProgressRepository;
import com.apiculture.simulator.data.repository.HexOverlaySeedInstaller;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.data.repository.LandMaskAssets;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.data.repository.MultiplayerRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.data.repository.WeatherRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.notification.GameNotificationChannels;

import java.util.List;

public class ApicultureApp extends Application {

    private AppDatabase database;
    private AuthRepository authRepository;
    private HiveRepository hiveRepository;
    private WeatherRepository weatherRepository;
    private MultiplayerRepository multiplayerRepository;
    private EconomyRepository economyRepository;
    private MarketRepository marketRepository;
    private HexParcelRepository hexParcelRepository;
    private HexFloraRepository hexFloraRepository;
    private ProfileRepository profileRepository;
    private PlayerProgressRepository playerProgressRepository;
    private LeaderboardRepository leaderboardRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        GameNotificationChannels.ensureCreated(this);
        database = AppDatabase.getInstance(this);
        authRepository = new AuthRepository();
        weatherRepository = new WeatherRepository();
        economyRepository = new EconomyRepository(this);
        FirebaseFirestore firestore = null;
        try {
            firestore = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            firestore = null;
        }
        profileRepository = new ProfileRepository(firestore);
        marketRepository = new MarketRepository(this, firestore, authRepository);
        hexParcelRepository = new HexParcelRepository(
                database.hexParcelOwnershipDao(),
                economyRepository,
                this);
        hexFloraRepository = new HexFloraRepository(database.hexFloraDao());
        hiveRepository = new HiveRepository(
                database.hiveDao(),
                database.hiveDailyYieldDao(),
                database.gameProductionStateDao(),
                weatherRepository,
                hexParcelRepository,
                hexFloraRepository,
                economyRepository,
                marketRepository,
                this);
        hiveRepository.runBroodPipelineReseedMigrationIfNeeded();
        multiplayerRepository = new MultiplayerRepository();
        playerProgressRepository = new PlayerProgressRepository(this);
        leaderboardRepository = new LeaderboardRepository(
                firestore, hiveRepository, economyRepository, playerProgressRepository);

        new Thread(() -> {
            LandMaskAssets.getOrLoadDefaultLandMask(this);
            HexOverlaySeedInstaller.installFromAssets(this);
            List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(this);
            hexFloraRepository.seedAllParcelsBlocking(parcels);
        }, "map-assets-preload").start();
    }

    public AuthRepository getAuthRepository() {
        return authRepository;
    }

    public HiveRepository getHiveRepository() {
        return hiveRepository;
    }

    public WeatherRepository getWeatherRepository() {
        return weatherRepository;
    }

    public MultiplayerRepository getMultiplayerRepository() {
        return multiplayerRepository;
    }

    public EconomyRepository getEconomyRepository() {
        return economyRepository;
    }

    public MarketRepository getMarketRepository() {
        return marketRepository;
    }

    public HexParcelRepository getHexParcelRepository() {
        return hexParcelRepository;
    }

    public HexFloraRepository getHexFloraRepository() {
        return hexFloraRepository;
    }

    public ProfileRepository getProfileRepository() {
        return profileRepository;
    }

    public PlayerProgressRepository getPlayerProgressRepository() {
        return playerProgressRepository;
    }

    public LeaderboardRepository getLeaderboardRepository() {
        return leaderboardRepository;
    }

}
