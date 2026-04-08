package com.apiculture.simulator.presentation.hive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.HiveLast6DaysCharts;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class HiveViewModel extends ViewModel {
    private final HiveRepository hiveRepository;
    private final HexParcelRepository hexParcelRepository;
    public HiveViewModel(HiveRepository hiveRepository, HexParcelRepository hexParcelRepository) {
        this.hiveRepository = hiveRepository;
        this.hexParcelRepository = hexParcelRepository;
    }

    public LiveData<List<HiveEntity>> hives(String ownerId) {
        return hiveRepository.getLocalHives(ownerId);
    }

    public LiveData<List<HiveEntity>> allHives() {
        return hiveRepository.getAllLocalHives();
    }

    public LiveData<HiveEntity> hiveById(String hiveId) {
        return hiveRepository.getHiveById(hiveId);
    }

    public void syncCloud() {
        hiveRepository.syncFromCloud();
    }

    public void startRealtimeCloudSync() {
        hiveRepository.startRealtimeCloudSync();
    }

    public void startRealtimeCloudSync(String ownerId) {
        hiveRepository.startRealtimeCloudSync(ownerId);
    }

    public void stopRealtimeCloudSync() {
        hiveRepository.stopRealtimeCloudSync();
    }

    public void startHexParcelCloudSync() {
        hexParcelRepository.startRealtimeCloudSync();
    }

    public void stopHexParcelCloudSync() {
        hexParcelRepository.stopRealtimeCloudSync();
    }

    public LiveData<List<HexParcelOwnershipEntity>> hexOwnerships() {
        return hexParcelRepository.observeOwnerships();
    }

    public void purchaseHex(String hexId, String buyerId, String parcelName, Consumer<String> onMainMessage) {
        hexParcelRepository.purchaseHex(hexId, buyerId, parcelName, onMainMessage);
    }

    public static int maxHivesPerParcel() {
        return HexParcelGameRules.MAX_HIVES_PER_HEX;
    }

    public static int hexPurchasePriceEuros() {
        return (int) HexParcelGameRules.HEX_PURCHASE_PRICE_EUR;
    }

    public void tickDailyProduction(String ownerId) {
        hiveRepository.tickDailyProductionForOwner(ownerId);
    }

    public void updateHiveName(String hiveId, String newName) {
        hiveRepository.updateHiveName(hiveId, newName);
    }

    public void loadLast7DaysProductionKg(String hiveId, String ownerId, Consumer<double[]> onMainThread) {
        hiveRepository.loadLast7DaysProductionKg(hiveId, ownerId, onMainThread);
    }

    public void loadLast6DaysHiveCharts(String hiveId, String ownerId,
            Consumer<HiveLast6DaysCharts> onMainThread) {
        hiveRepository.loadLast6DaysHiveCharts(hiveId, ownerId, onMainThread);
    }

    public void createStarterHive(String ownerId) {
        hiveRepository.enqueueCreateStarterHive(ownerId);
    }

    public void createHiveAtLocation(String ownerId, double lat, double lng, String floraIgnored,
            Consumer<String> onMainMessage) {
        hiveRepository.createHiveAtLocationValidated(ownerId, lat, lng, msg -> {
            if (onMainMessage != null) {
                onMainMessage.accept(msg);
            }
        });
    }

    public void listOwnedHexOptionsForFlora(String ownerId, String floraType,
            Consumer<List<HiveRepository.OwnedHexOption>> onMain) {
        hiveRepository.listOwnedHexOptionsForFloraAsync(ownerId, floraType, onMain);
    }

    public void purchaseHive(String ownerId, String hexId, String floraType, String hiveName,
            int superCount, Consumer<String> onMainMessage) {
        hiveRepository.purchaseHiveValidated(ownerId, hexId, floraType, hiveName, superCount,
                onMainMessage);
    }

    /** Precio de compra: 200 € sin alza, +50 € por cada alza (máx. 2). */
    public static int hivePurchasePriceEuros(int superCount) {
        return HiveRepository.purchasePriceEurosForSuperCount(superCount);
    }

    /** Precio de una alza comprada en la ficha de colmena. */
    public static int singleSuperPurchasePriceEuros() {
        return (int) HiveHoneyRules.SUPER_PURCHASE_PRICE_EUR;
    }

    public void purchaseSupers(HiveEntity hive, int count, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null || hive.ownerId == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.purchaseSupersForHive(hive.id, hive.ownerId, count, onMainMessage);
    }

    public void feedHive(HiveEntity hive) {
        hive.reserves = Math.min(100, hive.reserves + 20);
        hive.health = Math.min(100, hive.health + 5);
        hiveRepository.saveHive(recalculate(hive));
    }

    public void treatDisease(HiveEntity hive) {
        hive.health = Math.min(100, hive.health + 15);
        hive.reserves = Math.max(0, hive.reserves - 5);
        hive.varroaTreatmentDaysRemaining = 45;
        hive.varroaReboundDaysRemaining = 0;
        hiveRepository.saveHive(recalculate(hive));
    }

    public void splitHive(HiveEntity hive, java.util.function.Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.splitHiveHalf(hive.id, onMainMessage);
    }

    public void replaceQueen(HiveEntity hive) {
        hive.queenAgeDays = 1;
        hive.queenGeneticQuality = Math.min(100, hive.queenGeneticQuality + 10);
        hiveRepository.replacePurchasedQueen(hive);
    }

    public double harvestHoney(HiveEntity hive) {
        double stock = Math.max(0.0, hive.honeyProduction);
        double min = HiveHoneyRules.MIN_HIVE_STOCK_KG;
        if (stock <= min) {
            return 0.0;
        }
        double harvested = stock - min;
        hive.honeyProduction = min;
        hive.reserves = Math.max(0, hive.reserves - 10);
        hiveRepository.saveHive(hive);
        return harvested;
    }

    public void transhumance(HiveEntity hive, double lat, double lng, String floraIgnored,
            Consumer<String> onMainMessage) {
        hiveRepository.transhumanceValidated(hive, lat, lng, msg -> {
            if (onMainMessage != null) {
                onMainMessage.accept(msg);
            }
        });
    }

    private HiveEntity recalculate(HiveEntity hive) {
        return hive;
    }
}
