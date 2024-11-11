package kr.re.keti.onem2m_hackathon_app.utils

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
