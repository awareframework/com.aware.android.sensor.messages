package com.awareframework.android.sensor.messages.model

import com.awareframework.android.core.model.AwareObject

/**
 * Contains the messages sensor information.
 *
 * @author  sercant
 * @date 15/08/2018
 */
data class MessageData(
        var eventTimestamp: Long = 0L,
        var type: Int = -1,
        var trace: String? = null
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "messageData"
    }

    override fun toString(): String = toJson()
}