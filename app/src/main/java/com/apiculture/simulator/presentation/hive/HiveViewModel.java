package com.apiculture.simulator.presentation.hive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.HiveLast6DaysCharts;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.domain.game.XpAwards;
import com.apiculture.simulator.domain.game.HiveFeedType;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;

import android.content.Context;

import java.util.List;
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

    public void purchaseHex(
            String hexId,
            String buyerId,
            String parcelName,
            int playerLevel,
            Consumer<String> onMainMessage) {
        hexParcelRepository.purchaseHex(hexId, buyerId, parcelName, playerLevel, onMainMessage);
    }

    public void listOwnedTerrainsForHivePurchase(
            String ownerId, Consumer<List<HiveRepository.OwnedHexOption>> onMain) {
        hiveRepository.listOwnedTerrainsForHivePurchaseAsync(ownerId, onMain);
    }

    public void listReadyFlorasForHex(String hexId, Consumer<List<String>> onMain) {
        hiveRepository.listReadyFlorasForHexAsync(hexId, onMain);
    }

    public void loadFloraPlantingsInProgress(String ownerId, Consumer<List<FloraPlantingProgressRow>> onMain) {
        hiveRepository.loadFloraPlantingsInProgressAsync(ownerId, onMain);
    }

    public void plantAdditionalFlora(
            String hexId,
            String ownerId,
            String floraKey,
            int playerLevel,
            Consumer<String> onMain) {
        hiveRepository.plantAdditionalFloraAsync(hexId, ownerId, floraKey, playerLevel, onMain);
    }

    public static int maxHivesPerParcel() {
        return HexParcelGameRules.MAX_HIVES_PER_HEX;
    }

    /** Precio de compra del hex libre según su flora nativa (base + prima). */
    public static int hexPurchasePriceEurosForHex(String hexId, Context appContext) {
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        String flora = HexFlora.nativeFloraForParcel(parcel);
        return FloraProgression.terrainPurchaseTotalEurosForNativeFlora(flora);
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

    public void applyHiveFeeding(HiveEntity hive, HiveFeedType type, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null || hive.ownerId == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.applyHiveFeeding(hive.id, hive.ownerId, type, onMainMessage);
    }

    public void treatDisease(HiveEntity hive, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null || hive.ownerId == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.treatVarroa(hive.id, hive.ownerId, onMainMessage);
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

    public void replaceQueenFromInventory(HiveEntity hive, int queenIndex, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null || hive.ownerId == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.replaceQueenFromInventory(hive.id, hive.ownerId, queenIndex, onMainMessage);
    }

    public double harvestHoney(HiveEntity hive, double kgRequested) {
        if (hive == null) {
            return 0.0;
        }
        double stock = Math.max(0.0, hive.honeyProduction);
        double harvested = Math.min(stock, Math.max(0.0, kgRequested));
        if (harvested <= 1e-9) {
            return 0.0;
        }
        hive.honeyProduction = stock - harvested;
        hiveRepository.saveHive(hive);
        hiveRepository.grantXp(hive.ownerId, XpAwards.harvest(harvested));
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

}
