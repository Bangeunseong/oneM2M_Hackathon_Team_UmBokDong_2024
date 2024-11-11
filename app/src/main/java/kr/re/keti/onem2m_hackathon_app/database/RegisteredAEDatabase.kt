package kr.re.keti.onem2m_hackathon_app.database

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
        fun getInstance(context : Context) : RegisteredAEDatabase? {
            if(instance == null){
                synchronized(RegisteredAEDatabase::class){
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        RegisteredAEDatabase::class.java,
                        "registered_ae.db"
                    ).build()
                }
            }
            return instance
        }
    }
}