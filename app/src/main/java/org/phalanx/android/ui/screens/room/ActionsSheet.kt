package org.phalanx.android.ui.screens.room

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.phalanx.android.R
import org.phalanx.android.core.ai.actions.MessageAction
import org.phalanx.android.ui.components.ModalSheet
import org.phalanx.android.ui.theme.Symbols

/**
 * What the model found in a message, each one tap away from the app that takes it: the calendar,
 * the clock, a to-do app, maps or the dialer. Reminders are set here; tracking codes are copied.
 */
@Composable
internal fun ActionsSheet(
    state: ActionsState,
    onDismiss: () -> Unit,
    onRemind: (MessageAction.Reminder) -> Unit,
    onCopy: (String) -> Unit,
    onNoApp: () -> Unit
) {
    if (state == ActionsState.Idle) return
    val context = LocalContext.current
    ModalSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.actions_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        when (state) {
            ActionsState.Idle -> Unit
            ActionsState.Looking -> ListItem(
                headlineContent = { Text(stringResource(R.string.actions_looking)) },
                leadingContent = { LoadingIndicator() }
            )
            is ActionsState.Failed -> ListItem(headlineContent = { Text(stringResource(state.message)) })
            is ActionsState.Found -> {
                if (state.actions.isEmpty()) ListItem(headlineContent = { Text(stringResource(R.string.actions_none)) })
                state.actions.forEach { action ->
                    ActionRow(action) {
                        when (action) {
                            is MessageAction.Reminder -> onRemind(action)
                            is MessageAction.Tracking -> onCopy(action.code)
                            else -> try {
                                action.intent(ZoneId.systemDefault())?.let(context::startActivity)
                            } catch (_: ActivityNotFoundException) {
                                onNoApp()
                            }
                        }
                        onDismiss()
                    }
                }
                Text(
                    text = stringResource(R.string.actions_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionRow(action: MessageAction, onClick: () -> Unit) {
    val (icon, label) = iconAndLabel(action)
    ListItem(
        headlineContent = { Text(action.title) },
        supportingContent = { Text(listOfNotNull(stringResource(label), detailOf(action)).joinToString(" · ")) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun iconAndLabel(action: MessageAction): Pair<ImageVector, Int> = when (action) {
    is MessageAction.Event -> Symbols.EditCalendar to R.string.actions_event
    is MessageAction.Alarm -> Symbols.AlarmOn to R.string.actions_alarm
    is MessageAction.Timer -> Symbols.Timer to R.string.actions_timer
    is MessageAction.Reminder -> Symbols.NotificationsActive to R.string.actions_reminder
    is MessageAction.Task -> Symbols.CheckCircle to R.string.actions_task
    is MessageAction.Place -> Symbols.LocationOn to R.string.actions_place
    is MessageAction.Phone -> Symbols.Call to R.string.actions_phone
    is MessageAction.Tracking -> Symbols.ContentCopy to R.string.actions_tracking
}

@Composable
private fun detailOf(action: MessageAction): String? = when (action) {
    is MessageAction.Event -> if (action.allDay) {
        DATE.format(action.start)
    } else {
        listOfNotNull(DATE_TIME.format(action.start), action.place).joinToString(" · ")
    }
    is MessageAction.Alarm -> TIME.format(action.at)
    is MessageAction.Timer -> stringResource(R.string.actions_minutes, action.minutes)
    is MessageAction.Reminder -> DATE_TIME.format(action.at)
    is MessageAction.Task -> null
    is MessageAction.Place -> action.address
    is MessageAction.Phone -> action.number
    is MessageAction.Tracking -> action.code
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
