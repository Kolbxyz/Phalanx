package org.phalanx.android.ui.screens.room

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.phalanx.android.R
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

private const val EVENING_HOUR = 20
private const val MORNING_HOUR = 9

/** When to send the message being typed: a few one-tap choices, or any date and time. */
@Composable
internal fun ScheduleSheet(exact: Boolean, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    var picking by remember { mutableStateOf(false) }
    ModalSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.schedule_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        Choice(Symbols.Timer, stringResource(R.string.schedule_in_hour)) { onPick(now.plusHours(1).millis()) }
        if (now.hour < EVENING_HOUR - 1) {
            Choice(Symbols.Bedtime, stringResource(R.string.schedule_tonight)) {
                onPick(now.with(LocalTime.of(EVENING_HOUR, 0)).millis())
            }
        }
        Choice(Symbols.WbSunny, stringResource(R.string.schedule_tomorrow_morning)) {
            onPick(now.plusDays(1).with(LocalTime.of(MORNING_HOUR, 0)).millis())
        }
        Choice(Symbols.EditCalendar, stringResource(R.string.schedule_pick)) { picking = true }
        if (!exact) ExactTimingRow()
        Spacer(Modifier.height(16.dp))
    }
    if (picking) DateTimeDialog(onDismiss = { picking = false }, onPick = onPick)
}

@Composable
private fun Choice(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Shown when the system only allows approximate alarms; one tap opens the switch that fixes it. */
@Composable
private fun ExactTimingRow() {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(stringResource(R.string.schedule_exact)) },
        supportingContent = { Text(stringResource(R.string.schedule_exact_summary)) },
        leadingContent = { Icon(Symbols.AlarmOn, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                )
            }
        }
    )
}

/** A date, then a time; only future days can be chosen. */
@Composable
private fun DateTimeDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val today = LocalDate.now()
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        }
    )
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val chosen = date
    if (chosen == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    date = dateState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text(stringResource(R.string.schedule_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(dateState) }
        return
    }
    val next = LocalTime.now().plusHours(1)
    val timeState = rememberTimePickerState(initialHour = next.hour, initialMinute = 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_pick)) },
        text = { TimePicker(timeState) },
        confirmButton = {
            TextButton(onClick = {
                val at = chosen.atTime(timeState.hour, timeState.minute).atZone(ZoneId.systemDefault())
                // A time already past today means the user wants it now rather than never.
                onPick(maxOf(at.millis(), System.currentTimeMillis()))
                onDismiss()
            }) { Text(stringResource(R.string.schedule_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun ZonedDateTime.millis(): Long = toInstant().toEpochMilli()
