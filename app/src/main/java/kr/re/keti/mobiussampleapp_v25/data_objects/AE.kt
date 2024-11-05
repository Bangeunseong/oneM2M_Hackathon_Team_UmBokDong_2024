package kr.re.keti.mobiussampleapp_v25.data_objects

/**
 * Created by araha on 2016-09-20.
 */
class AE {
    var appos: String = ""
    var appId: String = ""
    var aEid: String? = ""
    var applicationName: String = ""
    var pointOfAccess: String = ""
    var appPort: String = ""
    var appProtocol: String = ""
    var tasPort: String = ""
    var cilimit: String = ""

    fun setAEId(aEid: String){
        this.aEid = aEid
    }

    fun setAppName(appname: String) {
        this.applicationName = appname
    }

    fun getappName(): String {
        return this.applicationName
    }
}