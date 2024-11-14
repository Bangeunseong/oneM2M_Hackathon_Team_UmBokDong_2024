package kr.re.keti.mobiussampleapp_v25.layouts

import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.google.android.gms.maps.OnMapReadyCallback
import info.mqtt.android.service.MqttAndroidClient
import kr.re.keti.mobiussampleapp_v25.data.ContentInstanceObject
import kr.re.keti.mobiussampleapp_v25.data.ContentSubscribeObject
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityDeviceControlBinding
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
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

class DeviceControlActivity: AppCompatActivity(), OnMapReadyCallback {
    private var _binding: ActivityDeviceControlBinding? = null
    private val binding get() = _binding!!
    private val locationSource = DeviceLocationSource()
    private val mutableLiveData = MutableLiveData<Pair<Float, Float>>()

    private var gpsMqttClient: MqttAndroidClient? = null
    private var presMqttClient: MqttAndroidClient? = null
    private var MQTT_Req_Topic = ""
    private var MQTT_Resp_Topic = ""

    private var handler = Handler()
    private var isOpened = false
    private lateinit var deviceAEName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeviceControlBinding.inflate(layoutInflater)
        deviceAEName = intent.getStringExtra("SERVICE_AE").toString()
        MQTT_Req_Topic = intent.getStringExtra("MQTT_REQ_TOPIC").toString()
        MQTT_Resp_Topic = intent.getStringExtra("MQTT_RESP_TOPIC").toString()

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        binding.imageButton.setOnClickListener {
            if(isOpened) createPresMQTT(false, serviceAEName = deviceAEName+"_pres")
            else createPresMQTT(true, serviceAEName = deviceAEName+"_pres")
        }
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        //googleMap.setLocationSource(locationSource)
    }

    internal inner class DeviceLocationSource: LocationSource{
        private var listener: OnLocationChangedListener? = null

        override fun activate(listener: OnLocationChangedListener) {
            this.listener = listener
            val location = Location("RealTimeLocationProvider")
            location.latitude = 37.1
            location.longitude = 32.0
            location.accuracy = 100F
            listener.onLocationChanged(location)
        }

        override fun deactivate() {
            listener = null
        }
    }

    // --- MQTT Functions ---
    /* MQTT Subscription */
    private fun createGPSMQTT(mqttStart: Boolean, serviceAEName: String) {
        if (mqttStart && gpsMqttClient == null) {
            /* Subscription Resource Create to Yellow Turtle */
            val subcribeResource = SubscribeResource(serviceAEName)
            subcribeResource.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    handler.post {
                        Log.d(TAG,"**** Subscription Resource Create 요청 결과 ****\r\n\r\n$msg")
                    }
                }
            })
            subcribeResource.start()

            /* MQTT Subscribe */
            gpsMqttClient = MqttAndroidClient(
                this.applicationContext,
                "tcp://" + csebase.host + ":" + csebase.MQTTPort,
                MqttClient.generateClientId()
            )
            gpsMqttClient!!.setCallback(gpsMqttCallback)
            try {
                val token = gpsMqttClient!!.connect()
                token.actionCallback = gpsIMqttActionListener
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            /* MQTT unSubscribe or Client Close */
            // mqttClient!!.setCallback(null)
            if(gpsMqttClient != null)
                gpsMqttClient!!.disconnect()
            gpsMqttClient = null
        }
    }
    /* MQTT Listener */
    private val gpsIMqttActionListener: IMqttActionListener = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            Log.d(TAG, "onSuccess")
            val payload = ""
            val mqttQos = 1 /* 0: NO QoS, 1: No Check , 2: Each Check */

            val message = MqttMessage(payload.toByteArray())
            Log.d(TAG, "${message}")
            try {
                gpsMqttClient!!.subscribe(MQTT_Req_Topic, mqttQos)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            Log.d(TAG, "onFailure")
        }
    }
    /* MQTT Broker Message Received */
    private val gpsMqttCallback: MqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable) {
            Log.d(TAG, "connectionLost")
        }

        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            Log.d(TAG, "messageArrived")

            //binding.textViewData.text = ""
            //binding.textViewData.text = """
            //    **** MQTT CO2 실시간 조회 ****

            //    ${message.toString().replace(",".toRegex(), "\n")}
            //    """.trimIndent()
            Log.d(TAG, "Notify ResMessage:$message")

            /* Json Type Response Parsing */
            val retrqi = MqttClientRequestParser.notificationJsonParse(message.toString())
            Log.d(TAG, "RQI[$retrqi]")

            val responseMessage = MqttClientRequest.notificationResponse(retrqi)
            Log.d(TAG, "Recv OK ResMessage [$responseMessage]")

            /* Make json for MQTT Response Message */
            val res_message = MqttMessage(responseMessage!!.toByteArray())

            try {
                gpsMqttClient!!.publish(MQTT_Resp_Topic, res_message)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken) {
            Log.d(TAG, "deliveryComplete")
        }
    }

    /* MQTT Subscription */
    private fun createPresMQTT(mqttStart: Boolean, serviceAEName: String) {
        if (mqttStart && presMqttClient == null) {
            /* Subscription Resource Create to Yellow Turtle */
            val subcribeResource = SubscribeResource(serviceAEName)
            subcribeResource.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    handler.post {
                        Log.d(TAG,"**** Subscription Resource Create 요청 결과 ****\r\n\r\n$msg")
                    }
                }
            })
            subcribeResource.start()

            /* MQTT Subscribe */
            presMqttClient = MqttAndroidClient(
                this.applicationContext,
                "tcp://" + csebase.host + ":" + csebase.MQTTPort,
                MqttClient.generateClientId()
            )
            presMqttClient!!.setCallback(presMqttCallback)
            try {
                val token = presMqttClient!!.connect()
                token.actionCallback = presIMqttActionListener
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            /* MQTT unSubscribe or Client Close */
            // mqttClient!!.setCallback(null)
            if(presMqttClient != null)
                presMqttClient!!.disconnect()
            presMqttClient = null
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
                presMqttClient!!.subscribe(MQTT_Req_Topic, mqttQos)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            Log.d(TAG, "onFailure")
        }
    }
    /* MQTT Broker Message Received */
    private val presMqttCallback: MqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable) {
            Log.d(TAG, "connectionLost")
        }

        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            Log.d(TAG, "messageArrived")

            //binding.textViewData.text = ""
            //binding.textViewData.text = """
            //    **** MQTT CO2 실시간 조회 ****

            //    ${message.toString().replace(",".toRegex(), "\n")}
            //    """.trimIndent()
            Log.d(TAG, "Notify ResMessage:$message")

            /* Json Type Response Parsing */
            val retrqi = MqttClientRequestParser.notificationJsonParse(message.toString())
            Log.d(TAG, "RQI[$retrqi]")

            val responseMessage = MqttClientRequest.notificationResponse(retrqi)
            Log.d(TAG, "Recv OK ResMessage [$responseMessage]")

            /* Make json for MQTT Response Message */
            val res_message = MqttMessage(responseMessage!!.toByteArray())

            try {
                presMqttClient!!.publish(MQTT_Resp_Topic, res_message)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken) {
            Log.d(TAG, "deliveryComplete")
        }
    }
    // ----------------------

    // --- Retrieve Actions ---
    /* Retrieve Sensor Data */
    internal inner class RetrieveRequest : Thread {
        private val LOG: Logger = Logger.getLogger(
            RetrieveRequest::class.java.name
        )
        private var receiver: IReceived? = null
        private var ContainerName = "cnt-co2"
        private var serviceAEName = ""

        constructor(containerName: String, serviceAEName: String) {
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
    // -----------------------

    // --- Control Actions ---
    /* Request Control LED */
    internal inner class ControlRequest(serviceAEName: String, containerName: String, comm: String) : Thread() {
        private val LOG: Logger = Logger.getLogger(
            ControlRequest::class.java.name
        )
        private var receiver: IReceived? = null
        private var containerName = ""
        private var serviceAEName = ""

        var contentInstance: ContentInstanceObject = ContentInstanceObject()

        init {
            this.containerName = containerName
            this.serviceAEName = serviceAEName
            contentInstance.setContent(comm)
        }

        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb = csebase.serviceUrl + "/" + serviceAEName + "/" + containerName

                val mUrl = URL(sb)

                val conn = mUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doInput = true
                conn.doOutput = true
                conn.useCaches = false
                conn.instanceFollowRedirects = false

                conn.setRequestProperty("Accept", "application/xml")
                conn.setRequestProperty("Content-Type", "application/vnd.onem2m-res+xml;ty=4")
                conn.setRequestProperty("locale", "ko")
                conn.setRequestProperty("X-M2M-RI", "12345")
                conn.setRequestProperty("X-M2M-Origin", ae.aEid)

                val reqContent = contentInstance.makeXML()
                conn.setRequestProperty("Content-Length", reqContent.length.toString())

                val dos = DataOutputStream(conn.outputStream)
                dos.write(reqContent.toByteArray())
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
    // ----------------------

    // --- Subscription Actions ---
    /* Subscribe Co2 Content Resource */
    internal inner class SubscribeResource(private val serviceAEName: String) : Thread() {
        private val LOG: Logger = Logger.getLogger(
            SubscribeResource::class.java.name
        )
        private var receiver: IReceived? = null
        private val container_name = "DATA" //change to control container name

        var subscribeInstance: ContentSubscribeObject = ContentSubscribeObject()

        init {
            subscribeInstance.setUrl(csebase.host)
            subscribeInstance.setResourceName(ae.aEid + "_rn")
            subscribeInstance.setPath(ae.aEid + "_sub")
            subscribeInstance.setOrigin_id(ae.aEid)
        }

        fun setReceiver(hanlder: IReceived?) {
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
        private const val TAG = "DEVICE_CONTROL_ACTIVITY"
    }
}