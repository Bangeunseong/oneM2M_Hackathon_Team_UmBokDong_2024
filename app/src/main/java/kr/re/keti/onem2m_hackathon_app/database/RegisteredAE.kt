package kr.re.keti.onem2m_hackathon_app.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RegisteredAE(
    @PrimaryKey
    var aEid: String,
    var appos: String,
    var appId: String,
    var applicationName: String,
    var pointOfAccess: String,
    var appPort: String,
    var appProtocol: String,
    var tasPort: String,
    var cilimit: String,) {
}