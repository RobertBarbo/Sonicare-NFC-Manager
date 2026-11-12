package si.robi.sonicarenfc

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.content.res.Configuration
import android.widget.PopupMenu
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lightweight, offline, dependency-free UI for Sonicare BrushSync heads.
 * Scanning is always read-only. A write requires confirmation AND a new NFC tap.
 */
class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private var nfcAdapter: NfcAdapter? = null
    private val busy = AtomicBoolean(false)
    @Volatile private var armedUid: String? = null
    @Volatile private var lastRead: SonicareProtocol.Head? = null

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var tagState: TextView
    private lateinit var uidView: TextView
    private lateinit var mfgView: TextView
    private lateinit var usedView: TextView
    private lateinit var remainingView: TextView
    private lateinit var ring: UseRingView
    private lateinit var resetButton: Button
    private lateinit var langButton: TextView
    private val preferences by lazy { getSharedPreferences("sonicare_preferences", MODE_PRIVATE) }
    private var language = "sl"
    private val darkMode by lazy { (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
    private fun tr(sl: String, en: String): String = if (language == "sl") sl else en

    private val background by lazy { if (darkMode) Color.rgb(17, 26, 28) else Color.rgb(246, 249, 248) }
    private val cardBackground by lazy { if (darkMode) Color.rgb(26, 39, 41) else Color.WHITE }
    private val ink by lazy { if (darkMode) Color.rgb(232, 245, 243) else Color.rgb(28, 52, 50) }
    private val muted by lazy { if (darkMode) Color.rgb(163, 190, 186) else Color.rgb(91, 117, 114) }
    private val teal by lazy { if (darkMode) Color.rgb(72, 197, 174) else Color.rgb(17, 126, 113) }
    private val tealLight by lazy { if (darkMode) Color.rgb(25, 64, 59) else Color.rgb(227, 244, 238) }
    private val amber by lazy { if (darkMode) Color.rgb(243, 186, 105) else Color.rgb(172, 101, 19) }
    private val border by lazy { if (darkMode) Color.rgb(55, 77, 75) else Color.rgb(222, 231, 227) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        language = preferences.getString("language", "sl") ?: "sl"
        window.statusBarColor = if (darkMode) Color.rgb(11, 20, 22) else Color.rgb(20, 68, 63)
        window.navigationBarColor = background
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (darkMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(if (darkMode) Color.rgb(18, 30, 30) else Color.rgb(248, 250, 249))
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }
        setContentView(scroll)

        // Brand header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "◉"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(teal, 15)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(52), dp(52)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titles.addView(text("SONICARE", 12f, true, teal).apply { letterSpacing = 0.15f })
        titles.addView(text("NFC Manager", 25f, true, ink))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // The flag is a real menu, not merely a language indicator.
        langButton = text(if (language == "sl") "🇸🇮" else "🇬🇧", 24f, false, ink).apply {
            contentDescription = tr("Izberi jezik", "Choose language")
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { showLanguageMenu(this) }
        }
        header.addView(langButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        val infoButton = text("ⓘ", 25f, true, teal).apply {
            gravity = Gravity.CENTER
            contentDescription = tr("O aplikaciji", "About this app")
            setOnClickListener { showAbout() }
        }
        header.addView(infoButton, LinearLayout.LayoutParams(dp(38), dp(48)))
        root.addView(header)
        space(root, 12)
        root.addView(text(tr("Preveri uporabo glave in po potrebi ponastavi elektronski števec.", "Read the brush head and reset its electronic usage counter if needed."), 14f, false, muted))
        space(root, 21)

        // Status card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(tealLight, 17)
        }
        statusDot = View(this).apply { background = rounded(teal, 20) }
        statusCard.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)))
        statusText = text(tr("Prisloni glavo k zadnji strani telefona.", "Hold the brush head near the back of your phone."), 14f, true, ink)
        statusCard.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        root.addView(statusCard)
        space(root, 17)

        // Large usage card
        val usageCard = card()
        usageCard.addView(text(tr("PORABLJEN ČAS", "USAGE"), 12f, true, muted).apply { letterSpacing = 0.14f })
        space(usageCard, 8)
        ring = UseRingView()
        usageCard.addView(ring, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(232)))
        space(usageCard, 4)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val usedBlock = stat(tr("UPORABLJENO", "USED"))
        usedView = usedBlock.second
        val left = usedBlock.first
        stats.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sep = View(this).apply { setBackgroundColor(border) }
        stats.addView(sep, LinearLayout.LayoutParams(dp(1), dp(46)).apply { marginEnd = dp(16) })
        val remainingBlock = stat(tr("PREOSTANE ŠE", "REMAINING"))
        remainingView = remainingBlock.second
        stats.addView(remainingBlock.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        usageCard.addView(stats)
        root.addView(usageCard)
        space(root, 15)

        // Chip card
        val chipCard = card()
        val chipHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chipHeader.addView(text(tr("Podatki glave", "Brush head details"), 19f, true, ink), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tagState = text(tr("NI PREBRANO", "NOT SCANNED"), 11f, true, muted).apply {
            background = rounded(Color.rgb(237, 242, 240), 10)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        chipHeader.addView(tagState)
        chipCard.addView(chipHeader)
        space(chipCard, 17)
        chipCard.addView(text(tr("PROIZVODNA OZNAKA", "MANUFACTURING CODE"), 11f, true, muted).apply { letterSpacing = 0.1f })
        mfgView = text("—", 19f, true, ink)
        chipCard.addView(mfgView)
        space(chipCard, 15)
        chipCard.addView(View(this).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)))
        space(chipCard, 15)
        chipCard.addView(text(tr("UID ČIPA", "TAG UID"), 11f, true, muted).apply { letterSpacing = 0.1f })
        uidView = text("—", 16f, true, ink).apply { setTextIsSelectable(true) }
        chipCard.addView(uidView)
        root.addView(chipCard)
        space(root, 8)
        val historyButton = text(tr("Zgodovina skeniranj  ›", "Scan history  ›"), 14f, true, teal).apply {
            gravity = Gravity.END
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { showHistory() }
        }
        root.addView(historyButton)
        space(root, 10)

        resetButton = Button(this).apply {
            text = tr("PONASTAVI ŠTEVEC", "RESET COUNTER")
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(17, 126, 113), 16)
            isEnabled = false
            alpha = 0.50f
            elevation = dp(2).toFloat()
            setOnClickListener { askForReset() }
        }
        root.addView(resetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        space(root, 13)
        root.addView(text(tr("Ponastavitev zahteva potrditev in ponovno prislonitev iste glave. Aplikacija preveri, ali je bil zapis uspešen.", "Reset requires confirmation and scanning the same brush head again. The app verifies the write."), 13f, false, muted))
        space(root, 16)
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(if (darkMode) Color.rgb(63, 48, 27) else Color.rgb(255, 246, 226), 16)
        }
        note.addView(text(tr("Pomembno", "Important"), 14f, true, amber))
        space(note, 5)
        note.addView(text(tr("Ponastavitev odstrani elektronsko opozorilo, ne pa obrabe ščetin. Obrabljeno glavo zamenjaj. Uporabi samo na svojih združljivih glavah BrushSync.", "Reset only clears the electronic warning, not bristle wear. Replace worn brush heads. Use only with your own compatible BrushSync heads."), 13f, false, if (darkMode) Color.rgb(239, 216, 172) else Color.rgb(105, 74, 29)))
        root.addView(note)
        space(root, 20)
        root.addView(text(tr("BREZ INTERNETA  •  BREZ RAČUNA  •  LOKALNI PODATKI", "OFFLINE  •  NO ACCOUNT  •  LOCAL DATA"), 10f, true, muted).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.07f
        })

        if (savedInstanceState != null) {
            val uid = savedInstanceState.getString("uid")
            val mfg = savedInstanceState.getString("mfg")
            val page = savedInstanceState.getByteArray("page")
            if (uid != null && mfg != null && page != null && page.size == 4) {
                val restored = SonicareProtocol.Head(uid, mfg,
                    savedInstanceState.getInt("used"), savedInstanceState.getInt("target", 21600), page)
                lastRead = restored
                display(restored)
                showStatus(tr("Glava uspešno prebrana.", "Brush head successfully scanned."), true)
            }
        }
        if (nfcAdapter == null) showStatus(tr("Ta telefon nima NFC vmesnika.", "This phone has no NFC reader."), false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastRead?.let {
            outState.putString("uid", it.uid)
            outState.putString("mfg", it.mfg)
            outState.putInt("used", it.usedSeconds)
            outState.putInt("target", it.targetSeconds)
            outState.putByteArray("page", it.page24)
        }
        super.onSaveInstanceState(outState)
    }

    private fun showLanguageMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "🇸🇮 Slovenščina")
        menu.menu.add(0, 2, 1, "🇬🇧 English")
        menu.setOnMenuItemClickListener { item ->
            val selected = if (item.itemId == 1) "sl" else "en"
            if (selected != language) {
                armedUid = null
                preferences.edit().putString("language", selected).apply()
                recreate()
            }
            true
        }
        menu.show()
    }

    private fun showAbout() {
        val content = tr(
            "Različica: 1.0.0\nAvtor: Robert Barbirič\nLicenca: MIT\n\nNeuradna odprtokodna aplikacija. Brez povezave s Philipsom. Deluje brez interneta in ne zbira osebnih podatkov.",
            "Version: 1.0.0\nAuthor: Robert Barbirič\nLicense: MIT\n\nUnofficial open-source application, not affiliated with Philips. Works offline and does not collect personal information."
        )
        AlertDialog.Builder(this)
            .setTitle(tr("O aplikaciji", "About"))
            .setMessage(content)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun recordHistory(head: SonicareProtocol.Head, reset: Boolean) {
        try {
            val old = JSONArray(preferences.getString("history", "[]"))
            val next = JSONArray()
            next.put(JSONObject().put("uid", head.uid).put("mfg", head.mfg)
                .put("time", System.currentTimeMillis()).put("used", head.usedSeconds)
                .put("reset", reset))
            for (i in 0 until min(19, old.length())) next.put(old.get(i))
            preferences.edit().putString("history", next.toString()).apply()
        } catch (_: Exception) { /* Never interrupt NFC operations for history. */ }
    }

    private fun showHistory() {
        val entries = try { JSONArray(preferences.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        val message = if (entries.length() == 0) tr("Zgodovina je prazna.", "No history yet.") else
            (0 until entries.length()).joinToString("\n\n") { i ->
                val row = entries.getJSONObject(i)
                val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.optLong("time")))
                val action = if (row.optBoolean("reset")) tr("Ponastavitev", "Reset") else tr("Branje", "Scan")
                "$stamp • $action\n${row.optString("mfg")} • ${shortTime(row.optInt("used"))}\n${row.optString("uid")}"
            }
        AlertDialog.Builder(this)
            .setTitle(tr("Zgodovina (zadnjih 20 dogodkov)", "History (last 20 events)"))
            .setMessage(message)
            .setPositiveButton(tr("Zapri", "Close"), null)
            .show()
    }

    private fun localError(key: String): String = when (key) {
        "not_nfc_a" -> tr("Čip ni NFC-A.", "Tag is not NFC-A.")
        "wrong_head" -> tr("To je druga glava. Ponastavitev preklicana.", "Different brush head detected. Reset cancelled.")
        "not_scanned" -> tr("Najprej preberi glavo.", "Scan the brush head first.")
        "uid_length" -> tr("Nepričakovana dolžina UID.", "Unexpected UID length.")
        "mfg_invalid" -> tr("Nepodprta proizvodna oznaka.", "Unsupported manufacturing code.")
        "lifetime_invalid" -> tr("Nepodprta meja življenjske dobe.", "Unsupported usage limit.")
        "already_zero" -> tr("Števec je že na nič.", "Counter is already zero.")
        "auth_failed" -> tr("Čip ni potrdil gesla.", "Tag did not accept authentication.")
        "write_failed" -> tr("Čip ni potrdil zapisa.", "Tag did not acknowledge the write.")
        "verify_failed" -> tr("Zapisa ni bilo mogoče preveriti.", "Could not verify the write.")
        "read_failed" -> tr("NFC branje ni uspelo.", "NFC read failed.")
        else -> key
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            showStatus(tr("V nastavitvah telefona vključi NFC.", "Enable NFC in your phone settings."), false)
            return
        }
        adapter.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        armedUid = null
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    private fun askForReset() {
        val current = lastRead ?: return
        if (current.usedSeconds <= 0 || armedUid != null) return
        AlertDialog.Builder(this)
            .setTitle(tr("Ponastavi števec?", "Reset usage counter?"))
            .setMessage(
                tr("Glava", "Brush head") + ": ${current.mfg}\n" +
                tr("Trenutni čas", "Current usage") + ": ${displayTime(current.usedSeconds)}\n\n" +
                tr("Ponastavitev ne obnovi ščetin. Za zapis glavo odmakni in jo ponovno prisloni.", "Resetting does not renew bristles. Remove and re-present the brush head to write.")
            )
            .setNegativeButton(tr("Prekliči", "Cancel"), null)
            .setPositiveButton(tr("Ponastavi", "Reset")) { _, _ ->
                armedUid = current.uid
                showStatus(tr("Odmakni glavo in jo PONOVNO prisloni.", "Remove the head, then scan the SAME head again."), true)
                updateResetState(false)
            }
            .show()
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!busy.compareAndSet(false, true)) return
        try {
            val tech = NfcA.get(tag) ?: throw IllegalArgumentException("not_nfc_a")
            tech.use { nfca ->
                nfca.connect()
                val head = SonicareProtocol.read(tag, nfca)
                val requested = armedUid
                if (requested != null) {
                    armedUid = null  // at most one authentication/write attempt
                    require(requested == head.uid) { "wrong_head" }
                    val prior = lastRead ?: throw IllegalStateException("not_scanned")
                    val after = SonicareProtocol.reset(tag, nfca, prior)
                    lastRead = after
                    runOnUiThread {
                        display(after)
                        showStatus(tr("Ponastavitev uspešna in preverjena!", "Reset successful and verified!"), true)
                        recordHistory(after, true)
                    }
                } else {
                    lastRead = head
                    runOnUiThread {
                        display(head)
                        showStatus(tr("Glava uspešno prebrana.", "Brush head successfully scanned."), true)
                        recordHistory(head, false)
                    }
                }
            }
        } catch (e: Exception) {
            armedUid = null
            val message = localError(e.message ?: e.javaClass.simpleName)
            runOnUiThread {
                showStatus(tr("Napaka", "Error") + ": $message", false)
                updateResetState((lastRead?.usedSeconds ?: 0) > 0)
            }
        } finally {
            busy.set(false)
        }
    }

    private fun display(head: SonicareProtocol.Head) {
        uidView.text = head.uid
        mfgView.text = head.mfg
        usedView.text = shortTime(head.usedSeconds)
        remainingView.text = shortTime((head.targetSeconds - head.usedSeconds).coerceAtLeast(0))
        tagState.text = tr("PREBRANO", "SCANNED")
        tagState.setTextColor(teal)
        tagState.background = rounded(tealLight, 10)
        ring.update(head.usedSeconds, head.targetSeconds)
        updateResetState(head.usedSeconds > 0 && armedUid == null)
    }

    private fun showStatus(message: String, positive: Boolean) {
        statusText.text = message
        statusDot.background = rounded(if (positive) teal else amber, 20)
    }

    private fun updateResetState(enabled: Boolean) {
        resetButton.isEnabled = enabled
        resetButton.alpha = if (enabled) 1f else 0.5f
    }

    private fun shortTime(seconds: Int): String = when {
        seconds >= 3600 -> "%d h %02d min".format(seconds / 3600, (seconds % 3600) / 60)
        seconds >= 60 -> "%d min %02d s".format(seconds / 60, seconds % 60)
        else -> "$seconds s"
    }

    private fun displayTime(seconds: Int): String = "%d h %02d min %02d s".format(
        seconds / 3600, (seconds % 3600) / 60, seconds % 60
    )

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = rounded(cardBackground, 24, border, 1)
        elevation = dp(1).toFloat()
    }

    private fun stat(title: String): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(title, 10f, true, muted).apply { letterSpacing = 0.08f })
        space(box, 6)
        val value = text("—", 17f, true, ink)
        box.addView(value)
        return box to value
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int = Color.TRANSPARENT, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(x: Int): Int = (resources.displayMetrics.density * x).roundToInt()
    private fun sp(x: Float): Float = resources.displayMetrics.scaledDensity * x

    private inner class UseRingView : View(this@MainActivity) {
        private var seconds = 0
        private var target = 21600
        private var hasData = false
        private val arcRect = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun update(used: Int, limit: Int) {
            seconds = used
            target = limit.coerceAtLeast(1)
            hasData = true
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val stroke = dp(15).toFloat()
            val radius = min(width.toFloat(), height.toFloat()) * 0.40f
            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = if (darkMode) Color.rgb(51, 71, 69) else Color.rgb(226, 239, 235)
            canvas.drawArc(arcRect, -90f, 360f, false, paint)
            paint.color = if (seconds >= target) amber else teal
            val fraction = (seconds.toFloat() / target).coerceIn(0f, 1f)
            if (fraction > 0f) canvas.drawArc(arcRect, -90f, fraction * 360f, false, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = ink
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = sp(32f)
            val main = if (!hasData) "—" else if (seconds < 60) "$seconds s" else shortTime(seconds)
            canvas.drawText(main, cx, cy - dp(8), paint)
            paint.color = muted
            paint.typeface = Typeface.DEFAULT
            paint.textSize = sp(13f)
            canvas.drawText(if (!hasData) tr("Prisloni glavo", "Scan a brush head") else tr("od 6 ur uporabe", "of 6 hours of use"), cx, cy + dp(24), paint)
            if (hasData) {
                paint.textSize = sp(12f)
                paint.color = if (seconds >= target) amber else teal
                val percent = (seconds * 100f / target).roundToInt()
                canvas.drawText("$percent %", cx, cy + dp(47), paint)
            }
        }
    }
}
