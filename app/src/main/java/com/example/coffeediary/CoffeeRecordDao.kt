package com.example.coffeediary

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CoffeeRecordDao {

    @Query("SELECT * FROM coffee_records ORDER BY id ASC")
    suspend fun getAllRecords(): List<CoffeeRecord>

    @Insert
    suspend fun insertRecord(record: CoffeeRecord): Long

    @Query("DELETE FROM coffee_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM coffee_records WHERE name = :name")
    suspend fun deleteRecordsByName(name: String)

    @Query("SELECT COUNT(*) FROM coffee_records")
    suspend fun getCount(): Int
}
