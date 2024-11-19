package kr.re.keti.mobiussampleapp_v25.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import info.mqtt.android.service.MqttAndroidClient
import kr.re.keti.mobiussampleapp_v25.App
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.csebase
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequest
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequestParser
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class MqttConnectionService: Service() {
    private var mqttClient: MqttAndroidClient? = null
    private var mqttReqTopic: String? = null
    private var mqttRespTopic: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bundle = intent?.extras
        mqttReqTopic = bundle?.getString("MQTT_REQ_TOPIC")
        mqttRespTopic = bundle?.getString("MQTT_RESP_TOPIC")

        createMQTT(true, mqttReqTopic!!, mqttRespTopic!!)
        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("MQTT Service")
            .setContentText("MQTT is running in the background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
        Log.d(TAG, "MQTT Connection Created")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MQTT Connection Destroyed")
        createMQTT(false, mqttReqTopic!!, mqttRespTopic!!)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    /* Create MQTT Connection */
    private fun createMQTT(mqttStart: Boolean, mqttReqTopic: String, mqttRespTopic: String) {
        if (mqttStart && mqttClient == null) {
            mqttClient = MqttAndroidClient(
                applicationContext,
                "tcp://" + csebase.host + ":" + csebase.MQTTPort,
                MqttClient.generateClientId()
            )

            /* MQTT Subscribe */
            mqttClient!!.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d(TAG, "connectionLost")
                    if(App.isConnected) mqttClient!!.reconnect()
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.d(TAG, "messageArrived")

                    /* Write Task to be done -> Update Presence data into room database */
                    Log.d(TAG, "Message Content: $message")

                    /* Json Type Response Parsing */
                    val retrqi = MqttClientRequestParser.notificationJsonParse(message.toString())
                    Log.d(TAG, "RQI[$retrqi]")
                    val responseMessage = MqttClientRequest.notificationResponse(retrqi)
                    Log.d(TAG, "Recv OK ResMessage [$responseMessage]")

                    /* Make json for MQTT Response Message */
                    val res_message = MqttMessage(responseMessage.toByteArray())

                    try {
                        mqttClient!!.publish(mqttRespTopic, res_message)
                    } catch (e: MqttException) {
                        e.printStackTrace()
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) { Log.d(TAG, "deliveryComplete") }
            })

            try {
                val mqttConnectOptions = MqttConnectOptions()
                mqttConnectOptions.isCleanSession = true
                mqttConnectOptions.isAutomaticReconnect = true
                val token = mqttClient!!.connect(mqttConnectOptions)
                token.actionCallback = object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "onSuccess")
                        val payload = ""
                        val mqttQos = 1 /* 0: NO QoS, 1: No Check , 2: Each Check */

                        val message = MqttMessage(payload.toByteArray())
                        try {
                            mqttClient!!.subscribe(mqttReqTopic, mqttQos)
                        } catch (e: MqttException) {
                            e.printStackTrace()
                        }
                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG, "onFailure")
                    }
                }
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    companion object{
        private const val TAG = "MQTT_SERVICE"
    }
}