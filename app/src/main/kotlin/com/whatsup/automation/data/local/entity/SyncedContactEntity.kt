package com.whatsup.automation.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "synced_contacts")
data class SyncedContactEntity(
    @PrimaryKey
    val normalizedPhone: String,
    val displayName: String
)

@Dao
interface SyncedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<SyncedContactEntity>)

    @Query("SELECT * FROM synced_contacts WHERE normalizedPhone = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): SyncedContactEntity?

    @Query("SELECT displayName FROM synced_contacts WHERE normalizedPhone = :phone LIMIT 1")
    suspend fun getContactNameByPhone(phone: String): String?

    @Query("DELETE FROM synced_contacts")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM synced_contacts")
    suspend fun getCount(): Int
}
