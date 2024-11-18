package kr.re.keti.mobiussampleapp_v25.layouts

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationBarView
import kr.re.keti.mobiussampleapp_v25.data.AE
import kr.re.keti.mobiussampleapp_v25.data.ApplicationEntityObject
import kr.re.keti.mobiussampleapp_v25.data.CSEBase
import kr.re.keti.mobiussampleapp_v25.utils.ParseElementXml
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.data.ContentSubscribeObject
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityMainBinding
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import timber.log.Timber
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

class MainActivity : AppCompatActivity() {
    // Field
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private var _navHostFragment: NavHostFragment? = null
    private val navHostFragment get() = _navHostFragment!!
    private var _navController: NavController? = null
    private val navController get() = _navController!!
    private var _appBarConfiguration: AppBarConfiguration? = null
    private val appBarConfiguration get() = _appBarConfiguration!!

    private var handler: Handler = Handler(Looper.myLooper()!!)

    private val MQTTPort = "1883"
    private var Mobius_Address = ""

    private val viewModel: MainViewModel by viewModels()

    /* Device Addition Activity Launcher */
    private val addDeviceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if(result.resultCode == RESULT_OK){
            val serviceAEBundle = result.data ?: return@registerForActivityResult
            val serviceAE = serviceAEBundle.getStringExtra("SERVICE_AE") ?: return@registerForActivityResult
            CoroutineScope(Dispatchers.Default).launch {
                setDeviceStatus(serviceAE)
            }.invokeOnCompletion {
                if(it != null)
                    Timber.tag(TAG).d("Registration Failed: ${it.cause}")
                else Timber.tag(TAG).d("Registration Succeeded")
            }
        } else{
            Timber.tag(TAG).d("Registration Canceled")
        }
    }

    /* onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        _navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment?
        _navController = navHostFragment.navController
        _appBarConfiguration = AppBarConfiguration(navController.graph)
        viewModel.setDatabase(applicationContext)

        navController.addOnDestinationChangedListener(DestinationChangedListener())
        binding.menuBottomNavigation.setOnItemSelectedListener(ItemSelectionListener())
        binding.mainCollapsingToolbarLayout.setupWithNavController(binding.toolbar,navController,appBarConfiguration)

        setSupportActionBar(binding.toolbar)
        setContentView(binding.root)

        // Create AE and Get AEID
        getAEInfo()

        // Set Device list from room database
        CoroutineScope(Dispatchers.Default).launch {
            setDeviceList()
        }
    }

    // onStart -> Check Permission
    public override fun onStart() {
        super.onStart()

        if(checkSelfPermission(FILE_INTEGRITY_SERVICE) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(NOTIFICATION_SERVICE) == PackageManager.PERMISSION_DENIED ||
            checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    Manifest.permission.USE_EXACT_ALARM),101)
            }
        }
    }

    // Release Memory
    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        _navController = null
        _navHostFragment = null
        _appBarConfiguration = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_device, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_add -> {
                val intent = Intent(this@MainActivity, DeviceAddActivity::class.java)
                addDeviceLauncher.launch(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Custom Methods ---
    /* AE Create for Android AE */
    private fun getAEInfo() {
        Mobius_Address = "192.168.55.35"

        csebase.setInfo(Mobius_Address, "7579", "Mobius", MQTTPort)

        // AE Create for Android AE
        ae.setAppName("ncubeapp")
        val aeCreate = aeCreateRequest()
        aeCreate.setReceiver(object : IReceived {
            override fun getResponseBody(msg: String) {
                handler.post {
                    Timber.tag(TAG).d("** AE Create ResponseCode[%s]", msg)
                    if (msg.toInt() == 201) {
                        viewModel.setMQTTTopic("/oneM2M/req/Mobius2/" + ae.aEid + "_sub" + "/#", "/oneM2M/resp/Mobius2/" + ae.aEid + "_sub" + "/json")
                        Timber.tag(TAG).d("ReqTopic[%s]", viewModel.mqttReqTopic)
                        Timber.tag(TAG).d("ResTopic[%s]", viewModel.mqttRespTopic)
                    } else { // If AE is Exist , GET AEID
                        val aeRetrive = aeRetrieveRequest()
                        aeRetrive.setReceiver(object : IReceived {
                            override fun getResponseBody(resmsg: String) {
                                handler.post {
                                    Timber.tag(TAG).d("** AE Retrive ResponseCode[%s] **", resmsg)
                                    Timber.tag(TAG).d("ReqTopic[%s]", viewModel.mqttReqTopic)
                                    Timber.tag(TAG).d( "ResTopic[%s]", viewModel.mqttRespTopic)
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

    /* Get Registered Device list from room database */
    private suspend fun setDeviceList() = coroutineScope {
        val getList = async { viewModel.database.registeredAEDAO().getAll() }

        withContext(Dispatchers.Main) {
            val deviceList = getList.await()

            viewModel.getDeviceList().clear()
            viewModel.getDeviceList().addAll(deviceList)
            for(i in deviceList.indices){ viewModel.addServiceAE(i) }
        }
    }
    /* Get Device Information from cloud server */
    private suspend fun setDeviceStatus(serviceAE: String) = coroutineScope {
        val reqLed = RetrieveRequest("DATA", serviceAE+"_led")
        val reqPres = RetrieveRequest("DATA", serviceAE+"_pres")
        val reqLock = RetrieveRequest("DATA", serviceAE+"_lock")
        val registeredAE = RegisteredAE(serviceAE,false,false,false,true)
        reqLed.setReceiver(object : IReceived {
            override fun getResponseBody(msg: String) {
                val pxml = ParseElementXml()
                registeredAE.isLedTurnedOn = pxml.GetElementXml(msg, "con") != "0"
            }
        })
        reqPres.setReceiver(object : IReceived {
            override fun getResponseBody(msg: String) {
                val pxml = ParseElementXml()
                registeredAE.isTriggered = pxml.GetElementXml(msg, "con") != "0"
            }
        })
        reqLock.setReceiver(object : IReceived{
            override fun getResponseBody(msg: String) {
                val pxml = ParseElementXml()
                registeredAE.isLocked = pxml.GetElementXml(msg, "con") != "0"
            }
        })

        val ledData = async { reqLed.start(); reqLed.join() }
        val presData = async { reqPres.start(); reqPres.join() }
        val lockData = async { reqLock.start(); reqLock.join() }

        withContext(Dispatchers.Main) {
            ledData.await()
            presData.await()
            lockData.await()

            Log.d(TAG,"RegisteredAE: ${registeredAE.isRegistered},${registeredAE.isTriggered},${registeredAE.isLedTurnedOn},${registeredAE.isLocked}")
            viewModel.getDeviceList().add(registeredAE)
            viewModel.addServiceAE(viewModel.getDeviceList().lastIndex)
            viewModel.database.registeredAEDAO().insert(registeredAE)
        }
    }

    /* Response callback Interface */
    interface IReceived {
        fun getResponseBody(msg: String)
    }

    /* Need for navigating between fragments */
    internal inner class ItemSelectionListener : NavigationBarView.OnItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            val currentFragmentId = navController.currentDestination!!.id
            if (item.itemId == R.id.menu_device) {
                when (currentFragmentId){
                    R.id.deviceMonitorFragment -> navController.navigate(R.id.action_deviceMonitorFragment_to_deviceFragment)
                }
            } else if (item.itemId == R.id.menu_manage) {
                when (currentFragmentId){
                    R.id.deviceListFragment -> navController.navigate(R.id.action_deviceFragment_to_deviceMonitorFragment)
                }
            }
            return true
        }
    }
    internal inner class DestinationChangedListener : NavController.OnDestinationChangedListener{
        override fun onDestinationChanged(
            controller: NavController,
            destination: NavDestination,
            arguments: Bundle?
        ) {
            when(destination.id){
                R.id.deviceListFragment -> {
                    binding.menuBottomNavigation.menu.findItem(R.id.menu_device).isChecked = true
                }
                R.id.deviceMonitorFragment -> {
                    binding.menuBottomNavigation.menu.findItem(R.id.menu_manage).isChecked = true
                }
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
                conn.setRequestProperty("Content-Length", reqXml.length.toString())

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
            aeRetrieveRequest::class.java.name
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
                conn.setRequestProperty("X-M2M-Origin", "Sandroid")
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

    /* Subscribe Presence Content Resource */
    internal inner class CreateSubscribeResource(private val serviceAEName: String) : Thread() {
        private val LOG: Logger = Logger.getLogger(
            CreateSubscribeResource::class.java.name
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

    /* constant values */
    companion object {
        val csebase = CSEBase()
        val ae = AE()
        private const val TAG = "MainActivity"
    }
}
