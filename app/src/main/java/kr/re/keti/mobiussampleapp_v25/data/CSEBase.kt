package kr.re.keti.mobiussampleapp_v25.data

/**
 * Created by araha on 2016-09-20.
 */
class CSEBase {
    var host: String = ""
    var port: String = ""
    var CSEName: String = ""
    var MQTTPort: String = ""

    fun setInfo(param1: String, param2: String, param3: String, param4: String) {
        this.host = param1
        this.port = param2
        this.CSEName = param3
        this.MQTTPort = param4
    }

    val serviceUrl: String
        get() = "http://" + this.host + ":" + this.port + "/" + this.CSEName
}
