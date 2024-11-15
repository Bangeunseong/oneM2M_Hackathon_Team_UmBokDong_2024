package kr.re.keti.mobiussampleapp_v25.layouts

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import kr.re.keti.mobiussampleapp_v25.data.ContentInstanceObject
import kr.re.keti.mobiussampleapp_v25.data.ContentSubscribeObject
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequest
import kr.re.keti.mobiussampleapp_v25.utils.MqttClientRequestParser
import kr.re.keti.mobiussampleapp_v25.utils.ParseElementXml
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityMainBinding
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import timber.log.Timber
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.coroutineContext

// TODO: 현재 Subscription 한 기기들의 정보를 받아와서 viewModel.mutableDeviceList에 저장하는 것이 필요
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
    private lateinit var db: RegisteredAEDatabase

    private var handler: Handler = Handler()

    private val MQTTPort = "1883"
    private var Mobius_Address = ""

    private val viewModel: MainViewModel by viewModels()

    // Device Addition Activity Launcher
    private val addDeviceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if(result.resultCode == RESULT_OK){
            val serviceAEBundle = result.data ?: return@registerForActivityResult
            val serviceAE = serviceAEBundle.getStringExtra("SERVICE_AE") ?: return@registerForActivityResult
            viewModel.getDeviceList().add(serviceAE)
            viewModel.addServiceAE(serviceAE)
            Timber.tag(TAG).d("Registration Succeeded")
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
        db = RegisteredAEDatabase.getInstance(applicationContext)!!

        CoroutineScope(Dispatchers.IO).launch {
            val deviceList = db.registeredAEDAO().getAll()
            for(device in deviceList){
                viewModel.addServiceAE(device.applicationName)
            }
        }

        navController.addOnDestinationChangedListener(DestinationChangedListener())
        binding.menuBottomNavigation.setOnItemSelectedListener(ItemSelectionListener())
        binding.mainCollapsingToolbarLayout.setupWithNavController(binding.toolbar,navController,appBarConfiguration)

        setSupportActionBar(binding.toolbar)
        setContentView(binding.root)

        // Create AE and Get AEID
        getAEInfo()
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

    public override fun onStop() {
        super.onStop()
    }

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

    /* AE Create for Android AE */
    private fun getAEInfo() {
        Mobius_Address = "192.168.55.35"

        csebase.setInfo(Mobius_Address, "7579", "Mobius", MQTTPort)

        //csebase.setInfo("203.253.128.151","7579","Mobius","1883");
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

    /* Response callback Interface */
    interface IReceived {
        fun getResponseBody(msg: String)
    }

    // Need for navigating between fragments
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

    // --- AE Actions (CR) ---
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
    // -----------------------


    companion object {
        val csebase = CSEBase()
        val ae = AE()
        private const val TAG = "MainActivity"
    }
}
