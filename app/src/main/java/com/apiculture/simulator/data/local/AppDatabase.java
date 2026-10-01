package com.apiculture.simulator.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.apiculture.simulator.data.local.dao.GameProductionStateDao;
import com.apiculture.simulator.data.local.dao.HexFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelFloraDao;
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.dao.HiveDailyYieldDao;
import com.apiculture.simulator.data.local.dao.HiveDao;
import com.apiculture.simulator.data.local.dao.HoneyOrderDao;
import com.apiculture.simulator.data.local.dao.PollinationContractDao;
import com.apiculture.simulator.data.local.dao.PollinationOfferDao;
import com.apiculture.simulator.data.local.dao.CargoTripDao;
import com.apiculture.simulator.data.local.dao.TruckTripDao;
import com.apiculture.simulator.data.local.entity.GameEventEntity;
import com.apiculture.simulator.data.local.entity.GameProductionStateEntity;
import com.apiculture.simulator.data.local.entity.HexFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveDailyYieldEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;
import com.apiculture.simulator.data.local.entity.PollinationOfferEntity;
import com.apiculture.simulator.data.local.entity.HoneyBatchEntity;
import com.apiculture.simulator.data.local.entity.LocationEntity;
import com.apiculture.simulator.data.local.entity.PlayerEntity;
import com.apiculture.simulator.data.local.entity.PollinationContractEntity;
import com.apiculture.simulator.data.local.entity.CargoTripEntity;
import com.apiculture.simulator.data.local.entity.TruckTripEntity;

