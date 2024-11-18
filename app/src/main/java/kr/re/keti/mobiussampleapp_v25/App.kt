package kr.re.keti.mobiussampleapp_v25

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import info.mqtt.android.service.MqttAndroidClient
import kr.re.keti.mobiussampleapp_v25.workers.MqttWorker

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
        var isConnected = false
        const val CHANNEL_ID = "ANOMALY_DETECTION_SERVICE_CHANNEL"
    }
}