package kr.re.keti.mobiussampleapp_v25

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import kr.re.keti.mobiussampleapp_v25.data_objects.AE
import kr.re.keti.mobiussampleapp_v25.data_objects.ApplicationEntityObject
import kr.re.keti.mobiussampleapp_v25.data_objects.CSEBase
import kr.re.keti.mobiussampleapp_v25.data_objects.ContentInstanceObject
import kr.re.keti.mobiussampleapp_v25.data_objects.ContentSubscribeObject
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityMainBinding
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequest
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequestParser
import kr.re.keti.mobiussampleapp_v25.utils.ParseElementXml
import info.mqtt.android.service.MqttAndroidClient
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

// TODO: WorkManager를 이용한 MQTT connection 열기 및 데이터 Retrieve(비동기적 데이터 가져오기 위함) -> Trigger Theft Notification
class MainActivity : AppCompatActivity(), View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    // Field
    private var _binding: ActivityMainBinding? = null
    var handler: Handler = Handler()

    private val MQTTPort = "1883"
    private val ServiceAEName = "ae-edu1"
    private var MQTT_Req_Topic = ""
    private var MQTT_Resp_Topic = ""
    private var mqttClient: MqttAndroidClient? = null
    private var Mobius_Address = ""
    private val binding get() = _binding!!

    /* onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetrieve.setOnClickListener(this)
        binding.switchMqtt.setOnCheckedChangeListener(this)
        binding.btnControlGreen.setOnClickListener(this)
        binding.btnControlBlue.setOnClickListener(this)
        binding.toggleButtonAddr.setOnClickListener(this)

        binding.btnRetrieve.visibility = View.INVISIBLE
        binding.switchMqtt.visibility = View.INVISIBLE
        binding.btnControlGreen.visibility = View.INVISIBLE
        binding.btnControlBlue.visibility = View.INVISIBLE

        binding.toggleButtonAddr.isFocusable = true

        // Create AE and Get AEID
        // GetAEInfo();

        // Check Permission

    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
    }

    /* AE Create for Androdi AE */
    fun GetAEInfo() {
        Mobius_Address = binding.editText.text.toString()

        csebase.setInfo(Mobius_Address, "7579", "Mobius", "1883")

        //csebase.setInfo("203.253.128.151","7579","Mobius","1883");
        // AE Create for Android AE
        ae.setAppName("ncubeapp")
        val aeCreate = aeCreateRequest()
        aeCreate.setReceiver(object : IReceived {
            override fun getResponseBody(msg: String) {
                handler.post {
                    Log.d(TAG, "** AE Create ResponseCode[$msg]")
                    if (msg.toInt() == 201) {
                        MQTT_Req_Topic = "/oneM2M/req/Mobius2/" + ae.aEid + "_sub" + "/#"
                        MQTT_Resp_Topic = "/oneM2M/resp/Mobius2/" + ae.aEid + "_sub" + "/json"
                        Log.d(TAG, "ReqTopic[$MQTT_Req_Topic]")
                        Log.d(TAG, "ResTopic[$MQTT_Resp_Topic]")
                    } else { // If AE is Exist , GET AEID
                        val aeRetrive = aeRetrieveRequest()
                        aeRetrive.setReceiver(object : IReceived {
                            override fun getResponseBody(resmsg: String) {
                                handler.post {
                                    Log.d(TAG, "** AE Retrive ResponseCode[$resmsg]")
                                    MQTT_Req_Topic =
                                        "/oneM2M/req/Mobius2/" + ae.aEid + "_sub" + "/#"
                                    MQTT_Resp_Topic =
                                        "/oneM2M/resp/Mobius2/" + ae.aEid + "_sub" + "/json"
                                    Log.d(TAG, "ReqTopic[$MQTT_Req_Topic]")
                                    Log.d(TAG, "ResTopic[$MQTT_Resp_Topic]")
                                }
                            }
                        })
                        aeRetrive.start()
                    }
                }
            }
        })
        aeCreate.start()
    }

    /* Switch - Get Co2 Data With MQTT */
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            Log.d(TAG, "MQTT Create")
            MQTT_Create(true)
        } else {
            Log.d(TAG, "MQTT Close")
            MQTT_Create(false)
        }
    }

    /* MQTT Subscription */
    fun MQTT_Create(mtqqStart: Boolean) {
        if (mtqqStart && mqttClient == null) {
            /* Subscription Resource Create to Yellow Turtle */
            val subcribeResource = SubscribeResource()
            subcribeResource.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    handler.post {
                        binding.textViewData.text = "**** Subscription Resource Create 요청 결과 ****\r\n\r\n$msg"
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
            mqttClient!!.setCallback(mainMqttCallback)
            try {
                val token = mqttClient!!.connect()
                token.actionCallback = mainIMqttActionListener
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        } else {
            /* MQTT unSubscribe or Client Close */
            //mqttClient!!.setCallback(null)
            mqttClient!!.close()
            mqttClient = null
        }
    }

    /* MQTT Listener */
    private val mainIMqttActionListener: IMqttActionListener = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken) {
            Log.d(TAG, "onSuccess")
            val payload = ""
            val mqttQos = 1 /* 0: NO QoS, 1: No Check , 2: Each Check */

            val message = MqttMessage(payload.toByteArray())
            try {
                mqttClient!!.subscribe(MQTT_Req_Topic, mqttQos)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
            Log.d(TAG, "onFailure")
        }
    }

    /* MQTT Broker Message Received */
    private val mainMqttCallback: MqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable) {
            Log.d(TAG, "connectionLost")
        }

        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            Log.d(TAG, "messageArrived")

            binding.textViewData.text = ""
            binding.textViewData.text = """
                **** MQTT CO2 실시간 조회 ****
                
                ${message.toString().replace(",".toRegex(), "\n")}
                """.trimIndent()
            Log.d(TAG, "Notify ResMessage:$message")

            /* Json Type Response Parsing */
            val retrqi = MqttClientRequestParser.notificationJsonParse(message.toString())
            Log.d(TAG, "RQI[$retrqi]")

            val responseMessage = MqttClientRequest.notificationResponse(retrqi)
            Log.d(TAG, "Recv OK ResMessage [$responseMessage]")

            /* Make json for MQTT Response Message */
            val res_message = MqttMessage(responseMessage!!.toByteArray())

            try {
                mqttClient!!.publish(MQTT_Resp_Topic, res_message)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken) {
            Log.d(TAG, "deliveryComplete")
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnRetrieve -> {
                val req = RetrieveRequest()
                binding.textViewData.text = ""
                req.setReceiver(object : IReceived {
                    override fun getResponseBody(msg: String) {
                        handler.post {
                            binding.textViewData.text = "************** CO2 조회 *************\r\n\r\n$msg"
                        }
                    }
                })
                req.start()
            }

            R.id.btnControl_Green -> {
                if ((v as ToggleButton).isChecked) {
                    val req = ControlRequest("1")
                    req.setReceiver(object : IReceived {
                        override fun getResponseBody(msg: String) {
                            handler.post {
                                binding.textViewData.text =
                                    "************** LED Green 제어(켜짐) *************\r\n\r\n$msg"
                            }
                        }
                    })
                    req.start()
                } else {
                    val req = ControlRequest("2")
                    req.setReceiver(object : IReceived {
                        override fun getResponseBody(msg: String) {
                            handler.post {
                                binding.textViewData.text =
                                    "************** LED Green 제어(꺼짐) **************\r\n\r\n$msg"
                            }
                        }
                    })
                    req.start()
                }
            }

            R.id.btnControl_Blue -> {
                if ((v as ToggleButton).isChecked) {
                    val req = ControlRequest("3")
                    req.setReceiver(object : IReceived {
                        override fun getResponseBody(msg: String) {
                            handler.post {
                                binding.textViewData.text =
                                    "************** LED BLUE 제어(켜짐) *************\r\n\r\n$msg"
                            }
                        }
                    })
                    req.start()
                } else {
                    val req = ControlRequest("4")
                    req.setReceiver(object : IReceived {
                        override fun getResponseBody(msg: String) {
                            handler.post {
                                binding.textViewData.text =
                                    "************** LED BLUE 제어(꺼짐) **************\r\n\r\n$msg"
                            }
                        }
                    })
                    req.start()
                }
            }

            R.id.toggleButton_Addr -> {
                if ((v as ToggleButton).isChecked) {
                    binding.btnRetrieve.visibility = View.VISIBLE
                    binding.switchMqtt.visibility = View.VISIBLE
                    binding.btnControlGreen.visibility = View.VISIBLE
                    binding.btnControlBlue.visibility = View.VISIBLE

                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.editText.windowToken, 0) //hide keyboard

                    GetAEInfo()
                } else {
                    binding.btnRetrieve.visibility = View.INVISIBLE
                    binding.switchMqtt.visibility = View.INVISIBLE
                    binding.btnControlGreen.visibility = View.INVISIBLE
                    binding.btnControlBlue.visibility = View.INVISIBLE
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()

        if(checkSelfPermission(FILE_INTEGRITY_SERVICE) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(NOTIFICATION_SERVICE) == PackageManager.PERMISSION_DENIED){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Manifest.permission.USE_EXACT_ALARM),101)
            }
        }
    }

    public override fun onStop() {
        super.onStop()
    }

    /* Response callback Interface */
    interface IReceived {
        fun getResponseBody(msg: String)
    }

    /* Retrieve Co2 Sensor */
    internal inner class RetrieveRequest : Thread {
        private val LOG: Logger = Logger.getLogger(
            RetrieveRequest::class.java.name
        )
        private var receiver: IReceived? = null
        private var ContainerName = "cnt-co2"

        constructor(containerName: String) {
            this.ContainerName = containerName
        }

        constructor()

        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb =
                    csebase.serviceUrl + "/" + ServiceAEName + "/" + ContainerName + "/" + "latest"

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

    /* Request Control LED */
    internal inner class ControlRequest(comm: String) : Thread() {
        private val LOG: Logger = Logger.getLogger(
            ControlRequest::class.java.name
        )
        private var receiver: IReceived? = null
        private val container_name = "cnt-led"

        var contentinstance: ContentInstanceObject = ContentInstanceObject()

        init {
            contentinstance.setContent(comm)
        }

        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb = csebase.serviceUrl + "/" + ServiceAEName + "/" + container_name

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

                val reqContent = contentinstance.makeXML()
                conn.setRequestProperty("Content-Length", reqContent!!.length.toString())

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

    /* Request AE Creation */
    internal inner class aeCreateRequest : Thread() {
        private val LOG: Logger = Logger.getLogger(
            aeCreateRequest::class.java.name
        )
        var TAG: String = aeCreateRequest::class.java.name
        private var receiver: IReceived? = null
        var responseCode: Int = 0
        var applicationEntity: ApplicationEntityObject = ApplicationEntityObject()
        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        init {
            applicationEntity.setResourceName(ae.getappName())
        }

        override fun run() {
            try {
                val sb = csebase.serviceUrl
                val mUrl = URL(sb)

                val conn = mUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doInput = true
                conn.doOutput = true
                conn.useCaches = false
                conn.instanceFollowRedirects = false

                conn.setRequestProperty("Content-Type", "application/vnd.onem2m-res+xml;ty=2")
                conn.setRequestProperty("Accept", "application/xml")
                conn.setRequestProperty("locale", "ko")
                conn.setRequestProperty("X-M2M-Origin", "S" + ae.getappName())
                conn.setRequestProperty("X-M2M-RI", "12345")
                conn.setRequestProperty("X-M2M-NM", ae.getappName())

                val reqXml = applicationEntity.makeXML()
                conn.setRequestProperty("Content-Length", reqXml!!.length.toString())

                val dos = DataOutputStream(conn.outputStream)
                dos.write(reqXml.toByteArray())
                dos.flush()
                dos.close()

                responseCode = conn.responseCode

                var `in`: BufferedReader? = null
                var aei = ""
                if (responseCode == 201) {
                    // Get AEID from Response Data
                    `in` = BufferedReader(InputStreamReader(conn.inputStream))

                    var resp = ""
                    var strLine: String
                    while ((`in`.readLine().also { strLine = it }) != null) {
                        resp += strLine
                    }

                    val pxml = ParseElementXml()
                    aei = pxml.GetElementXml(resp, "aei")
                    ae.aEid = aei
                    Log.d(TAG, "Create Get AEID[$aei]")
                    `in`.close()
                }
                if (responseCode != 0) {
                    receiver!!.getResponseBody(responseCode.toString())
                }
                conn.disconnect()
            } catch (exp: Exception) {
                LOG.log(Level.SEVERE, exp.message)
            }
        }
    }

    /* Retrieve AE-ID */
    internal inner class aeRetrieveRequest : Thread() {
        private val LOG: Logger = Logger.getLogger(
            aeCreateRequest::class.java.name
        )
        private var receiver: IReceived? = null
        var responseCode: Int = 0

        fun setReceiver(hanlder: IReceived?) {
            this.receiver = hanlder
        }

        override fun run() {
            try {
                val sb = csebase.serviceUrl + "/" + ae.getappName()
                val mUrl = URL(sb)

                val conn = mUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.doInput = true
                conn.doOutput = false

                conn.setRequestProperty("Accept", "application/xml")
                conn.setRequestProperty("X-M2M-RI", "12345")
                conn.setRequestProperty("X-M2M-Origin", "Sandoroid")
                conn.setRequestProperty("nmtype", "short")
                conn.connect()

                responseCode = conn.responseCode

                var `in`: BufferedReader? = null
                var aei = ""
                if (responseCode == 200) {
                    // Get AEID from Response Data
                    `in` = BufferedReader(InputStreamReader(conn.inputStream))

                    var resp = ""
                    var strLine: String
                    while ((`in`.readLine().also { strLine = it }) != null) {
                        resp += strLine
                    }

                    val pxml = ParseElementXml()
                    aei = pxml.GetElementXml(resp, "aei")
                    ae.aEid = aei
                    //Log.d(TAG, "Retrieve Get AEID[" + aei + "]");
                    `in`.close()
                }
                if (responseCode != 0) {
                    receiver!!.getResponseBody(responseCode.toString())
                }
                conn.disconnect()
            } catch (exp: Exception) {
                LOG.log(Level.SEVERE, exp.message)
            }
        }
    }

    /* Subscribe Co2 Content Resource */
    internal inner class SubscribeResource : Thread() {
        private val LOG: Logger = Logger.getLogger(
            SubscribeResource::class.java.name
        )
        private var receiver: IReceived? = null
        private val container_name = "cnt-co2" //change to control container name

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
                val sb = csebase.serviceUrl + "/" + ServiceAEName + "/" + container_name

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

    companion object {
        private val csebase = CSEBase()
        private val ae = AE()
        private const val TAG = "MainActivity"
    }
}
