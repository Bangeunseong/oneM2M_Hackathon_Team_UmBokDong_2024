package kr.re.keti.mobiussampleapp_v25.services

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.re.keti.mobiussampleapp_v25.App
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.ae
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.csebase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequest
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequestParser
import kr.re.keti.mobiussampleapp_v25.utils.ParseElementXml
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

class MqttConnectionService: Service() {
    private var mqttClient: MqttAndroidClient? = null
    private var mqttReqTopic: String? = null
    private var mqttRespTopic: String? = null
    private lateinit var db: RegisteredAEDatabase
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        db = RegisteredAEDatabase.getInstance(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bundle = intent?.extras

        mqttReqTopic = bundle?.getString("MQTT_REQ_TOPIC")
        mqttRespTopic = bundle?.getString("MQTT_RESP_TOPIC")
        Log.d(TAG, "${mqttReqTopic}, ${mqttRespTopic}")

        createMQTT(true, mqttReqTopic!!, mqttRespTopic!!)
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("MQTT Service")
            .setContentText("MQTT is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        startForeground(1, notification)
        Log.d(TAG, "MQTT Connection Created")
        return START_STICKY_COMPATIBILITY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MQTT Connection Destroyed")
        createMQTT(false, mqttReqTopic!!, mqttRespTopic!!)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    /* Create MQTT Connection */
    private fun createMQTT(mqttStart: Boolean, mqttReqTopic: String, mqttRespTopic: String) {
        if (mqttStart && mqttClient == null) {
            mqttClient = MqttAndroidClient(
                this,
                "tcp://" + csebase.host + ":" + csebase.MQTTPort,
                MqttClient.generateClientId()
            )

            /* MQTT Subscribe */
            mqttClient!!.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d(TAG, "connectionLost")
                    Toast.makeText(applicationContext, "Connection lost to MQTT Server", Toast.LENGTH_SHORT).show()
                    App.isConnected = false
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.d(TAG, "messageArrived")

                    /* Write Task to be done -> Update Presence data into room database */
                    var valueSet: Pair<String, Boolean>? = null
                    CoroutineScope(Dispatchers.IO).launch {
                        if(message != null) {
                            valueSet = parseMqttMessage(message)
                            getDeviceInfoNLocation(valueSet!!)
                        }
                    }.invokeOnCompletion {
                        if(valueSet!!.second){
                            val notification = NotificationCompat.Builder(this@MqttConnectionService, App.CHANNEL_ID)
                                .setContentTitle("Bicycle Locker(${valueSet!!.first})")
                                .setContentText("Anomaly Detected")
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setOnlyAlertOnce(true)
                                .setAutoCancel(true)
                                .build()
                            notificationManager.notify(2, notification)
                        }
                    }

                    /* Json Type Response Parsing */
                    val retrqi = MqttClientRequestParser.notificationJsonParse(message.toString())
                    val responseMessage = MqttClientRequest.notificationResponse(retrqi)

                    /* Make json for MQTT Response Message */
                    val res_message = MqttMessage(responseMessage.toByteArray())

                    try {
                        mqttClient!!.publish(mqttRespTopic, res_message)
                    } catch (e: MqttException) {
                        Log.d(TAG, "${e.cause}: ${e.message}")
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) { Log.d(TAG, "deliveryComplete") }
            })

            try {
                val token = mqttClient!!.connect()
                token.actionCallback = object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "onSuccess")
                        val payload = ""
                        val mqttQos = 1 /* 0: NO QoS, 1: No Check , 2: Each Check */

                        val message = MqttMessage(payload.toByteArray())
                        try {
                            val token = mqttClient!!.subscribe(mqttReqTopic, mqttQos)
                            token.actionCallback = object : IMqttActionListener{
                                override fun onSuccess(asyncActionToken: IMqttToken?) {
                                    Log.d(TAG, "Successfully subscribed")
                                }

                                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                    Log.d(TAG, "Failed to subscribe")
                                    Log.d(TAG, "${exception?.cause}: ${exception?.message}")
                                }
                            }
                        } catch (e: MqttException) {
                            Log.d(TAG, "${e.cause}: ${e.message}")
                        }
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "onFailure")
                    }
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            try {
                if (mqttClient != null && mqttClient!!.isConnected) {
                    mqttClient!!.disconnect()
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun getDeviceInfoNLocation(valueSet: Pair<String, Boolean>) = coroutineScope {
        val registeredAE = async {
            val data = db.registeredAEDAO().get(valueSet.first)
            if(data != null){
                val retLocation = RetrieveRequest(serviceAEName = data.applicationName + "_gps", containerName = "DATA")
                retLocation.setReceiver(object : IReceived{
                    override fun getResponseBody(msg: String) {
                        val pxml = ParseElementXml()
                        val latitude = pxml.GetElementXml(msg, "latitude").toDouble()
                        val longitude = pxml.GetElementXml(msg, "longitude").toDouble()
                        App.detectedLocation = Pair(latitude,longitude)
                    }
                })
                retLocation.start(); retLocation.join()
                data
            } else null
        }

        withContext(Dispatchers.Main){
            val data = registeredAE.await()
            if(data != null){
                data.isTriggered = valueSet.second
                db.registeredAEDAO().update(data)
            } else cancel("Terrible Error Occurred!: RegisteredAE is null")
        }
    }
    /* Parse MQTT Message to Pair<String, Boolean> Data Type */
    private fun parseMqttMessage(message: MqttMessage): Pair<String, Boolean> {
        val jsonObject = JSONObject(message.toString())
        val containerUrl = jsonObject.getJSONObject("pc").getJSONObject("m2m:sgn").getString("sur").toString()
        val contentInstance =
            jsonObject.getJSONObject("pc").getJSONObject("m2m:sgn").getJSONObject("nev")
            .getJSONObject("rep").getJSONObject("m2m:cin").getString("con") != "0"
        val urlToken = containerUrl.replace("/",",").split(",")
        val deviceName = urlToken[1].removeSuffix("_pres")
        Log.d(TAG, "deviceName: $deviceName, contentInstance: $contentInstance")
        return Pair(deviceName, contentInstance)
    }

    /* Retrieve Sensor Data */
    internal inner class RetrieveRequest : Thread {
        private val LOG: Logger = Logger.getLogger(
            RetrieveRequest::class.java.name
        )
        private var receiver: IReceived? = null
        private var ContainerName = "cnt-co2"
        private var serviceAEName = ""

        constructor(serviceAEName: String, containerName: String) {
            this.ContainerName = containerName
            this.serviceAEName = serviceAEName
        }

        constructor()

        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb =
                    csebase.serviceUrl + "/" + serviceAEName + "/" + ContainerName + "/" + "latest"

                val mUrl = URL(sb)

                val conn = mUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.doInput = true
                conn.doOutput = false

                conn.setRequestProperty("Accept", "application/xml")
                conn.setRequestProperty("X-M2M-RI", "12345")
                conn.setRequestProperty("X-M2M-Origin", ae.aEid)
                conn.setRequestProperty("nmtype", "long")
                conn.connect()

                var strResp = ""
                val `in` = BufferedReader(InputStreamReader(conn.inputStream))

                var strLine = ""
                while ((`in`.readLine().also { strLine = it }) != null) {
                    strResp += strLine
                }

                if (strResp !== "") {
                    receiver!!.getResponseBody(strResp)
                }
                conn.disconnect()
            } catch (exp: Exception) {
                LOG.log(Level.WARNING, exp.message)
            }
        }
    }

    companion object{
        private const val TAG = "MQTT_SERVICE"
    }
}