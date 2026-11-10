package org.phalanx.android.ui.screens.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.components.CopyIdChip
import org.phalanx.android.ui.components.HaloAvatar
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.theme.Symbols

/**
 * The account's face: the photo on its halo, the name, edited where it stands, and the Matrix ID,
 * which copies itself. Tapping the photo picks a new one; the camera badge says so.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileHeader(
    profile: AccountProfile,
    avatar: ImageBitmap?,
    state: AccountUiState,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onEditName: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onCancelName: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.clickable(
                enabled = !state.avatarBusy,
                onClickLabel = stringResource(R.string.account_photo_change),
                onClick = onPickPhoto
            )
        ) {
            HaloAvatar(haloSize = 148.dp, halo = colors.primaryContainer) {
                InitialAvatar(
                    name = profile.label,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    size = 108.dp,
                    container = colors.primary,
                    content = colors.onPrimary,
                    image = avatar,
                    textStyle = MaterialTheme.typography.displaySmall
                )
                if (state.avatarBusy) LoadingIndicator(Modifier.size(48.dp))
            }
            Surface(
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                border = BorderStroke(3.dp, colors.surface),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp).size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Symbols.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.avatarFailed) {
            Text(
                stringResource(R.string.account_photo_failed),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (profile.avatarUrl != null) {
            TextButton(onClick = onRemovePhoto, enabled = !state.avatarBusy) {
                Text(stringResource(R.string.account_photo_remove))
            }
        }
        val draft = state.nameDraft
        if (draft == null) {
            NameButton(profile, onEditName)
        } else {
            NameEditor(draft, state.savingName, state.nameFailed, onNameChange, onSaveName, onCancelName)
        }
        CopyIdChip(profile.userId, stringResource(R.string.account_copy_id), outlined = true)
    }
}

@Composable
private fun NameButton(profile: AccountProfile, onEdit: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.account_edit_name), onClick = onEdit)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // A new name pops in, so a save is seen to have landed.
        AnimatedContent(
            targetState = profile.label,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = NAME_FROM))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "name"
        ) { name ->
            Text(
                name,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Icon(Symbols.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The name as a field in place of itself, saved or dropped right there. */
@Composable
private fun NameEditor(
    draft: String,
    saving: Boolean,
    failed: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = draft,
            onValueChange = { if (it.length <= MAX_NAME) onChange(it) },
            singleLine = true,
            enabled = !saving,
            isError = failed,
            shape = RoundedCornerShape(28.dp),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) onSave() }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focus)
        )
        if (failed) {
            Text(
                stringResource(R.string.account_name_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.account_cancel)) }
            Button(onClick = onSave, enabled = draft.isNotBlank() && !saving) {
                if (saving) LoadingIndicator(Modifier.size(20.dp)) else Text(stringResource(R.string.account_save))
            }
        }
    }
}

private const val NAME_FROM = 0.86f

// Matrix allows longer, but other clients cut a display name past this.
private const val MAX_NAME = 40
