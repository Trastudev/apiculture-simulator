package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.HoneyOrderEntity;

import java.util.List;

@Dao
public interface HoneyOrderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnore(HoneyOrderEntity row);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnore(List<HoneyOrderEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(HoneyOrderEntity row);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<HoneyOrderEntity> rows);

    @Query("SELECT * FROM honey_orders WHERE id = :id LIMIT 1")
    HoneyOrderEntity getById(String id);

    @Query("SELECT * FROM honey_orders WHERE taken = 0 ORDER BY expireEpochMs ASC")
    LiveData<List<HoneyOrderEntity>> observeOpen();

    @Query("SELECT * FROM honey_orders WHERE taken = 0"
            + " OR (taken = 1 AND claimedBy = :uid) ORDER BY expireEpochMs ASC")
    LiveData<List<HoneyOrderEntity>> observeVisible(String uid);

    @Query("DELETE FROM honey_orders WHERE taken = 1"
            + " AND (claimedBy IS NULL OR claimedBy = '' OR claimedBy != :uid)")
    void deleteForeignClaims(String uid);

    @Query("SELECT * FROM honey_orders WHERE taken = 0 ORDER BY expireEpochMs ASC")
    List<HoneyOrderEntity> getOpenSync();

    @Query("SELECT * FROM honey_orders ORDER BY expireEpochMs ASC")
    List<HoneyOrderEntity> getAllSync();

    @Query("SELECT * FROM honey_orders WHERE claimedBy = :uid")
    List<HoneyOrderEntity> getClaimedBy(String uid);

    @Query("SELECT COUNT(*) FROM honey_orders WHERE createdDayKey = :dayKey AND region = :region")
    int countForDayRegion(int dayKey, String region);

    @Query("SELECT COUNT(*) FROM honey_orders WHERE createdDayKey = :dayKey AND region = :region AND band = :band"
            + " AND taken = 0")
    int countOpenForDayRegionBand(int dayKey, String region, int band);

    @Query("SELECT destHexId FROM honey_orders WHERE taken = 0 AND destHexId IS NOT NULL")
    List<String> openDestHexIds();

    @Query("SELECT * FROM honey_orders WHERE taken = 0 AND region = :region AND band = :band")
    List<HoneyOrderEntity> getOpenRegionBand(String region, int band);

    @Query("SELECT * FROM honey_orders WHERE taken = 0 AND expireEpochMs <= :nowMs")
    List<HoneyOrderEntity> getExpiredOpen(long nowMs);

    @Query("UPDATE honey_orders SET taken = 1, claimedBy = :uid WHERE id = :id AND taken = 0")
    int markClaimed(String id, String uid);

    @Query("UPDATE honey_orders SET taken = 0, claimedBy = NULL WHERE id = :id")
    void unmarkTaken(String id);

    @Query("DELETE FROM honey_orders WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM honey_orders WHERE expireEpochMs <= :nowMs"
            + " AND (taken = 0 OR claimedBy IS NULL OR claimedBy = '')")
    void prune(long nowMs);

    @Query("DELETE FROM honey_orders")
    void deleteAll();
}
