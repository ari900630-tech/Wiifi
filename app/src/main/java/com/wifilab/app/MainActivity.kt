package com.wifilab.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val permissionRequest = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestWifiPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)
            setBackgroundColor(android.graphics.Color.rgb(7, 11, 18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = TextView(this).apply {
            text = "WiFi Lab"
            textSize = 30f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 70))
        val subtitle = TextView(this).apply {
            text = "מעבדת לימוד ואבחון Wi‑Fi — מידע אמיתי מהמכשיר"
            textSize = 15f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        root.addView(subtitle)
        status = TextView(this).apply {
            textSize = 17f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(android.graphics.Color.rgb(18, 25, 38))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        val refresh = Button(this).apply {
            text = "רענן אבחון רשת"
            setOnClickListener { updateNetworkInfo() }
        }
        root.addView(refresh, LinearLayout.LayoutParams(-1, 58))
        val lesson = TextView(this).apply {
            text = "שיעור מהיר\n• WPA2/WPA3: מנגנוני הגנה של רשתות Wi‑Fi.\n• 4‑Way Handshake: תהליך אימות.\n• Hash ו‑Salt: מושגים בסיסיים בהגנת סיסמאות.\n• Rate Limiting: האטת ניסיונות חוזרים.\n\nכל בדיקה מיועדת לרשתות ולמכשירים שיש לך הרשאה לבדוק."
            textSize = 16f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(18, 24, 18, 24)
        }
        root.addView(lesson)
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestWifiPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        val needed = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), permissionRequest)
        else updateNetworkInfo()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) updateNetworkInfo()
    }

    private fun updateNetworkInfo() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = wifi.connectionInfo
        val ssid = info?.ssid?.trim('"') ?: "לא זמין"
        val bssid = info?.bssid ?: "לא זמין"
        val ip = intToIp(info?.ipAddress ?: 0)
        val speed = if ((info?.linkSpeed ?: -1) >= 0) info.linkSpeed.toString() + " Mbps" else "לא זמין"
        val connection = if (connected) "מחובר ל‑Wi‑Fi" else "אין חיבור Wi‑Fi פעיל"
        status.text = "סטטוס: " + connection + "\n\nSSID: " + ssid + "\nBSSID: " + bssid +
                "\nכתובת IP מקומית: " + ip + "\nמהירות קישור: " + speed +
                "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
                "\n\nWi‑Fi: " + if (wifi.isWifiEnabled) "מופעל" else "כבוי" +
                "\n\nמקור המידע: ממשקי Android של המכשיר. אין נתוני דמה."
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return "לא זמין"
        return (value and 255).toString() + "." + ((value shr 8) and 255) + "." +
                ((value shr 16) and 255) + "." + ((value shr 24) and 255)
    }
}
