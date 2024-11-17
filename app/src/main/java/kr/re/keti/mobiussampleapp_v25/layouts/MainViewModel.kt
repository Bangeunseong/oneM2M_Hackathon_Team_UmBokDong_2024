package kr.re.keti.mobiussampleapp_v25.layouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kr.re.keti.mobiussampleapp_v25.database.RegisteredAE

class MainViewModel: ViewModel() {
    private val mutableServiceAEAdd = MutableLiveData<Int?>()
    private val mutableServiceAEUpdate = MutableLiveData<Int?>()
    private val mutableDeviceList = mutableListOf<RegisteredAE>()

    val serviceAEAdd: LiveData<Int?> get() = mutableServiceAEAdd
    val serviceAEUpdate: LiveData<Int?> get() = mutableServiceAEUpdate

    private var MQTT_Req_Topic = ""
    private var MQTT_Resp_Topic = ""
    val mqttReqTopic get() = MQTT_Req_Topic
    val mqttRespTopic get() = MQTT_Resp_Topic

    fun addServiceAE(position: Int?){
        mutableServiceAEAdd.value = position
    }

    fun updateServiceAE(position: Int?){
        mutableServiceAEUpdate.value = position
    }

    fun getDeviceList(): MutableList<RegisteredAE> {
        return mutableDeviceList
    }

    fun setMQTTTopic(req: String, resp: String){
        MQTT_Req_Topic = req
        MQTT_Resp_Topic = resp
    }

}