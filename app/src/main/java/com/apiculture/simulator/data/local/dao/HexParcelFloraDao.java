package com.apiculture.simulator.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HexParcelFloraEntity;

import java.util.List;

@Dao
public interface HexParcelFloraDao {

    @Query("SELECT * FROM hex_parcel_flora WHERE hexId = :hexId")
    List<HexParcelFloraEntity> listForHexSync(String hexId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HexParcelFloraEntity row);

    @Query("DELETE FROM hex_parcel_flora WHERE hexId = :hexId AND floraKey = :floraKey")
    void deleteByHexAndKey(String hexId, String floraKey);

    @Query("DELETE FROM hex_parcel_flora WHERE hexId = :hexId")
    void deleteAllForHex(String hexId);
}
