package com.example.phonebt

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.Manifest

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)

        fun addButton(text: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                this.text = text
                setOnClickListener { onClick() }
            })
        }

        addButton("1) السماح بالبلوتوث") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            }
        }

        addButton("2) تفعيل صلاحية قفل الشاشة") {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "مطلوبة لقفل الهاتف من الكمبيوتر")
            }
            startActivity(intent)
        }

        addButton("3) تشغيل الخدمة") {
            val intent = Intent(this, BtService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            status.text = "الخدمة تعمل. اقترن بالكمبيوتر من إعدادات البلوتوث."
        }

        addButton("إيقاف الخدمة") {
            stopService(Intent(this, BtService::class.java))
            status.text = "الخدمة متوقفة."
        }

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))
        status.text = "صلاحية القفل: " + if (admin) "مفعّلة" else "غير مفعّلة"
    }
}
