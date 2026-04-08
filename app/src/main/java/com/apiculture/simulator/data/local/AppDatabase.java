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
import com.apiculture.simulator.data.local.dao.HexParcelOwnershipDao;
import com.apiculture.simulator.data.local.dao.HiveDailyYieldDao;
import com.apiculture.simulator.data.local.dao.HiveDao;
import com.apiculture.simulator.data.local.entity.GameEventEntity;
import com.apiculture.simulator.data.local.entity.GameProductionStateEntity;
import com.apiculture.simulator.data.local.entity.HexFloraEntity;
import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;
import com.apiculture.simulator.data.local.entity.HiveDailyYieldEntity;
import com.apiculture.simulator.data.local.entity.HiveEntity;
import com.apiculture.simulator.data.local.entity.HoneyBatchEntity;
import com.apiculture.simulator.data.local.entity.LocationEntity;
import com.apiculture.simulator.data.local.entity.PlayerEntity;

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
                HexFloraEntity.class
        },
        version = 16,
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

    private static volatile AppDatabase INSTANCE;

    public abstract HiveDao hiveDao();

    public abstract HiveDailyYieldDao hiveDailyYieldDao();

    public abstract GameProductionStateDao gameProductionStateDao();

    public abstract HexParcelOwnershipDao hexParcelOwnershipDao();

    public abstract HexFloraDao hexFloraDao();

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
                            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