@Database(
        entities = {
                HiveEntity.class,
                HiveDailyYieldEntity.class,
                GameProductionStateEntity.class,
                PlayerEntity.class,
                LocationEntity.class,
                HoneyBatchEntity.class,
                GameEventEntity.class,
                HexParcelOwnershipEntity.class,
                HexFloraEntity.class,
                HexParcelFloraEntity.class,
                PollinationContractEntity.class,
                TruckTripEntity.class,
                CargoTripEntity.class,
                HoneyOrderEntity.class,
                PollinationOfferEntity.class
        },
        version = 46,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // beeCount inválido (0) por documentos sin campo o datos viejos
            db.execSQL("UPDATE hives SET beeCount = 25000 WHERE beeCount <= 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("UPDATE hives SET beeCount = 25000");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hive_daily_yield ("
                    + "hiveId TEXT NOT NULL, dayKey INTEGER NOT NULL, kg REAL NOT NULL, "
                    + "PRIMARY KEY(hiveId, dayKey))");
            db.execSQL("CREATE TABLE IF NOT EXISTS game_production_state ("
                    + "ownerId TEXT NOT NULL PRIMARY KEY, gameStartDayKey INTEGER NOT NULL, "
                    + "lastProcessedProductionDayKey INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN populationStateJson TEXT");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN varroaPct REAL NOT NULL DEFAULT 2.0");
            db.execSQL("ALTER TABLE hives ADD COLUMN varroaTreatmentDaysRemaining INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN varroaReboundDaysRemaining INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastHealthSimDayKey INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryDayKey INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryHoneyKg REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryDeltaBees INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryDeltaHealth INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryDeltaVarroa REAL NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryWorkerDeaths INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryWorkerEmergences INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummaryEggsLaid INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_parcel_ownership ("
                    + "hexId TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL)");
            db.execSQL("ALTER TABLE hives ADD COLUMN hexId TEXT");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_elevation ("
                    + "hexId TEXT NOT NULL PRIMARY KEY, maxElevationMeters INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN elevationMeters INTEGER NOT NULL DEFAULT -1");
            db.execSQL("DROP TABLE IF EXISTS hex_elevation");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_flora ("
                    + "hexId TEXT NOT NULL PRIMARY KEY, "
                    + "floraType TEXT NOT NULL)");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hive_daily_yield ADD COLUMN workerNetDelta INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hive_daily_yield ADD COLUMN eggsLaid INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN lastSummarySwarmed INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN parcelName TEXT");
        }
    };

    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN superCount INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE hives SET superCount = CASE "
                    + "WHEN honeyProduction > 30.0001 THEN 2 "
                    + "WHEN honeyProduction > 5.0001 THEN 1 "
                    + "ELSE 0 END");
            db.execSQL("UPDATE hives SET honeyProduction = CASE "
                    + "WHEN superCount <= 0 THEN MIN(honeyProduction, 5.0) "
                    + "WHEN superCount = 1 THEN MIN(honeyProduction, 30.0) "
                    + "ELSE MIN(honeyProduction, 60.0) END");
        }
    };

    private static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE game_production_state ADD COLUMN gameRealTimeAnchorEpochMs "
                    + "INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_parcel_flora ("
                    + "hexId TEXT NOT NULL, "
                    + "floraKey TEXT NOT NULL, "
                    + "plantedAtEpochMs INTEGER NOT NULL, "
                    + "readyAtEpochMs INTEGER NOT NULL, "
                    + "PRIMARY KEY(hexId, floraKey))");
            db.execSQL("INSERT OR IGNORE INTO hex_parcel_flora (hexId, floraKey, plantedAtEpochMs, readyAtEpochMs) "
                    + "SELECT hexId, floraType, 0, 0 FROM hex_flora");
        }
    };

    private static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN feedHoneyBonusEndDayKeyExclusive INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN feedHoneyBonusMultiplier REAL NOT NULL DEFAULT 1.0");
            db.execSQL("ALTER TABLE hives ADD COLUMN feedBroodBonusEndDayKeyExclusive INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN feedBroodBonusMultiplier REAL NOT NULL DEFAULT 1.0");
        }
    };

    private static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN transhumanceArrivesDayKey INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hive_daily_yield ADD COLUMN consumptionKg REAL NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hive_daily_yield ADD COLUMN forageKg REAL NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN honeyStocksJson TEXT");
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_flora ADD COLUMN expireAtDayKey INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_flora ADD COLUMN lastMaintainedYear INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN forageDayKey INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN forageSnapshotJson TEXT");
        }
    };

    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN contractId TEXT");
            db.execSQL("ALTER TABLE hives ADD COLUMN contractOriginHexId TEXT");
            db.execSQL("ALTER TABLE hives ADD COLUMN contractOriginFlora TEXT");
            db.execSQL("ALTER TABLE hives ADD COLUMN contractOriginLat REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN contractOriginLng REAL NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS pollination_contracts ("
                    + "id TEXT NOT NULL PRIMARY KEY, "
                    + "ownerId TEXT, "
                    + "hexId TEXT, "
                    + "flora TEXT, "
                    + "npcName TEXT, "
                    + "estateName TEXT, "
                    + "region TEXT, "
                    + "climateZone TEXT, "
                    + "layer INTEGER NOT NULL DEFAULT 0, "
                    + "status TEXT, "
                    + "collectedKg REAL NOT NULL DEFAULT 0, "
                    + "poolKg REAL NOT NULL DEFAULT 0, "
                    + "sawPeak INTEGER NOT NULL DEFAULT 0, "
                    + "minPct REAL NOT NULL DEFAULT 0, "
                    + "payB INTEGER NOT NULL DEFAULT 0, "
                    + "extraBPerPoint INTEGER NOT NULL DEFAULT 0, "
                    + "travelCostPaid INTEGER NOT NULL DEFAULT 0, "
                    + "acceptedDayKey INTEGER NOT NULL DEFAULT 0, "
                    + "hiveIdsJson TEXT)");
        }
    };

    private static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pollination_contracts ADD COLUMN workDays INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE pollination_contracts ADD COLUMN dueDayKey INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE pollination_contracts ADD COLUMN startDoy INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN inWarehouse INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hives ADD COLUMN returnToWarehouse INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN isPrimary INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN pendingContractHexId TEXT");
            db.execSQL("ALTER TABLE hives ADD COLUMN pendingContractDayKey INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS truck_trips ("
                    + "hiveId TEXT NOT NULL PRIMARY KEY, ownerId TEXT, "
                    + "originLat REAL NOT NULL, originLng REAL NOT NULL, "
                    + "destLat REAL NOT NULL, destLng REAL NOT NULL, destHexId TEXT, "
                    + "startEpochMs INTEGER NOT NULL, durationMs INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE truck_trips ADD COLUMN routePolyline TEXT");
        }
    };

    private static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS cargo_trips ("
                    + "id TEXT NOT NULL PRIMARY KEY, ownerId TEXT, kind TEXT, phase TEXT, "
                    + "floraKey TEXT, kg REAL NOT NULL, cargoJson TEXT, "
                    + "originLat REAL NOT NULL, originLng REAL NOT NULL, "
                    + "destLat REAL NOT NULL, destLng REAL NOT NULL, "
                    + "originLabel TEXT, destLabel TEXT, originHexId TEXT, destHexId TEXT, "
                    + "returnLat REAL NOT NULL, returnLng REAL NOT NULL, "
                    + "returnLabel TEXT, returnHexId TEXT, unitPrice REAL NOT NULL, "
                    + "npcName TEXT, hiveId TEXT, startEpochMs INTEGER NOT NULL, "
                    + "durationMs INTEGER NOT NULL, routePolyline TEXT)");
        }
    };

    private static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN hasWarehouse INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN warehouseLevel INTEGER NOT NULL DEFAULT 1");
        }
    };

    private static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS honey_orders ("
                    + "id TEXT NOT NULL PRIMARY KEY, npcName TEXT, portraitIndex INTEGER NOT NULL, "
                    + "floraKey TEXT, kg REAL NOT NULL, unitPrice REAL NOT NULL, destHexId TEXT, "
                    + "destLat REAL NOT NULL, destLng REAL NOT NULL, destLabel TEXT, region TEXT, "
                    + "createdDayKey INTEGER NOT NULL, expireEpochMs INTEGER NOT NULL, "
                    + "taken INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE honey_orders ADD COLUMN claimedBy TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN orderId TEXT");
        }
    };

    private static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_parcel_ownership_new ("
                    + "hexId TEXT NOT NULL, ownerId TEXT NOT NULL, parcelName TEXT, "
                    + "forageDayKey INTEGER NOT NULL, forageSnapshotJson TEXT, "
                    + "isPrimary INTEGER NOT NULL, hasWarehouse INTEGER NOT NULL, "
                    + "warehouseLevel INTEGER NOT NULL, PRIMARY KEY(hexId, ownerId))");
            db.execSQL("INSERT OR IGNORE INTO hex_parcel_ownership_new ("
                    + "hexId, ownerId, parcelName, forageDayKey, forageSnapshotJson, "
                    + "isPrimary, hasWarehouse, warehouseLevel) "
                    + "SELECT hexId, ownerId, parcelName, forageDayKey, forageSnapshotJson, "
                    + "isPrimary, hasWarehouse, warehouseLevel FROM hex_parcel_ownership");
            db.execSQL("DROP TABLE hex_parcel_ownership");
            db.execSQL("ALTER TABLE hex_parcel_ownership_new RENAME TO hex_parcel_ownership");
        }
    };

    private static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN siteLat REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN siteLng REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN warehouseLat REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE hex_parcel_ownership ADD COLUMN warehouseLng REAL NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS hex_parcel_ownership_new ("
                    + "hexId TEXT NOT NULL, ownerId TEXT NOT NULL, siteId TEXT NOT NULL, "
                    + "parcelName TEXT, forageDayKey INTEGER NOT NULL, forageSnapshotJson TEXT, "
                    + "isPrimary INTEGER NOT NULL, hasWarehouse INTEGER NOT NULL, "
                    + "warehouseLevel INTEGER NOT NULL, siteLat REAL NOT NULL, siteLng REAL NOT NULL, "
                    + "warehouseLat REAL NOT NULL, warehouseLng REAL NOT NULL, "
                    + "PRIMARY KEY(hexId, ownerId, siteId))");
            db.execSQL("INSERT OR IGNORE INTO hex_parcel_ownership_new ("
                    + "hexId, ownerId, siteId, parcelName, forageDayKey, forageSnapshotJson, "
                    + "isPrimary, hasWarehouse, warehouseLevel, siteLat, siteLng, "
                    + "warehouseLat, warehouseLng) "
                    + "SELECT hexId, ownerId, 'default', parcelName, forageDayKey, forageSnapshotJson, "
                    + "isPrimary, hasWarehouse, warehouseLevel, siteLat, siteLng, "
                    + "warehouseLat, warehouseLng FROM hex_parcel_ownership");
            db.execSQL("DROP TABLE hex_parcel_ownership");
            db.execSQL("ALTER TABLE hex_parcel_ownership_new RENAME TO hex_parcel_ownership");
        }
    };

    private static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN firstProductionDayKey INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE hives ADD COLUMN siteId TEXT");
        }
    };

    private static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE honey_orders ADD COLUMN band INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS pollination_offers ("
                    + "id TEXT NOT NULL PRIMARY KEY, hexId TEXT, flora TEXT, "
                    + "startDoy INTEGER NOT NULL, endDoy INTEGER NOT NULL, band INTEGER NOT NULL, "
                    + "region TEXT, createdDayKey INTEGER NOT NULL, expireEpochMs INTEGER NOT NULL, "
                    + "destLat REAL NOT NULL, destLng REAL NOT NULL, npcName TEXT, "
                    + "portraitIndex INTEGER NOT NULL, taken INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_43_44 = new Migration(43, 44) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE truck_trips ADD COLUMN routeRoadKinds TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN routeRoadKinds TEXT");
        }
    };

    private static final Migration MIGRATION_45_46 = new Migration(45, 46) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE truck_trips ADD COLUMN destFlora TEXT");
        }
    };

    private static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN legRole TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN vehicleId TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN shipmentId TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN chainLat REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN chainLng REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN chainLabel TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN chainHexId TEXT");
            db.execSQL("ALTER TABLE cargo_trips ADD COLUMN priceLocked INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract HiveDao hiveDao();

    public abstract HiveDailyYieldDao hiveDailyYieldDao();

    public abstract GameProductionStateDao gameProductionStateDao();

    public abstract HexParcelOwnershipDao hexParcelOwnershipDao();

    public abstract HexFloraDao hexFloraDao();

    public abstract HexParcelFloraDao hexParcelFloraDao();

    public abstract PollinationContractDao pollinationContractDao();

    public abstract TruckTripDao truckTripDao();

    public abstract CargoTripDao cargoTripDao();

    public abstract HoneyOrderDao honeyOrderDao();

    public abstract PollinationOfferDao pollinationOfferDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "apiculture_db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                            MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                            MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
                            MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
                            MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37,
                            MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41,
                            MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45,
                            MIGRATION_45_46)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
