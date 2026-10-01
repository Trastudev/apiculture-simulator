package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.PollinationContractEntity;

import java.util.List;

@Dao
public interface PollinationContractDao {
    @Query("SELECT * FROM pollination_contracts WHERE ownerId = :ownerId")
    List<PollinationContractEntity> getAllForOwnerSync(String ownerId);

    @Query("SELECT * FROM pollination_contracts WHERE ownerId = :ownerId")
    LiveData<List<PollinationContractEntity>> getAllForOwner(String ownerId);

    @Query("SELECT * FROM pollination_contracts WHERE id = :id LIMIT 1")
    PollinationContractEntity getByIdSync(String id);

    @Query("SELECT * FROM pollination_contracts WHERE ownerId = :ownerId AND status IN ('ACTIVE', 'RETURNING')")
    List<PollinationContractEntity> getOpenListForOwnerSync(String ownerId);

    @Query("SELECT * FROM pollination_contracts WHERE ownerId = :ownerId AND hexId = :hexId AND status IN ('ACTIVE', 'RETURNING') LIMIT 1")
    PollinationContractEntity getOpenForOwnerAndHexSync(String ownerId, String hexId);

    @Query("SELECT * FROM pollination_contracts WHERE ownerId = :ownerId AND status IN ('ACTIVE', 'RETURNING') LIMIT 1")
    PollinationContractEntity getOpenForOwnerSync(String ownerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PollinationContractEntity row);

    @Query("DELETE FROM pollination_contracts WHERE ownerId = :ownerId")
    void deleteAllForOwner(String ownerId);

    @Query("DELETE FROM pollination_contracts WHERE id = :id")
    void deleteById(String id);
}
