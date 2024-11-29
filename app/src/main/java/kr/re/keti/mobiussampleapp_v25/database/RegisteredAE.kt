package kr.re.keti.mobiussampleapp_v25.database

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RegisteredAE(
    @PrimaryKey
    var applicationName: String,
    var isTriggered: Boolean,
    var isLocked: Boolean,
    var isBuzTurnedOn: Boolean,
    var isLedTurnedOn: Boolean,
    var isRegistered: Boolean
): Parcelable{
    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readByte().toInt() != 0,
        parcel.readByte().toInt() != 0,
        parcel.readByte().toInt() != 0,
        parcel.readByte().toInt() != 0,
        parcel.readByte().toInt() != 0
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(applicationName)
        parcel.writeByte(if (isTriggered) 1 else 0)
        parcel.writeByte(if (isLocked) 1 else 0)
        parcel.writeByte(if (isBuzTurnedOn) 1 else 0)
        parcel.writeByte(if (isLedTurnedOn) 1 else 0)
        parcel.writeByte(if (isRegistered) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RegisteredAE> {
        override fun createFromParcel(parcel: Parcel): RegisteredAE {
            return RegisteredAE(parcel)
        }

        override fun newArray(size: Int): Array<RegisteredAE?> {
            return arrayOfNulls(size)
        }
    }
}