package com.trendscout.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import com.trendscout.app.R
import com.trendscout.core.model.Countries
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date
import java.util.Locale

object Fmt {
    private fun dec(pattern: String) = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.getDefault()))

    fun count(r: Resources, n: Long?): String {
        if (n == null) return "—"
        val a = kotlin.math.abs(n.toDouble())
        return when {
            a >= 1e9 -> dec("#.#").format(n / 1e9) + " " + r.getString(R.string.num_billion)
            a >= 1e6 -> dec("#.#").format(n / 1e6) + " " + r.getString(R.string.num_million)
            a >= 1e4 -> dec("#.#").format(n / 1e3) + " " + r.getString(R.string.num_thousand)
            else -> dec("#,###").format(n)
        }
    }

    fun countD(r: Resources, d: Double?): String = if (d == null) "—" else count(r, d.toLong())

    fun rate(r: Resources, perHour: Double?): String =
        if (perHour == null) "—" else (if (perHour >= 0) "+" else "") + countD(r, perHour) + r.getString(R.string.per_hour)

    fun percent(x: Double?): String = if (x == null) "—" else dec("#.#").format(x * 100) + "%"

    fun ratio(x: Double?): String = if (x == null) "—" else dec(if (x >= 10) "#" else "#.#").format(x)

    fun date(ms: Long?): String = if (ms == null) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

    fun dateTime(ms: Long?): String =
        if (ms == null) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    fun relative(ms: Long?, now: Long = System.currentTimeMillis()): String =
        if (ms == null) "—" else DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS).toString()

    fun hours(r: Resources, h: Double?): String = when {
        h == null -> "—"
        h < 48 -> r.getString(R.string.hours_short, h.toInt())
        else -> r.getString(R.string.days_short, (h / 24).toInt())
    }

    fun country(code: String): String = Countries.flag(code) + " " + Countries.name(code, Locale.getDefault())

    fun countryShort(code: String): String = Countries.flag(code) + " " + code.uppercase()
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.err_no_browser, Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
