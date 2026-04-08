package com.apiculture.simulator.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HiveDailyYieldEntity;

@Dao
public interface HiveDailyYieldDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HiveDailyYieldEntity entity);

    @Query("SELECT * FROM hive_daily_yield WHERE hiveId = :hiveId AND dayKey = :dayKey LIMIT 1")
    HiveDailyYieldEntity getYield(String hiveId, int dayKey);

    @Query("DELETE FROM hive_daily_yield WHERE hiveId = :hiveId")
    void deleteAllForHive(String hiveId);
}
