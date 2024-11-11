package kr.re.keti.onem2m_hackathon_app.utils

/**
 * Created by araha on 2016-09-13.
 */
object MqttClientRequest {
    fun notificationResponse(response: String?): String {
        val responseMessage =
            """
             {"rsc":"2000",
             "rqi":"$response",
             "pc":""}
             """.trimIndent()

        return responseMessage
    }
}
