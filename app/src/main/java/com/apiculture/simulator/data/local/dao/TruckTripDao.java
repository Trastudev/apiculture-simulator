package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.TruckTripEntity;

import java.util.List;

@Dao
public interface TruckTripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TruckTripEntity trip);

    @Query("SELECT * FROM truck_trips")
    LiveData<List<TruckTripEntity>> observeAll();

    @Query("SELECT * FROM truck_trips")
    List<TruckTripEntity> getAllSync();

    @Query("SELECT * FROM truck_trips WHERE hiveId = :hiveId LIMIT 1")
    TruckTripEntity getByHiveId(String hiveId);

    @Query("DELETE FROM truck_trips WHERE hiveId = :hiveId")
    void delete(String hiveId);

    @Query("DELETE FROM truck_trips WHERE ownerId = :ownerId")
    void deleteAllForOwner(String ownerId);
}
