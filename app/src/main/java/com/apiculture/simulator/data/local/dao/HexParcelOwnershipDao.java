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

    @Query("SELECT * FROM hex_parcel_ownership WHERE ownerId = :ownerId")
    List<HexParcelOwnershipEntity> getAllForOwnerSync(String ownerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HexParcelOwnershipEntity row);

    @Query("DELETE FROM hex_parcel_ownership WHERE ownerId = :ownerId")
    void deleteAllForOwner(String ownerId);

    @Query("DELETE FROM hex_parcel_ownership WHERE hexId = :hexId")
    void deleteByHexId(String hexId);
}
