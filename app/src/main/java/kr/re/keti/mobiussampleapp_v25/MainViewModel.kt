package kr.re.keti.mobiussampleapp_v25

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kr.re.keti.mobiussampleapp_v25.data_objects.AE

class MainViewModel: ViewModel() {
    private val mutableServiceAEName = MutableLiveData<String?>()
    val addedServiceAEName: LiveData<String?> get() = mutableServiceAEName
    val mutableDeviceList = mutableListOf<AE>()

    fun addServiceAE(serviceAEName: String?){
        mutableServiceAEName.value = serviceAEName
    }
}