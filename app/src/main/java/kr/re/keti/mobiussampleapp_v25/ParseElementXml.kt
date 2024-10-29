package kr.re.keti.mobiussampleapp_v25

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Created by araha on 2016-09-20.
 */
class ParseElementXml {
    private var getstr = ""

    fun GetElementXml(xmlParam: String, tagName: String?): String {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val docBuilder = factory.newDocumentBuilder()
            val `is`: InputStream = ByteArrayInputStream(xmlParam.toByteArray())
            val doc = docBuilder.parse(`is`)
            val resultNodes = doc.getElementsByTagName(tagName)

            if (resultNodes.length > 0 && resultNodes.item(0).childNodes.length > 0) {
                val subElement = resultNodes.item(0) as Element
                val aeIdNode = subElement.childNodes.item(0)
                getstr = aeIdNode.nodeValue
            }
            `is`.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getstr
    }
}
