package com.wifilab.app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var networks: TextView
    private lateinit var wifi: WifiManager
    private lateinit var prefs: SharedPreferences
    private lateinit var diagnosis: TextView
    private lateinit var history: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val permissionRequest = 4102

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                showScanResults(wifi.scanResults ?: emptyList())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        prefs = getSharedPreferences("wifi_lab", MODE_PRIVATE)
        registerScanReceiver()
        buildUi()
        requestWifiPermissions()
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(scanReceiver, filter)
        }
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

        val scanButton = Button(this).apply {
            text = "סרוק רשתות Wi‑Fi בסביבה"
            setOnClickListener { startWifiScan() }
        }
        root.addView(scanButton, LinearLayout.LayoutParams(-1, 58))

        networks = TextView(this).apply {
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(18, 18, 18, 18)
            setBackgroundColor(android.graphics.Color.rgb(18, 25, 38))
            text = "רשתות בסביבה:\nלחץ על «סרוק רשתות Wi‑Fi בסביבה» כדי להתחיל."
        }
        root.addView(networks, LinearLayout.LayoutParams(-1, -2))


        val diagnosisTitle = TextView(this).apply {
            text = "🌐 בדיקת Internet / DNS / Gateway"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(4, 22, 4, 10)
        }
        root.addView(diagnosisTitle)

        val diagnoseButton = Button(this).apply {
            text = "בדוק Internet / DNS / Gateway"
            setOnClickListener { runDiagnosis() }
        }
        root.addView(diagnoseButton, LinearLayout.LayoutParams(-1, 58))

        diagnosis = cardView()
        diagnosis.text = "לחץ על הבדיקה כדי לבדוק את הרשת המחוברת."
        root.addView(diagnosis)

        val historyTitle = TextView(this).apply {
            text = "📊 היסטוריית בדיקות"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(4, 22, 4, 10)
        }
        root.addView(historyTitle)

        val clearHistory = Button(this).apply {
            text = "נקה היסטוריה"
            setOnClickListener {
                prefs.edit().remove("history").apply()
                history.text = "אין בדיקות שמורות."
            }
        }
        root.addView(clearHistory, LinearLayout.LayoutParams(-1, 58))

        history = cardView()
        history.text = loadHistory()
        root.addView(history)

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

    private fun cardView(): TextView = TextView(this).apply {
        textSize = 16f
        setTextColor(android.graphics.Color.WHITE)
        setPadding(18, 18, 18, 18)
        setBackgroundColor(android.graphics.Color.rgb(18, 25, 38))
    }

    private fun requestWifiPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val needed = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), permissionRequest)
        } else {
            updateNetworkInfo()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            updateNetworkInfo()
        }
    }

    private fun startWifiScan() {
        updateNetworkInfo()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            networks.text = "אין הרשאת מיקום מדויקת. Android דורש הרשאה זו להצגת תוצאות סריקת Wi‑Fi."
            requestWifiPermissions()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28 && !locationManager.isLocationEnabled) {
            networks.text = "שירותי מיקום כבויים. ב‑Android 11 יש להפעיל מיקום כדי לקבל תוצאות מסריקת Wi‑Fi.\n\nאפשר לפתוח הגדרות מיקום מכאן."
            val openSettings = Button(this).apply {
                text = "פתח הגדרות מיקום"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            val parent = networks.parent as? LinearLayout
            if (parent != null && parent.indexOfChild(openSettings) == -1) {
                parent.addView(openSettings, parent.indexOfChild(networks) + 1, LinearLayout.LayoutParams(-1, 58))
            }
            return
        }

        networks.text = "סורק רשתות Wi‑Fi אמיתיות בסביבה…\nAndroid עשוי להגביל תדירות סריקות."
        val started = try {
            @Suppress("DEPRECATION")
            wifi.startScan()
        } catch (_: SecurityException) {
            false
        }

        if (!started) {
            val cached = try { wifi.scanResults ?: emptyList() } catch (_: SecurityException) { emptyList() }
            if (cached.isNotEmpty()) {
                showScanResults(cached)
                networks.append("\n\nAndroid לא אישר סריקה חדשה כרגע, לכן מוצגות תוצאות שנשמרו במערכת.")
            } else {
                networks.text = "Android לא אישר סריקה חדשה כרגע ואין תוצאות שמורות.\n\nבדוק ש‑Wi‑Fi ומיקום מופעלים, ושנסה שוב בעוד כמה שניות."
            }
        }
    }

    private fun showScanResults(results: List<ScanResult>) {
        if (results.isEmpty()) {
            networks.text = "לא נמצאו כרגע רשתות בתוצאות הסריקה.\n\nודא ש‑Wi‑Fi ומיקום מופעלים ונסה שוב."
            return
        }

        val sorted = results
            .filter { it.SSID.isNotBlank() }
            .distinctBy { it.BSSID }
            .sortedByDescending { it.level }

        if (sorted.isEmpty()) {
            networks.text = "נמצאו תוצאות ללא SSID גלוי.\n\nAndroid לא מאפשר לאפליקציה להציג שמות רשת מוסתרים."
            return
        }

        val builder = StringBuilder()
        builder.append("רשתות שנמצאו: ").append(sorted.size).append("\n")
        builder.append("סריקה אחרונה: ").append(now()).append("\n\n")

        sorted.forEachIndexed { index, result ->
            val security = when {
                result.capabilities.contains("SAE") -> "WPA3"
                result.capabilities.contains("WPA2") -> "WPA2"
                result.capabilities.contains("WEP") -> "WEP"
                result.capabilities.contains("OWE") -> "OWE"
                result.capabilities.contains("WPA") -> "WPA"
                else -> "פתוחה/לא מזוהה"
            }
            builder.append(index + 1)
                .append(". ")
                .append(result.SSID)
                .append("\n   עוצמה: ")
                .append(result.level)
                .append(" dBm — ").append(signalQuality(result.level))
                .append(" | תדר: ")
                .append(result.frequency)
                .append(" MHz | ").append(bandLabel(result.frequency))
                .append(" | ערוץ ").append(frequencyToChannel(result.frequency))
                .append("\n   אבטחה: ")
                .append(security)
                .append("\n   BSSID: ")
                .append(result.BSSID)
                .append("\n\n")
        }

        networks.text = builder.toString().trim()
    }

    private fun signalQuality(level: Int): String {
        return when {
            level >= -50 -> "מצוין"
            level >= -60 -> "טוב"
            level >= -70 -> "בינוני"
            else -> "חלש"
        }
    }

    private fun bandLabel(frequency: Int): String {
        return when {
            frequency in 2400..2500 -> "2.4GHz"
            frequency in 4900..5900 -> "5GHz"
            frequency in 5925..7125 -> "6GHz"
            else -> "תדר לא מזוהה"
        }
    }

    private fun frequencyToChannel(frequency: Int): String {
        return when {
            frequency in 2412..2472 -> ((frequency - 2407) / 5).toString()
            frequency == 2484 -> "14"
            frequency in 5000..5900 -> ((frequency - 5000) / 5).toString()
            frequency in 5955..7115 -> ((frequency - 5950) / 5).toString()
            else -> "לא זמין"
        }
    }

    private fun runDiagnosis() {
        updateNetworkInfo()
        diagnosis.text = "⏳ בודק Gateway, DNS ו‑Internet…"
        executor.execute {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val dhcp = try { wifi.dhcpInfo } catch (_: Exception) { null }
            val gateway = dhcp?.gateway?.let { intToIp(it) }

            val gatewayResult = if (!wifiConnected) "❌ אין חיבור Wi‑Fi פעיל"
            else if (gateway == null || gateway == "לא זמין") "❌ Gateway לא זמין"
            else "✅ Gateway: " + gateway

            val dnsResult = try {
                InetAddress.getByName("example.com")
                "✅ DNS עובד"
            } catch (_: Exception) {
                "❌ DNS לא פתר example.com"
            }

            val started = System.currentTimeMillis()
            val internetResult = try {
                val connection = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                val elapsed = System.currentTimeMillis() - started
                if (code in 200..399) "✅ Internet זמין (" + elapsed + " ms)" else "⚠️ Internet החזיר HTTP " + code
            } catch (_: Exception) {
                "❌ Internet לא זמין או חסום"
            }

            val result = gatewayResult + "\n" + dnsResult + "\n" + internetResult + "\n\nזמן בדיקה: " + now()
            runOnUiThread {
                diagnosis.text = result
                saveHistory(result)
            }
        }
    }

    private fun saveHistory(result: String) {
        val old = prefs.getString("history", "") ?: ""
        val entry = now() + "\n" + result
        val combined = (entry + "\n\n" + old).take(7000)
        prefs.edit().putString("history", combined).apply()
        history.text = combined
    }

    private fun loadHistory(): String {
        return prefs.getString("history", "") ?: "אין בדיקות שמורות."
    }

    private fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

    private fun updateNetworkInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = wifi.connectionInfo
        val ssid = info?.ssid?.trim('"') ?: "לא זמין"
        val bssid = info?.bssid ?: "לא זמין"
        val ip = intToIp(info?.ipAddress ?: 0)
        val speed = if ((info?.linkSpeed ?: -1) >= 0) info.linkSpeed.toString() + " Mbps" else "לא זמין"
        val dhcp = try { wifi.dhcpInfo } catch (_: Exception) { null }
        val gateway = dhcp?.gateway?.let { intToIp(it) } ?: "לא זמין"
        val dns1 = dhcp?.dns1?.let { intToIp(it) } ?: "לא זמין"
        val dns2 = dhcp?.dns2?.let { intToIp(it) } ?: "לא זמין"
        val connection = if (connected) "מחובר ל‑Wi‑Fi" else "אין חיבור Wi‑Fi פעיל"
        status.text = "סטטוס: " + connection + "\n\nSSID: " + ssid + "\nBSSID: " + bssid +
                "\nכתובת IP מקומית: " + ip + "\nGateway: " + gateway + "\nDNS: " + dns1 + " / " + dns2 + "\nמהירות קישור: " + speed +
                "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
                "\n\nWi‑Fi: " + if (wifi.isWifiEnabled) "מופעל" else "כבוי" +
                "\n\nמקור המידע: ממשקי Android של המכשיר. אין נתוני דמה."
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return "לא זמין"
        return (value and 255).toString() + "." + ((value shr 8) and 255) + "." +
                ((value shr 16) and 255) + "." + ((value shr 24) and 255)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (_: Exception) {
        }
    }
}
