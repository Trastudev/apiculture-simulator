package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HexParcelOwnershipEntity;

import java.util.List;

@Dao
public interface HexParcelOwnershipDao {

    @Query("SELECT * FROM hex_parcel_ownership")
    LiveData<List<HexParcelOwnershipEntity>> observeAll();

    @Query("SELECT * FROM hex_parcel_ownership")
    List<HexParcelOwnershipEntity> getAllSync();

    @Query("SELECT * FROM hex_parcel_ownership WHERE hexId = :hexId LIMIT 1")
    HexParcelOwnershipEntity getByHexIdSync(String hexId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE hexId = :hexId")
    List<HexParcelOwnershipEntity> listByHexSync(String hexId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE hexId = :hexId AND ownerId = :ownerId LIMIT 1")
    HexParcelOwnershipEntity getByHexAndOwnerSync(String hexId, String ownerId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE hexId = :hexId AND ownerId = :ownerId")
    List<HexParcelOwnershipEntity> listByHexAndOwnerSync(String hexId, String ownerId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE hexId = :hexId AND ownerId = :ownerId AND siteId = :siteId LIMIT 1")
    HexParcelOwnershipEntity getByHexOwnerSiteSync(String hexId, String ownerId, String siteId);

    @Query("DELETE FROM hex_parcel_ownership WHERE hexId = :hexId AND ownerId = :ownerId")
    void deleteByHexAndOwner(String hexId, String ownerId);

    @Query("DELETE FROM hex_parcel_ownership WHERE hexId = :hexId AND ownerId = :ownerId AND siteId = :siteId")
    void deleteByHexOwnerSite(String hexId, String ownerId, String siteId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE ownerId = :ownerId")
    List<HexParcelOwnershipEntity> getAllForOwnerSync(String ownerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HexParcelOwnershipEntity row);

    @Query("DELETE FROM hex_parcel_ownership WHERE ownerId = :ownerId")
    void deleteAllForOwner(String ownerId);

    @Query("DELETE FROM hex_parcel_ownership WHERE hexId = :hexId")
    void deleteByHexId(String hexId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE ownerId = :ownerId AND isPrimary = 1 LIMIT 1")
    HexParcelOwnershipEntity getPrimaryForOwnerSync(String ownerId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE ownerId = :ownerId AND hasWarehouse = 1 LIMIT 1")
    HexParcelOwnershipEntity getWarehouseForOwnerSync(String ownerId);

    @Query("SELECT * FROM hex_parcel_ownership WHERE ownerId = :ownerId AND hasWarehouse = 1")
    List<HexParcelOwnershipEntity> getWarehousesForOwnerSync(String ownerId);

    @Query("SELECT COUNT(*) FROM hex_parcel_ownership WHERE ownerId = :ownerId AND hasWarehouse = 1")
    int countWarehousesForOwnerSync(String ownerId);

    @Query("UPDATE hex_parcel_ownership SET isPrimary = 0 WHERE ownerId = :ownerId")
    void clearPrimaryForOwner(String ownerId);
}
