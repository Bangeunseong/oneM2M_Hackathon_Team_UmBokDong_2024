package kr.re.keti.mobiussampleapp_v25.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RegisteredAE_DAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(registeredAE: RegisteredAE)

    @Update
    suspend fun update(registeredAE: RegisteredAE)

    @Delete
    suspend fun delete(registeredAE: RegisteredAE)

    @Query("SELECT * FROM RegisteredAE WHERE applicationName = :applicationName")
    suspend fun get(applicationName: String) : RegisteredAE?

    @Query("SELECT * FROM RegisteredAE")
    suspend fun getAll() : List<RegisteredAE>

    @Query("DELETE FROM RegisteredAE ")
    suspend fun deleteAll()
}