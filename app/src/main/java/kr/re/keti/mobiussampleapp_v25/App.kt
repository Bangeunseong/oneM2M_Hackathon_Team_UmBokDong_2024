package kr.re.keti.mobiussampleapp_v25

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log


class App: Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelAlarm()
    }

    private fun createNotificationChannelAlarm() {
        Log.d("Notification Channel", "Channel_Created!")
        val serviceChannel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name) + " Service Channel", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        var serviceIntent: Intent? = null
        var detectedLocation: Pair<Double, Double>? = null
        var isConnected = false
        const val CHANNEL_ID = "ANOMALY_DETECTION_SERVICE_CHANNEL"
    }
}