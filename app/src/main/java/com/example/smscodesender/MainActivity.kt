package com.example.smscodesender

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var layoutPermissionButtons: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val defaultLabels = listOf(
        "Unlock / Open",
        "Lock / Close",
        "Status Check",
        "Arm System",
        "Disarm System",
        "Reset / Reboot"
    )
    private val defaultCodes = listOf(
        "1001",
        "1002",
        "STATUS",
        "ARM#1",
        "DISARM#0",
        "RESET99"
    )

    companion object {
        private const val SMS_SENT_ACTION = "com.example.smscodesender.SMS_SENT"
        private const val REQ_SEND_SMS = 101
    }

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val code = intent?.getStringExtra("code") ?: ""
            when (resultCode) {
                Activity.RESULT_OK -> {
                    tvStatus.text = "Last status: Sent '$code' successfully."
                    Toast.makeText(applicationContext, "SMS '$code' sent!", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    tvStatus.text = "Last status: Failed to send '$code' (Error code: $resultCode)."
                    Toast.makeText(applicationContext, "Failed to send SMS", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("SmsCodePrefs", Context.MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        layoutPermissionButtons = findViewById(R.id.layoutPermissionButtons)

        findViewById<Button>(R.id.btnTopRightSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnGrantPermission).setOnClickListener {
            requestSmsPermissions()
        }

        findViewById<Button>(R.id.btnAppSettings).setOnClickListener {
            openAppSettings()
        }

        val buttonIds = listOf(
            R.id.btnCode1, R.id.btnCode2, R.id.btnCode3,
            R.id.btnCode4, R.id.btnCode5, R.id.btnCode6
        )

        buttonIds.forEachIndexed { index, btnId ->
            val button = findViewById<Button>(btnId)
            updateButtonUI(button, index)

            button.setOnClickListener {
                val code = getCode(index)
                sendSmsCode(code)
            }

            button.setOnLongClickListener {
                showEditButtonDialog(button, index)
                true
            }
        }

        updatePermissionBanner()
        requestSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBanner()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsSentReceiver, IntentFilter(SMS_SENT_ACTION))
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(smsSentReceiver)
        } catch (_: Exception) {}
    }

    private fun getSavedPhoneNumber(): String =
        prefs.getString("saved_phone", "")?.trim() ?: ""

    private fun showSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 32, 56, 16)
        }

        val infoText = TextView(this).apply {
            text = "Target Mobile Number:"
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }

        val inputPhone = EditText(this).apply {
            hint = "e.g., +447700900077"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(getSavedPhoneNumber())
        }

        container.addView(infoText)
        container.addView(inputPhone)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newPhone = inputPhone.text.toString().trim()
                prefs.edit().putString("saved_phone", newPhone).apply()
                Toast.makeText(this, "Target mobile number saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("App Permissions") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun updatePermissionBanner() {
        if (hasSmsPermission()) {
            tvPermissionStatus.text = "✓ SMS Permission Granted (Ready to Send)"
            tvPermissionStatus.setTextColor(Color.parseColor("#2E7D32"))
            layoutPermissionButtons.visibility = View.GONE
        } else {
            tvPermissionStatus.text = "⚠ SMS Permission Required — Tap below to grant:"
            tvPermissionStatus.setTextColor(Color.parseColor("#C62828"))
            layoutPermissionButtons.visibility = View.VISIBLE
        }
    }

    private fun requestSmsPermissions() {
        val perms = arrayOf(
            Manifest.permission.SEND_SMS
        )
        ActivityCompat.requestPermissions(this, perms, REQ_SEND_SMS)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionBanner()
        if (requestCode == REQ_SEND_SMS && !hasSmsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Enable SMS Permission")
                .setMessage(
                    "If Android blocked the permission popup with 'Restricted setting' (common on Android 13+):\n\n" +
                    "1. Tap 'Open Settings' below.\n" +
                    "2. Tap the 3 dots (⋮) in the top-right corner and select 'Allow restricted settings'.\n" +
                    "3. Tap 'Permissions' -> 'SMS' -> 'Allow'."
                )
                .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun getLabel(index: Int): String =
        prefs.getString("btn_label_$index", defaultLabels[index]) ?: defaultLabels[index]

    private fun getCode(index: Int): String =
        prefs.getString("btn_code_$index", defaultCodes[index]) ?: defaultCodes[index]

    private fun updateButtonUI(button: Button, index: Int) {
        val label = getLabel(index)
        val code = getCode(index)
        button.text = "$label\n[$code]"
    }

    private fun showEditButtonDialog(button: Button, index: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 32, 56, 16)
        }

        val inputLabel = EditText(this).apply {
            hint = "Button Name (e.g., Open Gate)"
            setText(getLabel(index))
        }

        val inputCode = EditText(this).apply {
            hint = "SMS Code to Send (e.g., #1234#)"
            setText(getCode(index))
        }

        container.addView(inputLabel)
        container.addView(inputCode)

        AlertDialog.Builder(this)
            .setTitle("Customize Button ${index + 1}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = inputLabel.text.toString().trim().ifEmpty { "Button ${index + 1}" }
                val newCode = inputCode.text.toString().trim().ifEmpty { "0000" }
                prefs.edit()
                    .putString("btn_label_$index", newLabel)
                    .putString("btn_code_$index", newCode)
                    .apply()
                updateButtonUI(button, index)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendSmsCode(code: String) {
        val phone = getSavedPhoneNumber()
        if (phone.isEmpty()) {
            Toast.makeText(this, "Set the target mobile number in Settings first", Toast.LENGTH_SHORT).show()
            showSettingsDialog()
            return
        }

        if (!hasSmsPermission()) {
            requestSmsPermissions()
            return
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val sentIntent = Intent(SMS_SENT_ACTION).apply {
                setPackage(packageName)
                putExtra("code", code)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                code.hashCode(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            tvStatus.text = "Sending '$code'..."
            smsManager.sendTextMessage(phone, null, code, pendingIntent, null)
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.localizedMessage}"
            Toast.makeText(this, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
