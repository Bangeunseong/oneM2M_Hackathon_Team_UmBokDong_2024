package kr.re.keti.mobiussampleapp_v25.data

/**
 * Created by araha on 2016-09-20.
 */
class ContentSubscribeObject {
    private var resource_name = ""
    private var content_url: String? = ""
    private var subscribe_path = ""
    private var origin_id: String? = ""

    fun setUrl(url: String?) {
        this.content_url = url
    }

    fun setResourceName(resourceName: String) {
        this.resource_name = resourceName
    }

    fun setPath(subscribePath: String) {
        this.subscribe_path = subscribePath
    }

    fun setOrigin_id(originid: String?) {
        this.origin_id = originid
    }

    fun makeXML(): String {
        var xml = ""
        xml += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        xml += "<m2m:sub "
        xml += "xmlns:m2m=\"http://www.onem2m.org/xml/protocols\" "
        xml += "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" rn=\"$resource_name\">"
        xml += "<enc>"
        xml += "<net>3 4</net>"
        xml += "</enc>"
        xml += "<nu>"
        if(subscribe_path.isNotBlank())
            xml += "mqtt://$content_url/$subscribe_path"
        xml += "</nu>"
        xml += "<pn>1</pn>"
        xml += "<nct>2</nct>"
        xml += "<cr>$origin_id</cr>"
        xml += "</m2m:sub>"

        return xml
    }

    fun makeXML(subscribePaths: List<String>): String {
        var xml = ""
        xml += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        xml += "<m2m:sub "
        xml += "xmlns:m2m=\"http://www.onem2m.org/xml/protocols\">"
        xml += "<enc>"
        xml += "<net>3 4</net>"
        xml += "</enc>"
        xml += "<nu>"
        for (i in subscribePaths.indices) {
            if(i != 0) xml += " "
            xml += subscribePaths[i]
        }
        xml += "</nu>"
        xml += "<nct>2</nct>"
        xml += "</m2m:sub>"
        return xml
    }
}