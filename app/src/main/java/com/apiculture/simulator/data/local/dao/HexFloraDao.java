package com.apiculture.simulator.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HexFloraEntity;

@Dao
public interface HexFloraDao {

    @Query("SELECT * FROM hex_flora WHERE hexId = :hexId LIMIT 1")
    HexFloraEntity getByHexIdSync(String hexId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HexFloraEntity row);
}
