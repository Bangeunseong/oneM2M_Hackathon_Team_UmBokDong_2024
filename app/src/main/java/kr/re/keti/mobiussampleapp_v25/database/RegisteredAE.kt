package kr.re.keti.mobiussampleapp_v25.database

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