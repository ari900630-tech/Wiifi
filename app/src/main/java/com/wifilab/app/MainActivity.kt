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
import java.security.MessageDigest
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

        val hackerTitle = TextView(this).apply {
            text = "🕶️ Hacker Lab — מצב לימוד בטוח"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(4, 22, 4, 10)
        }
        root.addView(hackerTitle)

        val hackerInfo = cardView()
        hackerInfo.text = "כלים חינוכיים ללא תקיפה של רשתות:\n\n" +
                "🧪 סימולציית CTF מקומית — תרגול מושגים בלי לגעת ברשת אמיתית.\n" +
                "🔐 בודק חוזק סיסמה שהמשתמש מזין מקומית בלבד.\n" +
                "🛡️ בדיקת אבטחת הרשת המחוברת — WPA/WPA2/WPA3, עוצמת אות ופרטי חיבור זמינים.\n" +
                "📚 שיעורים על Handshake, Hash, Salt, DNS ו‑DHCP.\n\n" +
                "הכלים אינם מבצעים פריצת סיסמאות, deauthentication, יירוט תעבורה או סריקה לא מורשית."
        root.addView(hackerInfo)

        val ctfButton = Button(this).apply {
            text = "🧩 פתח CTF לימודי"
            setOnClickListener {
                diagnosis.text = "🧩 CTF מקומי — שלב 1\n\n" +
                        "מטרה: זהה את המושג שמתאר תרגום שם מתחם לכתובת IP.\n\n" +
                        "א) DHCP\nב) DNS\nג) WPA3\nד) BSSID\n\n" +
                        "תשובה נכונה: ב) DNS\n\nזהו תרגול מקומי בלבד — אין חיבור או תקיפה של רשת."
            }
        }
        root.addView(ctfButton, LinearLayout.LayoutParams(-1, 58))

        val passwordButton = Button(this).apply {
            text = "🔐 בדיקת חוזק סיסמה מקומית"
            setOnClickListener { showPasswordStrengthDialog() }
        }
        root.addView(passwordButton, LinearLayout.LayoutParams(-1, 58))

        val attackLabButton = Button(this).apply {
            text = "🎯 הפעל חדירה מבוקרת למעבדת CTF"
            setOnClickListener { showControlledAttackLab() }
        }
        root.addView(attackLabButton, LinearLayout.LayoutParams(-1, 58))

        val hashButton = Button(this).apply {
            text = "🧬 הדגמת Hash + Salt"
            setOnClickListener { showHashDemoDialog() }
        }
        root.addView(hashButton, LinearLayout.LayoutParams(-1, 58))

        val hardeningButton = Button(this).apply {
            text = "🛡️ בדיקת הקשחת רשת"
            setOnClickListener { showHardeningChecklist() }
        }
        root.addView(hardeningButton, LinearLayout.LayoutParams(-1, 58))

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

    private fun showControlledAttackLab() {
        val timestamp = now()
        diagnosis.text = "🎯 חדירה מבוקרת — מצב מעבדה\n\n" +
                "1. 🎯 יעד: Lab CTF מקומי בלבד\n" +
                "2. 🔎 זיהוי שירות: יעד תרגול וירטואלי\n" +
                "3. 🔐 בדיקת התחברות: ניסיון דמה מבוקר\n" +
                "4. 🧱 תגובת יעד: הניסיון נרשם ונחסם לפי מדיניות המעבדה\n" +
                "5. 🚨 התראה: פעילות חריגה זוהתה\n" +
                "6. 🛡️ Blue Team: האירוע נוסף ליומן ההגנה\n" +
                "7. ✅ סיום: אין פנייה לשרתים חיצוניים ואין הסתרת פעילות\n\n" +
                "לוג מעבדה:\n" +
                "[" + timestamp + "] LAB_START target=LOCAL_CTF\n" +
                "[" + now() + "] AUTH_TEST result=BLOCKED\n" +
                "[" + now() + "] DETECTION alert=TRIGGERED\n" +
                "[" + now() + "] LAB_END status=COMPLETED\n\n" +
                "הסימולציה גלויה ומיועדת רק לתרגול. היא אינה תוקפת שרת אמיתי ואינה מנסה לעקוף זיהוי."
    }

    private fun showPasswordStrengthDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "הזן סיסמה לבדיקה מקומית"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("בדיקת חוזק מקומית")
            .setMessage("הסיסמה נשארת במכשיר ואינה נשלחת לשום שרת.")
            .setView(input)
            .setPositiveButton("בדוק") { _, _ ->
                diagnosis.text = "🔐 תוצאת בדיקה מקומית\n\n" + passwordScore(input.text.toString())
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun passwordScore(password: String): String {
        if (password.isEmpty()) return "לא הוזנה סיסמה."
        var score = 0
        if (password.length >= 12) score += 2 else if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        val common = setOf("password", "12345678", "123456789", "qwerty", "letmein", "admin", "wifi")
        if (common.contains(password.lowercase(Locale.getDefault()))) score = maxOf(0, score - 3)
        val level = when { score >= 6 -> "חזק"; score >= 4 -> "בינוני"; else -> "חלש" }
        val tips = mutableListOf<String>()
        if (password.length < 12) tips.add("השתמש ב-12 תווים או יותר")
        if (!password.any { it.isUpperCase() }) tips.add("הוסף אות גדולה")
        if (!password.any { it.isLowerCase() }) tips.add("הוסף אות קטנה")
        if (!password.any { it.isDigit() }) tips.add("הוסף ספרה")
        if (!password.any { !it.isLetterOrDigit() }) tips.add("הוסף סימן מיוחד")
        return "רמה: " + level + " (ציון " + score + "/7)\n\n" +
                if (tips.isEmpty()) "אין הצעות נוספות לפי הבדיקה המקומית." else "המלצות:\n• " + tips.joinToString("\n• ")
    }

    private fun showHashDemoDialog() {
        val input = android.widget.EditText(this).apply { hint = "טקסט לדוגמה" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Hash + Salt — הדגמה")
            .setMessage("ההדגמה מתבצעת מקומית בלבד ואינה מפצחת סיסמאות.")
            .setView(input)
            .setPositiveButton("חשב") { _, _ ->
                val text = input.text.toString()
                val salt = "WiFiLabDemoSalt"
                diagnosis.text = "🧬 Hash + Salt\n\nSalt לדוגמה: " + salt +
                        "\nSHA-256(text + salt):\n" + sha256(text + salt) +
                        "\n\nהדגמה לימודית בלבד."
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun showHardeningChecklist() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = try { wifi.connectionInfo } catch (_: Exception) { null }
        val ssid = info?.ssid?.trim('"') ?: "לא זמין"
        val speed = if ((info?.linkSpeed ?: -1) >= 0) info?.linkSpeed.toString() + " Mbps" else "לא זמין"
        diagnosis.text = "🛡️ הקשחת רשת — בדיקות בטוחות\n\n" +
                "חיבור Wi‑Fi: " + if (wifiConnected) "✅" else "❌" + "\n" +
                "SSID: " + ssid + "\n" +
                "מהירות קישור: " + speed + "\n\n" +
                "Checklist לנתב שלך:\n" +
                "☐ השתמש ב-WPA2-AES או WPA3\n" +
                "☐ כבה WEP ותקנים ישנים אם אינם נחוצים\n" +
                "☐ השתמש בסיסמת Wi‑Fi ייחודית וחזקה\n" +
                "☐ עדכן קושחת נתב\n" +
                "☐ שנה סיסמת ניהול ברירת מחדל\n" +
                "☐ כבה WPS אם אינך זקוק לו\n" +
                "☐ בדוק אילו מכשירים מורשים להתחבר\n\n" +
                "הבדיקה אינה משנה הגדרות ואינה תוקפת את הרשת."
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
