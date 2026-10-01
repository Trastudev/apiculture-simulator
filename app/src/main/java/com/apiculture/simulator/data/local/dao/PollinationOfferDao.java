package com.apiculture.simulator.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.apiculture.simulator.data.local.entity.PollinationOfferEntity;

import java.util.List;

@Dao
public interface PollinationOfferDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnore(PollinationOfferEntity row);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIgnore(List<PollinationOfferEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PollinationOfferEntity row);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PollinationOfferEntity> rows);

    @Query("SELECT * FROM pollination_offers WHERE id = :id LIMIT 1")
    PollinationOfferEntity getById(String id);

    @Query("SELECT * FROM pollination_offers WHERE taken = 0 AND expireEpochMs > :nowMs"
            + " AND region = :region AND band = :band")
    List<PollinationOfferEntity> getOpenBand(String region, int band, long nowMs);

    @Query("SELECT * FROM pollination_offers WHERE taken = 0")
    List<PollinationOfferEntity> getOpenSync();

    @Query("SELECT * FROM pollination_offers WHERE taken = 0")
    LiveData<List<PollinationOfferEntity>> observeOpen();

    @Query("SELECT COUNT(*) FROM pollination_offers WHERE taken = 0 AND expireEpochMs > :nowMs"
            + " AND region = :region AND band = :band")
    int countOpenBand(String region, int band, long nowMs);

    @Query("SELECT hexId FROM pollination_offers WHERE taken = 0 AND hexId IS NOT NULL")
    List<String> openHexIds();

    @Query("SELECT * FROM pollination_offers WHERE taken = 0 AND expireEpochMs <= :nowMs")
    List<PollinationOfferEntity> getExpiredOpen(long nowMs);

    @Query("SELECT * FROM pollination_offers WHERE hexId = :hexId AND taken = 0 LIMIT 1")
    PollinationOfferEntity getOpenForHex(String hexId);

    @Query("UPDATE pollination_offers SET taken = 1 WHERE id = :id AND taken = 0")
    int markTaken(String id);

    @Query("DELETE FROM pollination_offers WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM pollination_offers WHERE expireEpochMs <= :nowMs AND taken = 0")
    void prune(long nowMs);

    @Query("DELETE FROM pollination_offers")
    void deleteAll();
}
