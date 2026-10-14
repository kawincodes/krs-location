package com.krstechsolutions.krslocation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.krstechsolutions.krslocation.ui.theme.ShadRadius
import com.krstechsolutions.krslocation.ui.theme.ShadTheme

/*
 * A small set of shadcn/ui style primitives for Compose:
 * Button, Card, Input, Label, Switch, Badge, Alert, Separator and Dialog.
 * They are plain Compose code styled with the tokens in ui/theme/Theme.kt.
 */

// ---- Button ---------------------------------------------------------------------------------

enum class ButtonVariant { Default, Secondary, Outline, Ghost, Destructive }
enum class ButtonSize { Default, Sm, Lg, Icon, IconSm }

@Composable
fun ShadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = ShadTheme.colors
    val background: Color
    val foreground: Color
    var borderColor: Color? = null
    when (variant) {
        ButtonVariant.Default -> { background = c.primary; foreground = c.primaryForeground }
        ButtonVariant.Secondary -> { background = c.secondary; foreground = c.secondaryForeground }
        ButtonVariant.Outline -> { background = c.background; foreground = c.foreground; borderColor = c.input }
        ButtonVariant.Ghost -> { background = Color.Transparent; foreground = c.foreground }
        ButtonVariant.Destructive -> { background = c.destructive; foreground = c.destructiveForeground }
    }

    val height = when (size) {
        ButtonSize.Default, ButtonSize.Icon -> 40.dp
        ButtonSize.Sm, ButtonSize.IconSm -> 36.dp
        ButtonSize.Lg -> 44.dp
    }
    val horizontalPadding = when (size) {
        ButtonSize.Default -> 16.dp
        ButtonSize.Sm -> 12.dp
        ButtonSize.Lg -> 32.dp
        ButtonSize.Icon, ButtonSize.IconSm -> 0.dp
    }
    val isIcon = size == ButtonSize.Icon || size == ButtonSize.IconSm
    val shape = ShadRadius.mdShape
    val alpha = if (enabled) 1f else 0.5f
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .then(if (isIcon) Modifier.size(height) else Modifier.height(height))
            .clip(shape)
            .background(background.copy(alpha = background.alpha * alpha))
            .then(borderColor?.let { Modifier.border(1.dp, it.copy(alpha = alpha), shape) } ?: Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = foreground),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides foreground.copy(alpha = alpha),
            LocalTextStyle provides ShadTheme.typography.small,
        ) {
            content()
        }
    }
}

/** 16dp icon followed by the standard 8dp gap, matching shadcn's `[&_svg]:size-4 gap-2`. */
@Composable
fun RowScope.ButtonIcon(icon: ImageVector, contentDescription: String? = null, trailingGap: Boolean = true) {
    Icon(icon, contentDescription, Modifier.size(16.dp))
    if (trailingGap) Spacer(Modifier.width(8.dp))
}

// ---- Card -----------------------------------------------------------------------------------

@Composable
fun ShadCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ShadTheme.colors
    Column(
        modifier
            .shadow(if (c.isDark) 0.dp else 1.dp, ShadRadius.xlShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(ShadRadius.xlShape)
            .background(c.card)
            .border(1.dp, c.border, ShadRadius.xlShape)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.cardForeground) {
            content()
        }
    }
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = ShadTheme.typography.h4)
}

@Composable
fun CardDescription(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = ShadTheme.colors.mutedForeground, style = ShadTheme.typography.muted)
}

// ---- Label / Input --------------------------------------------------------------------------

@Composable
fun ShadLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = ShadTheme.typography.small)
}

@Composable
fun ShadInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    textStyle: TextStyle = ShadTheme.typography.p,
) {
    val c = ShadTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(if (focused) c.ring else c.input, label = "inputBorder")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, label = "inputBorderWidth")

    Column(modifier) {
        if (label != null) {
            ShadLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(ShadRadius.mdShape)
                .background(c.background)
                .border(borderWidth, borderColor, ShadRadius.mdShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(16.dp), tint = c.mutedForeground)
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = singleLine,
                textStyle = textStyle.copy(color = if (enabled) c.foreground else c.mutedForeground),
                cursorBrush = SolidColor(c.foreground),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                style = textStyle,
                                color = c.mutedForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

// ---- Switch ---------------------------------------------------------------------------------

@Composable
fun ShadSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ShadTheme.colors
    val track by animateColorAsState(if (checked) c.primary else c.input, label = "switchTrack")
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switchThumb")
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(ShadRadius.fullShape)
            .background(track.copy(alpha = if (enabled) 1f else 0.5f))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .background(c.background, CircleShape),
        )
    }
}

// ---- Badge ----------------------------------------------------------------------------------

enum class BadgeVariant { Default, Secondary, Outline, Destructive, Success }

@Composable
fun ShadBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = ShadTheme.colors
    val (bg, fg, border) = when (variant) {
        BadgeVariant.Default -> Triple(c.primary, c.primaryForeground, Color.Transparent)
        BadgeVariant.Secondary -> Triple(c.secondary, c.secondaryForeground, Color.Transparent)
        BadgeVariant.Outline -> Triple(Color.Transparent, c.foreground, c.border)
        BadgeVariant.Destructive -> Triple(c.destructive, c.destructiveForeground, Color.Transparent)
        BadgeVariant.Success -> Triple(c.success, c.successForeground, Color.Transparent)
    }
    Row(
        modifier
            .clip(ShadRadius.fullShape)
            .background(bg)
            .border(1.dp, border, ShadRadius.fullShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            leading?.invoke()
            Text(text, style = ShadTheme.typography.xs.copy(fontWeight = FontWeight.SemiBold), color = fg)
        }
    }
}

// ---- Alert ----------------------------------------------------------------------------------

enum class AlertVariant { Default, Destructive }

@Composable
fun ShadAlert(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    variant: AlertVariant = AlertVariant.Default,
    action: (@Composable () -> Unit)? = null,
) {
    val c = ShadTheme.colors
    val tint = if (variant == AlertVariant.Destructive) {
        if (c.isDark) Color(0xFFF87171) else c.destructive
    } else {
        c.foreground
    }
    val borderColor = if (variant == AlertVariant.Destructive) tint.copy(alpha = 0.5f) else c.border

    Row(
        modifier
            .fillMaxWidth()
            .clip(ShadRadius.lgShape)
            .background(c.background)
            .border(1.dp, borderColor, ShadRadius.lgShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp).offset(y = 2.dp), tint = tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ShadTheme.typography.small, color = tint)
            if (description != null) {
                Text(description, style = ShadTheme.typography.muted, color = if (variant == AlertVariant.Destructive) tint else c.mutedForeground)
            }
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

// ---- Separator ------------------------------------------------------------------------------

@Composable
fun ShadSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ShadTheme.colors.border),
    )
}

// ---- Dialog ---------------------------------------------------------------------------------

@Composable
fun ShadDialog(
    onDismissRequest: () -> Unit,
    title: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String? = null,
    icon: ImageVector? = null,
) {
    val c = ShadTheme.colors
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, ShadRadius.lgShape)
                .clip(ShadRadius.lgShape)
                .background(c.popover)
                .border(1.dp, c.border, ShadRadius.lgShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = c.popoverForeground)
                Text(title, style = ShadTheme.typography.h4, color = c.popoverForeground)
            }
            Text(description, style = ShadTheme.typography.muted, color = c.mutedForeground)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (cancelText != null) {
                    ShadButton(onClick = onDismissRequest, variant = ButtonVariant.Outline) { Text(cancelText) }
                }
                ShadButton(onClick = onConfirm) { Text(confirmText) }
            }
        }
    }
}
