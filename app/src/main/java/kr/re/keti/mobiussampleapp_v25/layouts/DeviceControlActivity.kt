package kr.re.keti.mobiussampleapp_v25.layouts

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.LocationSource
import com.google.android.gms.maps.LocationSource.OnLocationChangedListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.re.keti.mobiussampleapp_v25.App
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.data.ContentInstanceObject
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityDeviceControlBinding
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.ae
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.csebase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived
import kr.re.keti.mobiussampleapp_v25.utils.ParseElementXml
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
    private val mutableLocationData = MutableLiveData<Pair<Double, Double>>()

    private var MQTT_Req_Topic = ""
    private var MQTT_Resp_Topic = ""
    private var isOpened = false
    private val job = Job()
    private val locationSource = DeviceLocationSource()
    private val markers = mutableListOf<MarkerOptions>()

    private lateinit var deviceAEName: String
    private lateinit var db: RegisteredAEDatabase
    private lateinit var callback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDeviceControlBinding.inflate(layoutInflater)
        val bundle = intent.extras

        if (bundle != null) {
            deviceAEName = bundle.getString("SERVICE_AE").toString()
            MQTT_Req_Topic = bundle.getString("MQTT_REQ_TOPIC").toString()
            MQTT_Resp_Topic = bundle.getString("MQTT_RESP_TOPIC").toString()
        } else{
            Log.d(TAG, "Bundle is null!")
            setResult(RESULT_CANCELED)
            finish()
        }
        db = RegisteredAEDatabase.getInstance(applicationContext)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        binding.imageButton.setOnClickListener {
            isOpened = !isOpened
            if(!isOpened) {
                binding.imageButton.setImageResource(R.drawable.ic_arrow_up)
                ObjectAnimator.ofFloat(binding.deviceControlLayout, "translationY", (346f * Resources.getSystem().displayMetrics.density + 0.5f)).apply {
                    duration = 1000
                    start()
                }
            }
            else {
                binding.imageButton.setImageResource(R.drawable.ic_arrow_down)
                ObjectAnimator.ofFloat(binding.deviceControlLayout, "translationY", (0f * Resources.getSystem().displayMetrics.density + 0.5f)).apply {
                    duration = 1000
                    start()
                }
            }
        }
        binding.ledSwitch.setOnCheckedChangeListener { _, isChecked ->
            val req = ControlRequest(deviceAEName+"_led", "COMMAND", if (isChecked) "1" else "0")
            req.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    Log.d(TAG, "************** LED Light Control *************\r\n\r\n$msg")
                    val pxml = ParseElementXml()
                    CoroutineScope(Dispatchers.IO).launch {
                        val registeredAE = db.registeredAEDAO().get(deviceAEName)
                        Log.d(TAG, "RegisteredAE: ${registeredAE!!.isLedTurnedOn}")
                        registeredAE.isLedTurnedOn = pxml.GetElementXml(msg, "con") != "0"
                        db.registeredAEDAO().update(registeredAE)
                    }
                }
            })
            req.start()
        }
        binding.lockSwitch.setOnCheckedChangeListener { _, isChecked ->
            val req = ControlRequest(deviceAEName+"_lock", "COMMAND", if (isChecked) "1" else "0")
            req.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    Log.d(TAG, "************** Lock Control *************\r\n\r\n$msg")
                    val pxml = ParseElementXml()
                    CoroutineScope(Dispatchers.IO).launch {
                        val registeredAE = db.registeredAEDAO().get(deviceAEName)
                        Log.d(TAG, "RegisteredAE: ${registeredAE!!.isLocked}")
                        registeredAE.isLocked = pxml.GetElementXml(msg, "con") != "0"
                        binding.lockSwitch.text = if(registeredAE.isLocked) "Locked" else "Unlocked"
                        db.registeredAEDAO().update(registeredAE)
                    }
                }
            })
            req.start()
        }
        binding.buzSwitch.setOnCheckedChangeListener { _, isChecked ->
            val req = ControlRequest(deviceAEName+"_buz", "COMMAND", if (isChecked) "1" else "0")
            req.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    Log.d(TAG, "************** Buzzer Control *************\r\n\r\n$msg")
                    val pxml = ParseElementXml()
                    CoroutineScope(Dispatchers.IO).launch {
                        val registeredAE = db.registeredAEDAO().get(deviceAEName)
                        Log.d(TAG, "RegisteredAE: ${registeredAE!!.isBuzTurnedOn}")
                        registeredAE.isBuzTurnedOn = pxml.GetElementXml(msg, "con") != "0"
                        db.registeredAEDAO().update(registeredAE)
                    }
                }
            })
            req.start()
        }
        // When location resource arrived this function activates
        mutableLocationData.observe(this) {
            val location = it
            locationSource.updateLocation(location)
            binding.mapView.getMapAsync { googleMap ->
                val cameraPosition = googleMap.cameraPosition
                googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(
                            LatLng(location.first, location.second),
                            17f,
                            cameraPosition.tilt,
                            cameraPosition.bearing
                        )
                    )
                )
            }
        }

        // For retrieving resource content from room database
        CoroutineScope(Dispatchers.IO + job).launch {
            getDeviceStatus()
            while(true) {
                getAnomalyDetection()
                getGPSLocation()
                delay(5000)
            }
        }
        setContentView(binding.root)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        callback = object: OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                val bundle = intent.extras
                CoroutineScope(Dispatchers.IO).launch {
                    bundle!!.putParcelable("SERVICE_AE_OBJECT", db.registeredAEDAO().get(deviceAEName))
                }.invokeOnCompletion {
                    intent.putExtras(bundle!!)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
        if(job.isCancelled) job.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
        if(!job.isCompleted) job.cancel()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if(job.isCancelled) job.start()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if(!job.isCompleted) job.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
        if(!job.isCompleted) job.cancel()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        googleMap.setLocationSource(locationSource)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        googleMap.isMyLocationEnabled = true
    }

    // Retrieve Actuators status
    private suspend fun getDeviceStatus() = coroutineScope {
        val pxml = ParseElementXml()
        val data = async { db.registeredAEDAO().get(deviceAEName) }
        val led = async {
            var isLedOn = false
            val reqLed = RetrieveRequest(deviceAEName+"_led", "DATA")
            reqLed.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    isLedOn = pxml.GetElementXml(msg, "con") != "0"
                }
            })
            reqLed.start(); reqLed.join()
            isLedOn
        }
        val buz = async {
            var isBuzTurnedOn = false
            val reqBuz = RetrieveRequest(deviceAEName+"_buz", "DATA")
            reqBuz.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    isBuzTurnedOn = pxml.GetElementXml(msg, "con") != "0"
                }
            })
            reqBuz.start(); reqBuz.join()
            isBuzTurnedOn
        }
        val lock = async {
            var isLocked = false
            val reqLock = RetrieveRequest(deviceAEName+"_lock", "DATA")
            reqLock.setReceiver(object : IReceived {
                override fun getResponseBody(msg: String) {
                    isLocked = pxml.GetElementXml(msg, "con") != "0"
                }
            })
            reqLock.start(); reqLock.join()
            isLocked
        }

        withContext(Dispatchers.Main){
            // Asynchronous Data Retrieval Process
            val registeredAE = data.await()
            val isLedOn = led.await()
            val isBuzTurnedOn = buz.await()
            val isLocked = lock.await()

            // Change UI
            binding.ledSwitch.isChecked = isLedOn
            binding.buzSwitch.isChecked = isBuzTurnedOn
            binding.lockSwitch.isChecked = isLocked
            binding.textView2.text = if(isLocked) "Locked" else "Unlocked"

            // Update Database
            registeredAE!!.isLedTurnedOn = isLedOn
            registeredAE.isLocked = isLocked
            registeredAE.isBuzTurnedOn = isBuzTurnedOn
            binding.lockSwitch.text = if(isLocked) "Locked" else "Unlocked"
            db.registeredAEDAO().update(registeredAE)
        }
    }
    // Retrieve Anomaly Detection State
    private suspend fun getAnomalyDetection() = coroutineScope {
        val data = async { db.registeredAEDAO().get(deviceAEName) }

        withContext(Dispatchers.Main) {
            val registeredAE = data.await()
            Log.d(TAG, "Got Information from Database: ${registeredAE!!.applicationName} -> ${registeredAE.isTriggered}")

            if(registeredAE.isTriggered) {
                binding.mapView.getMapAsync {
                    val location = App.detectedLocation
                    val marker = MarkerOptions()
                    if(location != null){
                        marker.position(LatLng(location.first, location.second))
                        marker.snippet("Location: ${String.format("%.3f",location.first)}, ${String.format("%.3f", location.second)}")
                        marker.title("Anomaly Detected!")
                        markers.add(marker)
                        it.addMarker(marker)
                    } else{
                        Toast.makeText(this@DeviceControlActivity, "Anomaly is detected, but cannot find its location!",Toast.LENGTH_SHORT).show()
                    }
                }
                binding.imageView3.setImageResource(R.drawable.icon_warning)
                binding.textView2.text = "Anomaly Detected!"
            } else{
                binding.imageView3.setImageResource(R.drawable.icon_check)
                binding.textView2.text = "No Anomaly Detected!"
            }
        }
    }
    // Retrieve GPS Location of device
    private suspend fun getGPSLocation() = coroutineScope{
        val reqLocation = RetrieveRequest(deviceAEName+"_gps", "DATA")
        reqLocation.setReceiver(object : IReceived {
            override fun getResponseBody(msg: String) {
                val pxml = ParseElementXml()
                val latitude = pxml.GetElementXml(msg, "latitude").toDouble()
                val longitude = pxml.GetElementXml(msg, "longitude").toDouble()
                mutableLocationData.postValue(Pair(latitude, longitude))
            }
        })
        reqLocation.start()
        // mutableLocationData.postValue(Pair(Math.random()+37, Math.random()+112))
    }

    /* Set Location Source of GoogleMap */
    internal inner class DeviceLocationSource: LocationSource{
        private var listener: OnLocationChangedListener? = null

        override fun activate(p0: OnLocationChangedListener) {
            this.listener = p0
        }

        fun updateLocation(loc: Pair<Double, Double>){
            val location = Location("Mobius")
            location.latitude = loc.first
            location.longitude = loc.second
            location.accuracy = 50.0f
            listener?.onLocationChanged(location)
        }

        override fun deactivate() {
            listener = null
        }
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

    companion object{
        private const val TAG = "DEVICE_CONTROL_ACTIVITY"
    }
}