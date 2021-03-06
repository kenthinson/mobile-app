package io.rebble.fossil

import android.Manifest
import android.app.AlertDialog
import android.app.Notification
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.Telephony
import android.text.TextUtils
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.Log
import io.rebble.libpebblecommon.ProtocolHandler
import io.rebble.libpebblecommon.blobdb.NotificationSource
import io.rebble.libpebblecommon.blobdb.PushNotification
import io.rebble.libpebblecommon.services.notification.NotificationService


@ExperimentalUnsignedTypes
class WatchService : Service() {
    private val pBinder = ProtBinder()
    private val logTag: String = "FossilWatchService"
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var protocolHandler: ProtocolHandler? = null

    private val blueCommon = BlueCommon(this) {
        protocolHandler?.handle(it)
    }

    private val mainNotifBuilder = NotificationCompat.Builder(this, "device_status")
            .setContentTitle("Disconnected")
            .setSmallIcon(R.drawable.ic_notification_disconnected)

    inner class ProtBinder : Binder() {
        fun getService() : WatchService {
            return this@WatchService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return pBinder
    }

    private fun packageToSource(pkg: String?): NotificationSource {
        //TODO: Check for other email clients
        return when (pkg) {
            "com.google.android.gm" -> NotificationSource.Email
            "com.facebook.katana" -> NotificationSource.Facebook
            "com.twitter.android", "com.twitter.android.lite" -> NotificationSource.Twitter
            Telephony.Sms.getDefaultSmsPackage(this) -> NotificationSource.SMS
            else -> NotificationSource.Generic
        }
    }

    @ExperimentalStdlibApi
    private val notifBroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val notif = intent?.getBundleExtra("notification") ?: return
            NotificationService.send(PushNotification(notif["subject"] as String, notif["sender"] as String, notif["content"] as String, packageToSource(notif["pkg"] as String))) {
                Log.d("FossilNotifRet", "Got resp from BlobDB: ${it::class.simpleName}")
            }
        }
    }

    @ExperimentalStdlibApi
    override fun onCreate() {
        super.onCreate()
        // TODO: BLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Requires location permission for bluetooth LE", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w(logTag, "Bluetooth - Not enabled")
        }
        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(notifBroadcastReceiver, IntentFilter("io.rebble.fossil.NOTIFICATION_BROADCAST"))

        startIO { res ->
            protocolHandler?.handle(res)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, mainNotifBuilder.build())
        return START_STICKY
    }

    private fun setNotification(connected: Boolean) {
        mainNotifBuilder
                .setContentTitle(if (connected) "Connected to device" else "Disconnected")
                .setContentText(if (connected) blueCommon.driver!!.getTarget()?.name else null)
                .setSmallIcon(if (connected) R.drawable.ic_notification_connected else R.drawable.ic_notification_disconnected)
                .setPriority(if (connected) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(this).notify(1, mainNotifBuilder.build())
    }

    private fun startIO(packetCallback: (ByteArray) -> Unit) {
        protocolHandler = ProtocolHandler {bytes -> blueCommon.driver?.sendPacket(bytes)}
        blueCommon.setOnConnectionChange { connected -> setNotification(connected) }
    }

    // Public methods for activity / flutter

    fun scanDevices(resultCallback: (List<BluetoothDevice>) -> Unit) {
        blueCommon.scanDevices(resultCallback)
    }

    fun targetPebble(device: BluetoothDevice): Boolean {
        return blueCommon.targetPebble(device)
    }

    fun targetPebble(addr: Long): Boolean {
        return blueCommon.targetPebble(addr)
    }

    fun sendDevPacket(packet: ByteArray) {
        blueCommon.driver?.sendPacket(packet)
    }

    fun isConnected(): Boolean {
        return blueCommon.driver?.isConnected ?: false
    }
}
