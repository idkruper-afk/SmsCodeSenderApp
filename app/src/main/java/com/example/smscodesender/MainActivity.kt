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
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etCustomCode: TextInputEditText
    private lateinit var tvStatus: TextView
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
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etCustomCode = findViewById(R.id.etCustomCode)
        tvStatus = findViewById(R.id.tvStatus)

        // Restore saved phone number
        etPhoneNumber.setText(prefs.getString("saved_phone", ""))
        etPhoneNumber.doAfterTextChanged { text ->
            prefs.edit().putString("saved_phone", text?.toString()?.trim() ?: "").apply()
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

        findViewById<Button>(R.id.btnSendCustom).setOnClickListener {
            val customCode = etCustomCode.text?.toString()?.trim() ?: ""
            if (customCode.isEmpty()) {
                Toast.makeText(this, "Enter a custom code first", Toast.LENGTH_SHORT).show()
            } else {
                sendSmsCode(customCode)
            }
        }

        requestSmsPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
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
        val phone = etPhoneNumber.text?.toString()?.trim() ?: ""
        if (phone.isEmpty()) {
            Toast.makeText(this, "Please enter a target mobile number first", Toast.LENGTH_SHORT).show()
            etPhoneNumber.requestFocus()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SEND_SMS)
            Toast.makeText(this, "Grant SMS permission and tap the button again", Toast.LENGTH_SHORT).show()
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

            tvStatus.text = "Sending '$code' to $phone..."
            smsManager.sendTextMessage(phone, null, code, pendingIntent, null)
        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.localizedMessage}"
            Toast.makeText(this, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SEND_SMS)
        }
    }
}
