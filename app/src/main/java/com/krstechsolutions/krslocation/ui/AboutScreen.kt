package com.krstechsolutions.krslocation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Smartphone
import com.krstechsolutions.krslocation.BuildConfig
import com.krstechsolutions.krslocation.DeveloperInfo
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.Screen
import com.krstechsolutions.krslocation.ui.components.AlertVariant
import com.krstechsolutions.krslocation.ui.components.ButtonIcon
import com.krstechsolutions.krslocation.ui.components.ButtonSize
import com.krstechsolutions.krslocation.ui.components.ButtonVariant
import com.krstechsolutions.krslocation.ui.components.ShadAlert
import com.krstechsolutions.krslocation.ui.components.ShadBadge
import com.krstechsolutions.krslocation.ui.components.BadgeVariant
import com.krstechsolutions.krslocation.ui.components.ShadButton
import com.krstechsolutions.krslocation.ui.components.ShadCard
import com.krstechsolutions.krslocation.ui.components.ShadListRow
import com.krstechsolutions.krslocation.ui.components.ShadSeparator
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme
import com.krstechsolutions.krslocation.update.UpdateInstaller

@Composable
fun AboutScreen(vm: MainViewModel) {
    val c = ShadTheme.colors
    val context = LocalContext.current

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ShadButton(onClick = { vm.screen = Screen.Home }, variant = ButtonVariant.Ghost, size = ButtonSize.Icon) {
                Icon(Lucide.ArrowLeft, stringResource(R.string.action_back), Modifier.size(20.dp))
            }
            Text(stringResource(R.string.about_title), style = ShadTheme.typography.h4)
        }
        ShadSeparator()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App identity
            ShadCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(ShadRadius.xlShape)
                            .background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.MapPin, null, Modifier.size(28.dp), tint = c.primaryForeground)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = ShadTheme.typography.h3)
                        Text(
                            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = ShadTheme.typography.muted,
                            color = c.mutedForeground,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_tagline), style = ShadTheme.typography.p, color = c.mutedForeground)
            }

            // Updates
            SectionLabel(stringResource(R.string.about_updates))
            ShadCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                ShadListRow(
                    title = stringResource(R.string.about_installed_version),
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Lucide.Package,
                    trailing = {
                        when {
                            vm.updateAvailable != null -> ShadBadge(vm.updateAvailable?.version ?: "", variant = BadgeVariant.Default)
                            vm.updateChecked -> Icon(Lucide.CircleCheck, null, Modifier.size(18.dp), tint = c.success)
                        }
                    },
                )
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShadButton(
                        onClick = { vm.checkForUpdates(manual = true) },
                        variant = ButtonVariant.Outline,
                        enabled = !vm.updateChecking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonIcon(Lucide.RefreshCw)
                        Text(stringResource(if (vm.updateChecking) R.string.about_checking else R.string.about_check_updates))
                    }

                    val release = vm.latestRelease
                    when {
                        release != null && release.isNewerThanInstalled -> ShadAlert(
                            title = stringResource(R.string.about_update_available, release.version),
                            description = release.notes.trim().take(400).ifEmpty { release.name },
                            icon = Lucide.Download,
                            variant = AlertVariant.Default,
                            action = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShadButton(
                                        onClick = { UpdateInstaller.download(context, release) },
                                        size = ButtonSize.Sm,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        ButtonIcon(Lucide.Download)
                                        Text(stringResource(R.string.about_download_install))
                                    }
                                    ShadButton(
                                        onClick = { open(release.htmlUrl) },
                                        variant = ButtonVariant.Ghost,
                                        size = ButtonSize.Sm,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        ButtonIcon(Lucide.ExternalLink)
                                        Text(stringResource(R.string.about_view_release))
                                    }
                                }
                            },
                        )
                        release != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Icon(Lucide.CircleCheck, null, Modifier.size(16.dp), tint = c.success)
                            Text(stringResource(R.string.about_up_to_date), style = ShadTheme.typography.muted, color = c.mutedForeground)
                        }
                        vm.updateError != null -> Text(
                            stringResource(R.string.about_update_error, vm.updateError ?: ""),
                            style = ShadTheme.typography.muted,
                            color = c.mutedForeground,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            // Developer
            SectionLabel(stringResource(R.string.about_developer))
            ShadCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                ShadListRow(
                    title = DeveloperInfo.NAME,
                    subtitle = stringResource(R.string.about_developer),
                    icon = Lucide.Smartphone,
                )
                ShadListRow(
                    title = stringResource(R.string.about_contact),
                    subtitle = DeveloperInfo.EMAIL,
                    icon = Lucide.Mail,
                    onClick = { open("mailto:${DeveloperInfo.EMAIL}") },
                    trailing = { Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = c.mutedForeground) },
                )
                ShadListRow(
                    title = stringResource(R.string.about_website),
                    subtitle = DeveloperInfo.WEBSITE.removePrefix("https://"),
                    icon = Lucide.Globe,
                    onClick = { open(DeveloperInfo.WEBSITE) },
                    trailing = { Icon(Lucide.ExternalLink, null, Modifier.size(16.dp), tint = c.mutedForeground) },
                )
                ShadListRow(
                    title = stringResource(R.string.about_source),
                    subtitle = DeveloperInfo.SOURCE.removePrefix("https://"),
                    icon = Lucide.Code,
                    onClick = { open(DeveloperInfo.SOURCE) },
                    trailing = { Icon(Lucide.ExternalLink, null, Modifier.size(16.dp), tint = c.mutedForeground) },
                )
            }

            // How it works / credits
            SectionLabel(stringResource(R.string.about_how_title))
            ShadCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_how_desc), style = ShadTheme.typography.p, color = c.mutedForeground)
            }
            SectionLabel(stringResource(R.string.about_built_with))
            ShadCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.about_built_with_desc),
                    style = ShadTheme.typography.p,
                    color = c.mutedForeground,
                    overflow = TextOverflow.Visible,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
