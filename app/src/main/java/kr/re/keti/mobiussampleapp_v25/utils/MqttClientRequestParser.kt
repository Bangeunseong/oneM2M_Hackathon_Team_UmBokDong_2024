package kr.re.keti.mobiussampleapp_v25.utils

import org.json.JSONObject

/**
 * Created by araha on 2016-09-13.
 */
object MqttClientRequestParser {
    private const val TAG = "MqttClientRequestParser"

    // xml parser
    // json parser
    @Throws(Exception::class)
    fun notificationJsonParse(message: String): String {
        val json = JSONObject(message)
        val responserqi = json.getString("rqi")

        return responserqi
    }
}
