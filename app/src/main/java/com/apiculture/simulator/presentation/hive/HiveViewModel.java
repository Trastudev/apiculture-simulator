package com.apiculture.simulator.presentation.hive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.repository.HexParcelRepository;
import com.apiculture.simulator.data.repository.HiveLast6DaysCharts;
import com.apiculture.simulator.data.repository.HiveRepository;
import com.apiculture.simulator.data.repository.IberiaHexOverlayStore;
import com.apiculture.simulator.domain.parcel.FloraPlantingProgressRow;
import com.apiculture.simulator.domain.game.HexFloraSaturation;
import com.apiculture.simulator.domain.game.HiveFeedType;
import com.apiculture.simulator.domain.game.HiveHoneyRules;
import com.apiculture.simulator.domain.game.HiveHoneyStocks;
import com.apiculture.simulator.domain.parcel.FloraProgression;
import com.apiculture.simulator.domain.parcel.HexFlora;
import com.apiculture.simulator.domain.parcel.HexParcel;
import com.apiculture.simulator.domain.parcel.HexParcelGameRules;

import android.content.Context;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class HiveViewModel extends ViewModel {
    private final HiveRepository hiveRepository;
    private final HexParcelRepository hexParcelRepository;
    private final MutableLiveData<String> viewingHiveId = new MutableLiveData<>();

    public HiveViewModel(HiveRepository hiveRepository, HexParcelRepository hexParcelRepository) {
        this.hiveRepository = hiveRepository;
        this.hexParcelRepository = hexParcelRepository;
    }

    public void setViewingHiveId(String hiveId) {
        if (hiveId == null || hiveId.isEmpty()) {
            return;
        }
        if (hiveId.equals(viewingHiveId.getValue())) {
            return;
        }
        viewingHiveId.setValue(hiveId);
    }

    public LiveData<HiveEntity> viewingHive() {
        return Transformations.switchMap(viewingHiveId, id -> {
            if (id == null || id.isEmpty()) {
                MutableLiveData<HiveEntity> empty = new MutableLiveData<>();
                empty.setValue(null);
                return empty;
            }
            return hiveRepository.getHiveById(id);
        });
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

    /** Prado de otro jugador: lectura, sin guardar esas colmenas en Room. */
    public void loadVisitYardHives(String ownerId, String hexId, Consumer<List<HiveEntity>> onMain) {
        if (ownerId == null || ownerId.isEmpty() || hexId == null || hexId.isEmpty() || onMain == null) {
            if (onMain != null) {
                onMain.accept(Collections.emptyList());
            }
            return;
        }
        hiveRepository.loadVisitYardHives(ownerId, hexId, onMain);
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
            double siteLat,
            double siteLng,
            boolean withWarehouse,
            Consumer<String> onMainMessage) {
        hexParcelRepository.purchaseHex(hexId, buyerId, parcelName, playerLevel,
                siteLat, siteLng, withWarehouse, onMainMessage);
    }

    public void listOwnedTerrainsForHivePurchase(
            String ownerId, Consumer<List<HiveRepository.OwnedHexOption>> onMain) {
        hiveRepository.listOwnedTerrainsForHivePurchaseAsync(ownerId, onMain);
    }

    public void floraSaturation(String hexId, @Nullable String floraKey, Consumer<HexFloraSaturation> onMain) {
        hiveRepository.floraSaturationAsync(hexId, floraKey, onMain);
    }

    public void listReadyFlorasForHex(String hexId, @Nullable String siteId, Consumer<List<String>> onMain) {
        hiveRepository.listReadyFlorasForHexAsync(hexId, siteId, onMain);
    }

    public void loadFloraPlantingsInProgress(String ownerId, Consumer<List<FloraPlantingProgressRow>> onMain) {
        hiveRepository.loadFloraPlantingsInProgressAsync(ownerId, onMain);
    }

    public void plantAdditionalFlora(
            String hexId,
            String ownerId,
            String floraKey,
            Consumer<String> onMain) {
        hiveRepository.plantAdditionalFloraAsync(hexId, ownerId, floraKey, null, onMain);
    }

    public static int maxHivesPerParcel() {
        return HexParcelGameRules.MAX_HIVES_PER_SITE;
    }

    /** Precio de compra del hex libre según su mix silvestre (base + prima más alta). */
    public static int hexPurchasePriceEurosForHex(String hexId, Context appContext) {
        HexParcel parcel = IberiaHexOverlayStore.findById(appContext, hexId);
        return FloraProgression.terrainPurchaseTotalEurosForNativeMix(HexFlora.nativeMixForParcel(parcel));
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

    public void createHiveAtLocation(String ownerId, double lat, double lng, String floraType,
            Consumer<String> onMainMessage) {
        hiveRepository.createHiveAtLocationValidated(ownerId, lat, lng, floraType, msg -> {
            if (onMainMessage != null) {
                onMainMessage.accept(msg);
            }
        });
    }

    public void changeHiveForageFlora(String hiveId, String floraKey, Consumer<String> onMainMessage) {
        hiveRepository.changeHiveForageFloraAsync(hiveId, floraKey, onMainMessage);
    }

    public void listOwnedHexOptionsForFlora(String ownerId, String floraType,
            Consumer<List<HiveRepository.OwnedHexOption>> onMain) {
        hiveRepository.listOwnedHexOptionsForFloraAsync(ownerId, floraType, onMain);
    }

    public void purchaseHive(String ownerId, String hexId, String floraType, String hiveName,
            int superCount, Consumer<String> onMainMessage) {
        purchaseHive(ownerId, hexId, floraType, hiveName, superCount, null, onMainMessage);
    }

    public void purchaseHive(String ownerId, String hexId, String floraType, String hiveName,
            int superCount, @Nullable String siteId, Consumer<String> onMainMessage) {
        hiveRepository.purchaseHiveValidated(ownerId, hexId, floraType, hiveName, superCount,
                siteId, onMainMessage);
    }

    public void sellHive(HiveEntity hive, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.sellHive(hive, onMainMessage);
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

    public void splitHiveIntoEmptyNuc(HiveEntity hive, String hexId, String floraType, String hiveName,
            int superCount, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.splitHiveIntoEmptyNuc(
                hive.id, hive.ownerId, hexId, floraType, hiveName, superCount, onMainMessage);
    }

    public void splitAndSend(HiveEntity hive, String floraType, String hiveName,
            String destHexId, String destSiteId, String vehicleId, Consumer<String> onMainMessage) {
        if (hive == null || hive.id == null) {
            if (onMainMessage != null) {
                onMainMessage.accept("Colmena no válida.");
            }
            return;
        }
        hiveRepository.splitAndSend(hive.id, hive.ownerId, floraType, hiveName,
                destHexId, destSiteId, vehicleId, onMainMessage);
    }

    public void listSplitDestinations(HiveEntity hive, Consumer<List<HiveRepository.SplitDest>> onMain) {
        hiveRepository.listSplitDestinations(
                hive != null ? hive.ownerId : null, hive, onMain);
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

    public double harvestHoney(HiveEntity hive, String floraKey, double kgRequested) {
        if (hive == null) {
            return 0.0;
        }
        HiveHoneyStocks.ensureSeeded(hive);
        double harvested = HiveHoneyStocks.harvestType(hive, floraKey, kgRequested);
        if (harvested <= 1e-9) {
            return 0.0;
        }
        hiveRepository.saveHive(hive);
        return harvested;
    }

    public void transhumance(HiveEntity hive, double lat, double lng, String floraIgnored,
            Consumer<String> onMainMessage) {
        hiveRepository.transhumanceValidated(hive, lat, lng, floraIgnored, msg -> {
            if (onMainMessage != null) {
                onMainMessage.accept(msg);
            }
        });
    }

    public void placeFromWarehouse(String ownerId, String hiveId, String destHexId,
            Consumer<String> onMainMessage) {
        hiveRepository.placeWarehouseHive(ownerId, hiveId, destHexId, onMainMessage);
    }

    public void buyWarehouse(String ownerId, String hexId, Consumer<String> onMainMessage) {
        buyWarehouse(ownerId, hexId, Double.NaN, Double.NaN, null, onMainMessage);
    }

    public void buyWarehouse(String ownerId, String hexId, double tapLat, double tapLng,
            String warehouseName, Consumer<String> onMainMessage) {
        hexParcelRepository.buyWarehouse(ownerId, hexId, tapLat, tapLng, warehouseName, onMainMessage);
    }

    public void setApiarySite(String ownerId, String hexId, double tapLat, double tapLng,
            Consumer<String> onMainMessage) {
        hexParcelRepository.setApiarySite(ownerId, hexId, tapLat, tapLng, onMainMessage);
    }

    public void upgradeWarehouse(String ownerId, String hexId, Consumer<String> onMainMessage) {
        hexParcelRepository.upgradeWarehouse(ownerId, hexId, onMainMessage);
    }

    public void sellApiary(String ownerId, String hexId, @Nullable String siteId,
            Consumer<String> onMainMessage) {
        hexParcelRepository.sellApiary(ownerId, hexId, siteId, onMainMessage);
    }

    public void sellWarehouse(String ownerId, String hexId, @Nullable String siteId,
            Consumer<String> onMainMessage) {
        hexParcelRepository.sellWarehouse(ownerId, hexId, siteId, onMainMessage);
    }

}
