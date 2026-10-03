package com.readvoice.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.annotation.StringRes
import com.readvoice.app.R
import com.readvoice.app.domain.model.AppError

/** The sentence shown to the user for each failure. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.NETWORK -> R.string.error_network
    AppError.UNAUTHORIZED -> R.string.error_session_expired
    AppError.RATE_LIMITED -> R.string.error_rate_limited
    AppError.SERVER -> R.string.error_server
    AppError.INVALID_RESPONSE -> R.string.error_invalid_response
    AppError.AUTH_REJECTED -> R.string.error_auth_rejected
    AppError.NOT_CONFIGURED -> R.string.error_not_configured
    AppError.NO_BROWSER -> R.string.error_no_browser
}

/** How long ago something happened, as a unit and a count. */
object RelativeTime {

    enum class Unit { NOW, MINUTES, HOURS, DAYS, WEEKS, YEARS }

    data class Span(val unit: Unit, val amount: Long)

    fun between(nowMillis: Long, thenMillis: Long): Span {
        val minutes = (nowMillis - thenMillis).coerceAtLeast(0) / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> Span(Unit.NOW, 0)
            hours < 1 -> Span(Unit.MINUTES, minutes)
            days < 1 -> Span(Unit.HOURS, hours)
            days < 7 -> Span(Unit.DAYS, days)
            days < 365 -> Span(Unit.WEEKS, days / 7)
            else -> Span(Unit.YEARS, days / 365)
        }
    }
}

/** Short age label such as "2h" or "3d". */
fun Context.relativeTimeLabel(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val span = RelativeTime.between(nowMillis, thenMillis)
    val amount = span.amount.toInt()
    return when (span.unit) {
        RelativeTime.Unit.NOW -> getString(R.string.time_now)
        RelativeTime.Unit.MINUTES -> getString(R.string.time_minutes, amount)
        RelativeTime.Unit.HOURS -> getString(R.string.time_hours, amount)
        RelativeTime.Unit.DAYS -> getString(R.string.time_days, amount)
        RelativeTime.Unit.WEEKS -> getString(R.string.time_weeks, amount)
        RelativeTime.Unit.YEARS -> getString(R.string.time_years, amount)
    }
}

/** Switches weight while keeping the font family chosen in Appearance. */
fun TextView.setBold(bold: Boolean) {
    typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
}
