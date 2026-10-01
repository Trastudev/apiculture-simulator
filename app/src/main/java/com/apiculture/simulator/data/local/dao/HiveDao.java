package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HiveEntity;

import java.util.List;

@Dao
public interface HiveDao {
    @Query("SELECT * FROM hives WHERE ownerId = :ownerId")
    LiveData<List<HiveEntity>> getHivesByOwner(String ownerId);

    @Query("SELECT * FROM hives WHERE ownerId = :ownerId")
    List<HiveEntity> getHivesByOwnerSync(String ownerId);

    @Query("SELECT * FROM hives")
    LiveData<List<HiveEntity>> getAllHives();

    @Query("SELECT * FROM hives")
    List<HiveEntity> getAllHivesSync();

    @Query("SELECT * FROM hives WHERE id = :hiveId LIMIT 1")
    LiveData<HiveEntity> getHiveById(String hiveId);

    @Query("SELECT * FROM hives WHERE id = :hiveId LIMIT 1")
    HiveEntity getHiveByIdSync(String hiveId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HiveEntity hive);

    @Query("SELECT COUNT(*) FROM hives WHERE hexId = :hexId AND hexId IS NOT NULL AND inWarehouse = 0")
    int countByHexId(String hexId);

    @Query("SELECT * FROM hives WHERE hexId = :hexId AND hexId IS NOT NULL AND inWarehouse = 0")
    List<HiveEntity> getByHexIdSync(String hexId);

    @Query("SELECT COUNT(*) FROM hives WHERE hexId = :hexId AND ownerId = :ownerId AND hexId IS NOT NULL AND inWarehouse = 0")
    int countByHexIdAndOwner(String hexId, String ownerId);

    @Query("SELECT * FROM hives WHERE ownerId = :ownerId AND inWarehouse = 1")
    List<HiveEntity> getWarehouseHivesSync(String ownerId);

    @Query("SELECT COUNT(*) FROM hives WHERE ownerId = :ownerId AND inWarehouse = 1")
    int countWarehouse(String ownerId);

    @Query("SELECT * FROM hives WHERE contractId = :contractId")
    List<HiveEntity> getHivesByContractSync(String contractId);

    @Query("DELETE FROM hives WHERE id = :hiveId")
    void deleteById(String hiveId);
}
