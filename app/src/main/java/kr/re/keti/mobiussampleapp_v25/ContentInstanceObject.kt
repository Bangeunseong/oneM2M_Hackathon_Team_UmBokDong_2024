package kr.re.keti.mobiussampleapp_v25

/**
 * Created by araha on 2016-09-20.
 */
class ContentInstanceObject {
    private var content = ""

    fun setContent(contentValue: String) {
        this.content = contentValue
    }

    fun makeXML(): String {
        var xml = ""

        xml += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        xml += "<m2m:cin "
        xml += "xmlns:m2m=\"http://www.onem2m.org/xml/protocols\" "
        xml += "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
        xml += "<cnf>text</cnf>"
        xml += "<con>$content</con>"
        xml += "</m2m:cin>"

        return xml
    }
}
