package org.phalanx.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.model.UserTrust
import org.phalanx.android.ui.theme.Symbols

/** Whether someone is verified, in a line under their name; a changed identity reads as an error. */
@Composable
internal fun TrustLabel(trust: UserTrust, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, label, color) = when (trust) {
        UserTrust.Verified -> Triple(Symbols.VerifiedUser, R.string.trust_verified, colors.primary)
        UserTrust.Unverified -> Triple(Symbols.Shield, R.string.trust_unverified, colors.onSurfaceVariant)
        UserTrust.Changed -> Triple(Symbols.GppMaybe, R.string.trust_changed, colors.error)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color)
    }
}
