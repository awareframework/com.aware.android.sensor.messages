package com.awareframework.android.sensor.messages

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.messages.model.MessageData


/**
 * Messages Module. For now, scans and returns surrounding messages devices and RSSI dB values.
 *
 * @author  sercant
 * @date 14/08/2018
 */
class MessagesSensor : AwareSensor() {

    companion object {
        const val TAG = "AWARE::Messages"

        /**
         * Fired event: message received
         */
        const val ACTION_AWARE_MESSAGE_RECEIVED = "ACTION_AWARE_MESSAGE_RECEIVED"

        /**
         * Fired event: message sent
         */
        const val ACTION_AWARE_MESSAGE_SENT = "ACTION_AWARE_MESSAGE_SENT"

        /**
         * Un-official and un-supported SMS provider
         * BEWARE: Might have to change in the future API's as Android evolves...
         */
        private val MESSAGES_CONTENT_URI = Uri.parse("content://sms")
        private const val MESSAGE_INBOX = 1
        private const val MESSAGE_SENT = 2

        const val ACTION_AWARE_MESSAGES_START = "com.awareframework.android.sensor.messages.SENSOR_START"
        const val ACTION_AWARE_MESSAGES_STOP = "com.awareframework.android.sensor.messages.SENSOR_STOP"

        const val ACTION_AWARE_MESSAGES_SET_LABEL = "com.awareframework.android.sensor.messages.SET_LABEL"
        const val EXTRA_LABEL = "label"

        const val ACTION_AWARE_MESSAGES_SYNC = "com.awareframework.android.sensor.messages.SENSOR_SYNC"

        val CONFIG = Config()

        val REQUIRED_PERMISSIONS = arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS
        )

        fun start(context: Context, config: Config? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, MessagesSensor::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessagesSensor::class.java))
        }
    }

    private val messagesHandler = Handler()
    private val messagesObserver = object : ContentObserver(messagesHandler) {

        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)

            val lastMessage = contentResolver.query(MESSAGES_CONTENT_URI, null, null, null, "date DESC LIMIT 1")

            if (lastMessage?.moveToFirst() == true) {
                val lmType = lastMessage.getInt(lastMessage.getColumnIndex("type"))
                val lmTimestamp = lastMessage.getLong(lastMessage.getColumnIndex("date"))
                val lmTrace = lastMessage.getString(lastMessage.getColumnIndex("address"))

                if (!lastMessage.isClosed)
                    lastMessage.close()

                // TODO add check if the message exists in the db

                val data = MessageData().apply {
                    timestamp = System.currentTimeMillis()
                    deviceId = CONFIG.deviceId
                    label = CONFIG.label

                    eventTimestamp = lmTimestamp
                    trace = lmTrace // TODO encrypt data
                    type = lmType
                }

                dbEngine?.save(data, MessageData.TABLE_NAME)

                CONFIG.sensorObserver?.onMessage(data)

                when (lmType) {
                    MESSAGE_INBOX -> {
                        ACTION_AWARE_MESSAGE_RECEIVED
                    }

                    MESSAGE_SENT -> {
                        ACTION_AWARE_MESSAGE_SENT
                    }

                    else -> {
                        null
                    }
                }?.let {
                    logd(it)
                    sendBroadcast(Intent(it))
                }
            }
        }
    }

    private val messagesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                ACTION_AWARE_MESSAGES_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_MESSAGES_SYNC -> onSync(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)

        registerReceiver(messagesReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_MESSAGES_SET_LABEL)
            addAction(ACTION_AWARE_MESSAGES_SYNC)
        })

        logd("Messages service created!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (REQUIRED_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) != PERMISSION_GRANTED }) {
            logw("Missing permissions detected.")
            return START_NOT_STICKY
        }

        contentResolver.registerContentObserver(MESSAGES_CONTENT_URI, true, messagesObserver)

        logd("Messages service is active.")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        contentResolver.unregisterContentObserver(messagesObserver)

        unregisterReceiver(messagesReceiver)

        dbEngine?.close()

        logd("Messages service terminated.")
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(MessageData.TABLE_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class Config(
            var sensorObserver: Observer? = null
    ) : SensorConfig(dbPath = "aware_messages") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is Config) {
                sensorObserver = config.sensorObserver
            }
        }
    }

    interface Observer {
        /**
         * Message callback when a message event is recorded (received, made, missed)
         *
         * @param data
         */
        fun onMessage(data: MessageData)
    }

    class MessagesSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        start(context)
                    }
                }

                ACTION_AWARE_MESSAGES_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stop(context)
                }

                ACTION_AWARE_MESSAGES_START -> {
                    start(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (MessagesSensor.CONFIG.debug) Log.d(MessagesSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(MessagesSensor.TAG, text)
}