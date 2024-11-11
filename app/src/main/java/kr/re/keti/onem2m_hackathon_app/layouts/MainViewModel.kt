package kr.re.keti.onem2m_hackathon_app.layouts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kr.re.keti.onem2m_hackathon_app.data.AE

class MainViewModel: ViewModel() {
    private val mutableServiceAEName = MutableLiveData<String?>()
    val addedServiceAEName: LiveData<String?> get() = mutableServiceAEName
    val mutableDeviceList = mutableListOf<AE>()

    fun addServiceAE(serviceAEName: String?){
        mutableServiceAEName.value = serviceAEName
    }
}