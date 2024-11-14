package kr.re.keti.mobiussampleapp_v25.layouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kr.re.keti.mobiussampleapp_v25.data.AE

class MainViewModel: ViewModel() {
    private val mutableServiceAEName = MutableLiveData<String?>()
    private val mutableDeviceList = mutableListOf<String>()
    private var MQTT_Req_Topic = ""
    private var MQTT_Resp_Topic = ""
    val mqttReqTopic get() = MQTT_Req_Topic
    val mqttRespTopic get() = MQTT_Resp_Topic
    val addedServiceAEName: LiveData<String?> get() = mutableServiceAEName

    fun addServiceAE(serviceAEName: String?){
        mutableServiceAEName.value = serviceAEName
    }

    fun getDeviceList(): MutableList<String> {
        return mutableDeviceList
    }

    fun setMQTTTopic(req: String, resp: String){
        MQTT_Req_Topic = req
        MQTT_Resp_Topic = resp
    }

}