package kr.re.keti.mobiussampleapp_v25.layouts

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.re.keti.mobiussampleapp_v25.R
import kr.re.keti.mobiussampleapp_v25.data.ContentInstanceObject
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.databinding.FragmentDeviceManageBinding
import kr.re.keti.mobiussampleapp_v25.databinding.ItemRecyclerDeviceMonitorBinding
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

class DeviceManageFragment: Fragment() {
    private var _binding: FragmentDeviceManageBinding? = null
    private val binding get() = _binding!!
    private var _adapter: DeviceAdapter? = null
    private val adapter get() = _adapter!!

    private val viewModel: MainViewModel by activityViewModels()
    private val controlActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it.resultCode == RESULT_OK){
            val bundle = it.data?.extras
            Log.d("DeviceManageFragment", "bundle: $bundle")
            if(bundle != null){
                Log.d("DeviceManageFragment", "bundle: ${bundle.getInt("SERVICE_AE_POSITION")}")
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
                    val registeredAE = bundle.getParcelable("SERVICE_AE_OBJECT", RegisteredAE::class.java)
                    val position = bundle.getInt("SERVICE_AE_POSITION")
                    if(registeredAE != null){
                        viewModel.getDeviceList()[position] = registeredAE
                        viewModel.updateServiceAE(position)
                    } else Log.d("DeviceManageFragment", "registeredAE is null", )
                } else{
                    @Suppress("DEPRECATION")
                    val registeredAE = bundle.getParcelable("SERVICE_AE_OBJECT") as RegisteredAE?
                    val position = bundle.getInt("SERVICE_AE_POSITION")
                    if(registeredAE != null){
                        viewModel.getDeviceList()[position] = registeredAE
                        viewModel.updateServiceAE(position)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _adapter = DeviceAdapter(viewModel.getDeviceList())
        viewModel.serviceAEAdd.observe(this) {
            if (it != null) {
                adapter.notifyItemInserted(it)
            }
        }
        viewModel.serviceAEUpdate.observe(this) {
            if (it != null) {
                adapter.notifyItemChanged(it)
            }
        }
        viewModel.serviceAEDelete.observe(this){
            if(it != null){
                adapter.notifyItemRemoved(it)
            }
        }
        viewModel.serviceAEListRefresh.observe(this) {
            if(it != null){
                if(it) adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.deviceMonitorRecyclerView.setHasFixedSize(false)
        binding.deviceMonitorRecyclerView.adapter = adapter
        binding.deviceMonitorRecyclerView.layoutManager = GridLayoutManager(context, 2, RecyclerView.VERTICAL, false)
        binding.deviceMonitorRecyclerView.addItemDecoration(ItemPadding(5,5))
    }

    override fun onDestroy() {
        super.onDestroy()

        _binding = null
        _adapter = null
    }

    // --- Custom Methods ---
    /* Control Device Led Status when pressing led btn */
    private suspend fun controlDeviceLedStatus(device: RegisteredAE, position: Int) = coroutineScope{
        val controlLed = async {
            val reqLed = ControlRequest(device.applicationName+"_led", "DATA", if(device.isLedTurnedOn) "0" else "1")
            reqLed.setReceiver(object : IReceived{
                override fun getResponseBody(msg: String) {
                    val pxml = ParseElementXml()
                    device.isLedTurnedOn = pxml.GetElementXml(msg, "con") != "0"
                    viewModel.getDeviceList()[position] = device
                    CoroutineScope(Dispatchers.IO).launch {

                        viewModel.database.registeredAEDAO().update(device)
                    }
                }
            })
            reqLed.start(); reqLed.join()
        }

        withContext(Dispatchers.Main){
            controlLed.await()
            viewModel.updateServiceAE(position)
        }
    }
    /* Control Device Lock State when pressing lock btn */
    private suspend fun controlDeviceLockStatus(device: RegisteredAE, position: Int) = coroutineScope{
        val controlLock = async {
            val reqLock = ControlRequest(device.applicationName+"_lock", "DATA", if(device.isLocked) "0" else "1")
            reqLock.setReceiver(object : IReceived{
                override fun getResponseBody(msg: String) {
                    val pxml = ParseElementXml()
                    device.isLocked = pxml.GetElementXml(msg, "con") != "0"
                    viewModel.getDeviceList()[position] = device
                    CoroutineScope(Dispatchers.IO).launch {

                        viewModel.database.registeredAEDAO().update(device)
                    }
                }
            })
            reqLock.start(); reqLock.join()
        }

        withContext(Dispatchers.Main){
            controlLock.await()
            viewModel.updateServiceAE(position)
        }
    }
    // ----------------------

    // --- Inner Classes ---
    /* Request Control */
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
    /* Inner Class For Decorating Recycler View of Device List */
    internal inner class ItemPadding(private val divWidth: Int?, private val divHeight: Int?) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View,
            parent: RecyclerView, state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            if (divWidth != null) {
                outRect.left = divWidth
                outRect.right = divWidth
            }
            if(divHeight != null) {
                outRect.top = divHeight
                outRect.bottom = divHeight
            }
        }
    }
    /* Inner Class For Setting Adapter in Device Recycler View */
    inner class DeviceAdapter(private val deviceList: MutableList<RegisteredAE>) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemRecyclerDeviceMonitorBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(deviceList[position])
        }

        override fun getItemCount(): Int {
            return deviceList.size
        }

        inner class ViewHolder(private val binding: ItemRecyclerDeviceMonitorBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(device: RegisteredAE) {
                Log.d("DeviceManageFragment","${device.applicationName}, ${device.isLedTurnedOn}, ${device.isLocked}")
                binding.deviceName.text = device.applicationName
                CoroutineScope(Dispatchers.IO).launch {
                    binding.btnLock.setImageResource(if(device.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock)
                    binding.btnLight.setImageResource(if(device.isLedTurnedOn) R.drawable.ic_light_on else R.drawable.ic_light_off)
                    binding.btnLight.invalidate()
                    binding.btnLock.invalidate()
                }
                binding.btnLight.setOnClickListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        controlDeviceLedStatus(device, layoutPosition)
                    }
                }
                binding.btnLock.setOnClickListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        controlDeviceLockStatus(device, layoutPosition)
                    }
                }
                binding.itemLayout.setOnClickListener {
                    val intent = Intent(context, DeviceControlActivity::class.java)
                    val bundle = Bundle()
                    bundle.putString("SERVICE_AE", device.applicationName)
                    bundle.putString("MQTT_REQ_TOPIC", viewModel.mqttReqTopic)
                    bundle.putString("MQTT_RESP_TOPIC", viewModel.mqttRespTopic)
                    bundle.putInt("SERVICE_AE_POSITION", layoutPosition)
                    intent.putExtras(bundle)
                    controlActivityLauncher.launch(intent)
                }
            }
        }
    }
    // ----------------------
}