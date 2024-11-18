package kr.re.keti.mobiussampleapp_v25.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import info.mqtt.android.service.MqttAndroidClient
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

class MqttService(private val mqttReqTopic: String, private val mqttRespTopic: String): Service() {
    private var mqttClient: MqttAndroidClient? = null

    override fun onCreate() {
        super.onCreate()
        createMQTT(true, mqttReqTopic, mqttRespTopic)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        createMQTT(false, mqttReqTopic, mqttRespTopic)
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
                override fun connectionLost(cause: Throwable?) { Log.d(TAG, "connectionLost") }
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
                val token = mqttClient!!.connect(MqttConnectOptions())
                token.actionCallback = object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "onSuccess")
                        val payload = ""
                        val mqttQos = 1 /* 0: NO QoS, 1: No Check , 2: Each Check */

                        val message = MqttMessage(payload.toByteArray())
                        Log.d(TAG, "${message}")
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
                throw e
            }
        } else {
            /* MQTT unSubscribe or Client Close */
            if(mqttClient!!.isConnected){
                try {
                    val token = mqttClient!!.disconnect()
                    token.actionCallback = object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(TAG, "Successfully Disconnected!")
                        }
                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.d(TAG, "Failed to disconnect from server: ${exception?.cause}, ${exception?.message}")
                        }
                    }
                } catch (e: MqttException){
                    e.printStackTrace()
                }
            }
        }
    }
    /* MQTT Reconnection */
    fun reConnectMQTT() {
        if(mqttClient!!.isConnected) return
        try{
            mqttClient!!.reconnect()
        } catch (e: MqttException){
            e.printStackTrace()
        }
    }

    companion object{
        private const val TAG = "MQTT_SERVICE"
    }
}