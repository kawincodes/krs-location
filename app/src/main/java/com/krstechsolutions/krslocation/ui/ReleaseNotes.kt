package com.krstechsolutions.krslocation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.krstechsolutions.krslocation.MainViewModel
import com.krstechsolutions.krslocation.R
import com.krstechsolutions.krslocation.ui.components.BadgeVariant
import com.krstechsolutions.krslocation.ui.components.ButtonIcon
import com.krstechsolutions.krslocation.ui.components.ButtonSize
import com.krstechsolutions.krslocation.ui.components.ButtonVariant
import com.krstechsolutions.krslocation.ui.components.ShadBadge
import com.krstechsolutions.krslocation.ui.components.ShadButton
import com.krstechsolutions.krslocation.ui.components.ShadListRow
import com.krstechsolutions.krslocation.ui.components.ShadSeparator
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme
import com.krstechsolutions.krslocation.update.MarkdownLite
import com.krstechsolutions.krslocation.update.ReleaseInfo
import com.krstechsolutions.krslocation.update.UpdateInstaller

/** Why the release notes sheet is open: an available update, or the version just installed. */
enum class ReleaseNotesMode { UPDATE, INSTALLED, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesSheet(vm: MainViewModel, release: ReleaseInfo, mode: ReleaseNotesMode) {
    val c = ShadTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val status = vm.installStatus
    val busy = status is UpdateInstaller.Status.Downloading || status is UpdateInstaller.Status.Verifying

    ModalBottomSheet(
        onDismissRequest = vm::dismissReleaseNotes,
        sheetState = sheetState,
        containerColor = c.card,
        contentColor = c.cardForeground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(c.primary, ShadRadius.lgShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Sparkles, null, Modifier.size(20.dp), tint = c.primaryForeground)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (mode == ReleaseNotesMode.INSTALLED) stringResource(R.string.whats_new_installed, release.version)
                        else stringResource(R.string.whats_new_title, release.version),
                        style = ShadTheme.typography.h4,
                    )
                    Text(
                        if (release.apkSizeLabel.isEmpty()) stringResource(R.string.release_meta_no_size, release.publishedDate)
                        else stringResource(R.string.release_meta, release.publishedDate, release.apkSizeLabel),
                        style = ShadTheme.typography.muted,
                        color = c.mutedForeground,
                    )
                }
                when {
                    release.prerelease -> ShadBadge(stringResource(R.string.badge_prerelease), variant = BadgeVariant.Outline)
                    release.isInstalledVersion -> ShadBadge(stringResource(R.string.badge_installed), variant = BadgeVariant.Secondary)
                    release.isNewerThanInstalled -> ShadBadge(stringResource(R.string.badge_new), variant = BadgeVariant.Success)
                }
            }

            ShadSeparator()

            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReleaseNotesBody(release.notes)
            }

            InstallStatusLine(status)

            when (mode) {
                ReleaseNotesMode.UPDATE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShadButton(
                        onClick = { vm.startUpdateDownload(release) },
                        size = ButtonSize.Lg,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonIcon(Lucide.Download)
                        Text(stringResource(R.string.about_download_install))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShadButton(
                            onClick = { UpdateInstaller.openInBrowser(context, release.htmlUrl) },
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.weight(1f),
                        ) {
                            ButtonIcon(Lucide.ExternalLink)
                            Text(stringResource(R.string.about_view_release))
                        }
                        ShadButton(onClick = vm::dismissReleaseNotes, variant = ButtonVariant.Ghost, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_later))
                        }
                    }
                }
                ReleaseNotesMode.INSTALLED, ReleaseNotesMode.HISTORY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShadButton(
                        onClick = { UpdateInstaller.openInBrowser(context, release.htmlUrl) },
                        variant = ButtonVariant.Outline,
                        modifier = Modifier.weight(1f),
                    ) {
                        ButtonIcon(Lucide.ExternalLink)
                        Text(stringResource(R.string.about_view_release))
                    }
                    ShadButton(onClick = vm::dismissReleaseNotes, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_got_it))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStatusLine(status: UpdateInstaller.Status) {
    val c = ShadTheme.colors
    val text = when (status) {
        is UpdateInstaller.Status.Downloading -> stringResource(R.string.update_downloading, status.release.version)
        is UpdateInstaller.Status.Verifying -> stringResource(R.string.update_verifying)
        is UpdateInstaller.Status.Ready -> stringResource(R.string.update_ready)
        is UpdateInstaller.Status.HashMismatch -> stringResource(R.string.update_hash_mismatch)
        is UpdateInstaller.Status.Failed -> status.reason
        else -> null
    } ?: return
    val error = status is UpdateInstaller.Status.HashMismatch || status is UpdateInstaller.Status.Failed
    Text(
        text,
        style = ShadTheme.typography.muted,
        color = if (error) c.destructive else c.mutedForeground,
    )
}

