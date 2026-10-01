package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.CargoTripEntity;

import java.util.List;

@Dao
public interface CargoTripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CargoTripEntity trip);

    @Query("SELECT * FROM cargo_trips")
    LiveData<List<CargoTripEntity>> observeAll();

    @Query("SELECT * FROM cargo_trips")
    List<CargoTripEntity> getAllSync();

    @Query("SELECT * FROM cargo_trips WHERE id = :id LIMIT 1")
    CargoTripEntity getById(String id);

    @Query("DELETE FROM cargo_trips WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM cargo_trips WHERE ownerId = :ownerId")
    void deleteAllForOwner(String ownerId);
}
