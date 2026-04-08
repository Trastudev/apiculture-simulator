package com.apiculture.simulator.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.apiculture.simulator.data.local.entity.GameProductionStateEntity;

@Dao
public interface GameProductionStateDao {

    @Query("SELECT * FROM game_production_state WHERE ownerId = :ownerId LIMIT 1")
    GameProductionStateEntity getByOwner(String ownerId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GameProductionStateEntity state);

    @Update
    void update(GameProductionStateEntity state);
}