/** Renders release-note Markdown with the app's typography. */
@Composable
fun ReleaseNotesBody(markdown: String) {
    val c = ShadTheme.colors
    val blocks = MarkdownLite.parse(markdown)
    if (blocks.isEmpty()) {
        Text(stringResource(R.string.whats_new_empty), style = ShadTheme.typography.muted, color = c.mutedForeground)
        return
    }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = c.foreground, textDecoration = TextDecoration.Underline),
    )
    blocks.forEach { block ->
        val text = buildAnnotatedString {
            block.spans.forEach { span ->
                when (span) {
                    is MarkdownLite.Span.Text -> append(span.text)
                    is MarkdownLite.Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
                    is MarkdownLite.Span.Code -> withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = c.muted)
                    ) { append(span.text) }
                    is MarkdownLite.Span.Link -> withLink(LinkAnnotation.Url(span.url, linkStyles)) { append(span.text) }
                }
            }
        }
        when (block.type) {
            MarkdownLite.BlockType.HEADING -> Text(
                text,
                style = ShadTheme.typography.small,
                modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
            )
            MarkdownLite.BlockType.BULLET -> Row(Modifier.padding(start = 4.dp)) {
                Text("•", style = ShadTheme.typography.p, color = c.mutedForeground)
                Spacer(Modifier.width(8.dp))
                Text(text, style = ShadTheme.typography.p, color = c.cardForeground)
            }
            MarkdownLite.BlockType.QUOTE -> Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.muted, ShadRadius.mdShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text, style = ShadTheme.typography.muted, color = c.mutedForeground)
            }
            MarkdownLite.BlockType.PARAGRAPH -> Text(text, style = ShadTheme.typography.p, color = c.cardForeground)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseHistorySheet(vm: MainViewModel) {
    val c = ShadTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.showReleaseHistory = false },
        sheetState = sheetState,
        containerColor = c.card,
        contentColor = c.cardForeground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.release_history_title), style = ShadTheme.typography.h4)
            when {
                vm.historyLoading -> Text(stringResource(R.string.about_checking), style = ShadTheme.typography.muted, color = c.mutedForeground)
                vm.historyError != null -> Text(
                    stringResource(R.string.release_history_error, vm.historyError ?: ""),
                    style = ShadTheme.typography.muted, color = c.mutedForeground,
                )
                vm.releaseHistory.isEmpty() -> Text(stringResource(R.string.release_history_empty), style = ShadTheme.typography.muted, color = c.mutedForeground)
                else -> LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(vm.releaseHistory, key = { it.tag }) { release ->
                        ShadListRow(
                            title = release.name,
                            subtitle = listOf(release.publishedDate, MarkdownLite.plainText(release.notes).lineSequence().firstOrNull { it.isNotBlank() } ?: "")
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            onClick = { vm.openReleaseNotes(release, ReleaseNotesMode.HISTORY) },
                            trailing = {
                                when {
                                    release.isInstalledVersion -> ShadBadge(stringResource(R.string.badge_installed), variant = BadgeVariant.Secondary)
                                    release.isNewerThanInstalled -> ShadBadge(stringResource(R.string.badge_new), variant = BadgeVariant.Success)
                                    release.prerelease -> ShadBadge(stringResource(R.string.badge_prerelease), variant = BadgeVariant.Outline)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun SheetHandle() {
    val c = ShadTheme.colors
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .size(width = 40.dp, height = 4.dp)
            .background(c.muted, ShadRadius.fullShape),
    )
}
