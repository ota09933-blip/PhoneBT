package com.example.phonebt

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import java.util.UUID

@SuppressLint("MissingPermission")
class BtService : Service() {

    companion object {
        // UUID الخاص بـ Serial Port Profile - ويندوز ينشئ له منفذ COM تلقائياً
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHANNEL = "phonebt"
    }

    @Volatile private var running = false
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var current: BluetoothSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        if (!running) {
            running = true
            Thread { listenLoop() }.start()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "PhoneBT", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("PhoneBT يعمل")
            .setContentText("بانتظار الاتصال من الكمبيوتر")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
    }

    private fun listenLoop() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        while (running) {
            try {
                server = adapter.listenUsingRfcommWithServiceRecord("PhoneBT", SPP_UUID)
                val socket = server!!.accept()
                server?.close()
                current = socket
                handle(socket)
            } catch (e: Exception) {
                if (running) Thread.sleep(1000)
            }
        }
    }

    private fun handle(socket: BluetoothSocket) {
        try {
            val reader = socket.inputStream.bufferedReader()
            val out = socket.outputStream
            while (running) {
                val line = reader.readLine() ?: break
                val reply = execute(line.trim())
                out.write((reply + "\n").toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            // انقطع الاتصال
        } finally {
            try { socket.close() } catch (_: Exception) {}
            current = null
        }
    }

    private fun execute(cmd: String): String {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val parts = cmd.split(":")
        return when (parts[0]) {
            "LOCK" -> {
                val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = ComponentName(this, AdminReceiver::class.java)
                if (dpm.isAdminActive(admin)) {
                    dpm.lockNow()
                    "OK"
                } else {
                    "ERR:NO_ADMIN"
                }
            }
            "VOL_UP" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                volume(audio)
            }
            "VOL_DOWN" -> {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                volume(audio)
            }
            "SET" -> {
                val v = parts.getOrNull(1)?.toIntOrNull()
                if (v == null) {
                    "ERR:BAD_VALUE"
                } else {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
                    volume(audio)
                }
            }
            "GET" -> volume(audio)
            else -> "ERR:UNKNOWN"
        }
    }

    private fun volume(audio: AudioManager): String {
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return "VOL:$cur/$max"
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        try { current?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
