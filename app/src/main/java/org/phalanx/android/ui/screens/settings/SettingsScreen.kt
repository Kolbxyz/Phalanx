package org.phalanx.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.phalanx.android.R
import org.phalanx.android.data.repository.AccountProfile
import org.phalanx.android.ui.components.BackButton
import org.phalanx.android.ui.components.InitialAvatar
import org.phalanx.android.ui.components.SegmentGap
import org.phalanx.android.ui.components.SegmentedRow
import org.phalanx.android.ui.components.segmentedShape
import org.phalanx.android.ui.theme.Symbols

/** The settings home: who is signed in, then one row per page of settings. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenBridges: () -> Unit,
    onOpenCategory: (SettingsCategory) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentGap)
        ) {
            ProfileCard(profile, avatar, onOpenAccount)
            Spacer(Modifier.height(16.dp))
            SegmentedRow(
                headline = stringResource(R.string.bridges_title),
                supporting = stringResource(R.string.bridges_summary),
                shape = segmentedShape(0, 1),
                leading = { Icon(Symbols.Hub, contentDescription = null) },
                trailing = { Icon(Symbols.KeyboardArrowRight, contentDescription = null) },
                interaction = Modifier.clickable { onOpenBridges() }
            )
            Spacer(Modifier.height(16.dp))
            val categories = SettingsCategory.entries
            categories.forEachIndexed { index, category ->
                SegmentedRow(
                    headline = stringResource(category.title),
                    supporting = stringResource(category.summary),
                    shape = segmentedShape(index, categories.size),
                    leading = { CategoryIcon(category, index) },
                    trailing = { Icon(Symbols.KeyboardArrowRight, contentDescription = null) },
                    interaction = Modifier.clickable { onOpenCategory(category) }
                )
            }
        }
    }
}

/** The signed-in account with its photo and display name; opens the account page. */
@Composable
private fun ProfileCard(profile: AccountProfile?, avatar: ImageBitmap?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(
                name = profile?.label.orEmpty(),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                size = 72.dp,
                container = colors.primary,
                content = colors.onPrimary,
                image = avatar,
                textStyle = MaterialTheme.typography.headlineMedium
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile?.label ?: stringResource(R.string.loading),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                profile?.takeIf { it.hasDisplayName }?.let {
                    Text(
                        text = it.userId,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(R.string.settings_profile_summary),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private const val ACCENTS = 3

/** Each page gets its own shape and accent, so the list scans by shape as much as by word. */
@Composable
private fun CategoryIcon(category: SettingsCategory, index: Int) {
    val colors = MaterialTheme.colorScheme
    val shapes = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Pill,
        MaterialShapes.Clover4Leaf, MaterialShapes.Cookie6Sided, MaterialShapes.Circle
    )
    val (container, content) = when (index % ACCENTS) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(shape = shapes[index % shapes.size].toShape(), color = container, contentColor = content) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(category.icon), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}
