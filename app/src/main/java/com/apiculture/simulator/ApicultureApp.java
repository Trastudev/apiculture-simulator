package com.apiculture.simulator;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

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
import com.apiculture.simulator.data.repository.GlobalEventRepository;
import com.apiculture.simulator.data.repository.MarketRepository;
import com.apiculture.simulator.data.repository.MultiplayerRepository;
import com.apiculture.simulator.data.repository.ProfileRepository;
import com.apiculture.simulator.data.repository.UserGameStateRepository;
import com.apiculture.simulator.data.repository.WeatherRepository;
import com.apiculture.simulator.domain.map.PlayableMapRegion;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.apiculture.simulator.domain.game.GameBalanceConfig;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.notification.GameNotificationChannels;
import com.apiculture.simulator.presentation.common.GameNotice;
import com.apiculture.simulator.presentation.common.LevelUpDialog;

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
    private UserGameStateRepository userGameStateRepository;
    private LeaderboardRepository leaderboardRepository;
    private GlobalEventRepository globalEventRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            MobileAds.initialize(this, initializationStatus -> {
            });
        } catch (Throwable ignored) {
        }
        GameNotificationChannels.ensureCreated(this);
        GameNotice.install(this);
        LevelUpDialog.install(this);
        GameBalanceConfig.load(this);
        database = AppDatabase.getInstance(this);
        authRepository = new AuthRepository(this);
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
        globalEventRepository = new GlobalEventRepository(this, firestore);
        globalEventRepository.setMarketRepository(marketRepository);
        globalEventRepository.setEconomyRepository(economyRepository);
        globalEventRepository.startListening();
        hexFloraRepository = new HexFloraRepository(
                database.hexFloraDao(),
                database.hexParcelFloraDao(),
                database.hexParcelOwnershipDao(),
                this);
        hexParcelRepository = new HexParcelRepository(
                database.hexParcelOwnershipDao(),
                economyRepository,
                this,
                hexFloraRepository);
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
        hiveRepository.setPlayerProgressRepository(playerProgressRepository);
        hexParcelRepository.setPlayerProgressRepository(playerProgressRepository);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        userGameStateRepository = new UserGameStateRepository(
                this, firestore, economyRepository, playerProgressRepository, mainHandler);
        leaderboardRepository = new LeaderboardRepository(
                firestore, hiveRepository, economyRepository, playerProgressRepository);
        economyRepository.setEconomyChangedCallback(() -> mainHandler.post(() -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                userGameStateRepository.enqueuePush(u.getUid());
            }
        }));
        playerProgressRepository.setProgressChangedCallback(() -> mainHandler.post(() -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                userGameStateRepository.enqueuePush(u.getUid());
                leaderboardRepository.enqueuePublish(u.getUid());
            }
        }));

        new Thread(() -> {
            HexOverlaySeedInstaller.installFromAssets(this);
            List<HexParcel> parcels = IberiaHexOverlayStore.getParcels(this);
            hexFloraRepository.seedAllParcelsBlocking(parcels);
            IberiaHexOverlayStore.getParcels(this, PlayableMapRegion.SOUTH_AFRICA);
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

    public UserGameStateRepository getUserGameStateRepository() {
        return userGameStateRepository;
    }

    public LeaderboardRepository getLeaderboardRepository() {
        return leaderboardRepository;
    }

    public GlobalEventRepository getGlobalEventRepository() {
        return globalEventRepository;
    }

}
