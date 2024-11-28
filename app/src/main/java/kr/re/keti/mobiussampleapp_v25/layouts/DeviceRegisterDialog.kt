package kr.re.keti.mobiussampleapp_v25.layouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAEDatabase
import kr.re.keti.mobiussampleapp_v25.databinding.ActivityAddFormatBinding
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.ae
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.Companion.csebase
import kr.re.keti.mobiussampleapp_v25.layouts.MainActivity.IReceived
import kr.re.keti.mobiussampleapp_v25.utils.DeviceAddListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger

class DeviceRegisterDialog(private val listener: DeviceAddListener) : DialogFragment() {
    private var _binding: ActivityAddFormatBinding? = null
    private val binding get() = _binding!!
    private var reqServiceAE: RetrieveRequest? = null
    private lateinit var db: RegisteredAEDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = RegisteredAEDatabase.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ActivityAddFormatBinding.inflate(inflater, container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.btnRegister.setOnClickListener {
            val serviceAE = binding.inputServiceAeName.text.toString()
            var device: RegisteredAE? = null
            CoroutineScope(Dispatchers.IO).launch {
                device = db.registeredAEDAO().get(serviceAE)
            }.invokeOnCompletion {
                if(device == null){
                    reqServiceAE = RetrieveRequest(serviceAE, "DATA")
                    reqServiceAE!!.setReceiver(object : IReceived {
                        override fun getResponseBody(msg: String) {
                            listener.setDeviceName(binding.inputServiceAeName.text.toString())
                            dismiss()
                        }
                    })
                    reqServiceAE!!.start()
                } else Toast.makeText(context, "$serviceAE is already registered", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
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

        var responseCode = 0

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

                responseCode = conn.responseCode

                var `in`: BufferedReader? = null
                if(responseCode == 200){
                    var strResp = ""
                    `in` = BufferedReader(InputStreamReader(conn.inputStream))

                    var strLine = ""
                    while ((`in`.readLine().also { strLine = it }) != null) {
                        strResp += strLine
                    }

                    if (strResp !== "") {
                        receiver!!.getResponseBody(strResp)
                    }
                } else Toast.makeText(context, "Can't find $serviceAEName, please check it is registered first", Toast.LENGTH_SHORT).show()
                conn.disconnect()
            } catch (exp: Exception) {
                LOG.log(Level.WARNING, exp.message)
            }
        }
    }
    // -----------------------
}