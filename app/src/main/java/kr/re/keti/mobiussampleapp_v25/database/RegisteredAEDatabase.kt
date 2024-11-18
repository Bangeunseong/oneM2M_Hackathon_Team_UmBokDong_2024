package kr.re.keti.mobiussampleapp_v25.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RegisteredAE::class], version = 1)
abstract class RegisteredAEDatabase: RoomDatabase() {
    abstract fun registeredAEDAO() : RegisteredAE_DAO

    companion object{
        private var instance : RegisteredAEDatabase? = null

        @Synchronized
        fun getInstance(context : Context) : RegisteredAEDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, RegisteredAEDatabase::class.java, "item_database")
                    .build()
                    .also { instance = it }
            }
        }
    }
}