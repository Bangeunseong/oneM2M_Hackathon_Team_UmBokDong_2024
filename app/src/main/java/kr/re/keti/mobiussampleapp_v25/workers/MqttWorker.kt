package kr.re.keti.mobiussampleapp_v25.workers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.re.keti.mobiussampleapp_v25.data.ContentSubscribeObject
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.ae
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.csebase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived
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
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

// TODO: Make things Work!!!
class MqttWorker(context: Context, workerParams: WorkerParameters,
                 mqttReqTopic: String, mqttRespTopic: String) : Worker(context, workerParams) {
    private var mqttClient: MqttAndroidClient? = null
    private var handler = Handler(Looper.myLooper()!!)
    private var isSuccessful = false
    private lateinit var db: RegisteredAEDatabase

    override fun doWork(): Result {
        db = RegisteredAEDatabase.getInstance(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            val deviceList = db.registeredAEDAO().getAll()
            for(device in deviceList){
                createPresMQTT(true, device.applicationName)
            }
        }.invokeOnCompletion {

        }
        return Result.success()
    }

    // --- MQTT Service ---
    /* MQTT Subscription */
    private fun createPresMQTT(mqttStart: Boolean, serviceAEName: String) {
        if (mqttStart && mqttClient == null) {
            /* Subscription Resource Create to Yellow Turtle */
            val subcribeResource = CreateSubscribeResource(serviceAEName)
            subcribeResource.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    handler.post {
                        Log.d(TAG,"**** Subscription Resource Create 요청 결과 ****\r\n\r\n$msg")
                    }
                }
            })
            subcribeResource.start()

            /* MQTT Subscribe */
            mqttClient = MqttAndroidClient(
                this.applicationContext,
                "tcp://" + csebase.host + ":" + csebase.MQTTPort,
                MqttClient.generateClientId()
            )
            mqttClient!!.setCallback(presMqttCallback)
            try {
                val token = mqttClient!!.connect(MqttConnectOptions())
                token.actionCallback = presIMqttActionListener
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            /* MQTT unSubscribe or Client Close */
            mqttClient?.close()
            mqttClient = null
        }
    }
    /* MQTT Listener */
    private val presIMqttActionListener: IMqttActionListener = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
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

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            mqttClient!!.reconnect()
            Log.d(TAG, "onFailure")
        }
    }
    /* MQTT Broker Message Received */
    private val presMqttCallback: MqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable) {
            Log.d(TAG, "connectionLost")
        }

        //@Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            Log.d(TAG, "messageArrived")

            /* Write Task to be done */
            // ->

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
                Log.d(TAG, "${e.message}")
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken) {
            Log.d(TAG, "deliveryComplete")
        }
    }
    // ------------------------

    // --- Subscription Actions ---
    /* Subscribe Co2 Content Resource */
    internal inner class CreateSubscribeResource(private val serviceAEName: String) : Thread() {
        private val LOG: Logger = Logger.getLogger(
            CreateSubscribeResource::class.java.name
        )
        private var receiver: kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived? = null
        private val container_name = "DATA" //change to control container name

        var subscribeInstance: ContentSubscribeObject = ContentSubscribeObject()

        init {
            subscribeInstance.setUrl(csebase.host)
            subscribeInstance.setResourceName(ae.aEid + "_rn")
            subscribeInstance.setPath(ae.aEid + "_sub")
            subscribeInstance.setOrigin_id(ae.aEid)
        }

        fun setReceiver(hanlder: kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb = csebase.serviceUrl + "/" + serviceAEName + "/" + container_name

                val mUrl = URL(sb)

                val conn = mUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doInput = true
                conn.doOutput = true
                conn.useCaches = false
                conn.instanceFollowRedirects = false

                conn.setRequestProperty("Accept", "application/xml")
                conn.setRequestProperty("Content-Type", "application/vnd.onem2m-res+xml; ty=23")
                conn.setRequestProperty("locale", "ko")
                conn.setRequestProperty("X-M2M-RI", "12345")
                conn.setRequestProperty("X-M2M-Origin", ae.aEid)

                val reqmqttContent = subscribeInstance.makeXML()
                conn.setRequestProperty("Content-Length", reqmqttContent.length.toString())

                val dos = DataOutputStream(conn.outputStream)
                dos.write(reqmqttContent.toByteArray())
                dos.flush()
                dos.close()

                val `in` = BufferedReader(InputStreamReader(conn.inputStream))

                var resp = ""
                var strLine = ""
                while ((`in`.readLine().also { strLine = it }) != null) {
                    resp += strLine
                }

                if (resp !== "") {
                    receiver!!.getResponseBody(resp)
                }
                conn.disconnect()
            } catch (exp: Exception) {
                LOG.log(Level.SEVERE, exp.message)
            }
        }
    }
    // ----------------------------

    companion object{
        private const val TAG = "MQTT_WORKER"
    }
}