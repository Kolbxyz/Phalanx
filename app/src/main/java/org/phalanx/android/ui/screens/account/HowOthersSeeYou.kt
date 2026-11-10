package org.phalanx.android.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.components.InitialAvatar

/** The name and photo as a chat shows them to others, following the name as it is typed. */
@Composable
internal fun HowOthersSeeYou(profile: AccountProfile, avatar: ImageBitmap?, nameDraft: String?) {
    val colors = MaterialTheme.colorScheme
    val name = nameDraft?.takeIf { it.isNotBlank() } ?: profile.label
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.account_how_others_see),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = Modifier.padding(start = 16.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                InitialAvatar(
                    name = name,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    size = 32.dp,
                    container = colors.primary,
                    content = colors.onPrimary,
                    image = avatar,
                    textStyle = MaterialTheme.typography.labelLarge
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHighest) {
                        Text(
                            stringResource(R.string.account_preview_bubble),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
