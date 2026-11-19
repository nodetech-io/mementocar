// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.nodetech.mementocar.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Immutable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import io.nodetech.mementocar.R
import io.nodetech.mementocar.data.ActiveDays
import io.nodetech.mementocar.data.BackupSummary
import io.nodetech.mementocar.data.ChecklistItemEntity
import io.nodetech.mementocar.data.DataLimits
import io.nodetech.mementocar.data.ReminderRecurrence
import io.nodetech.mementocar.data.ReminderStatus
import io.nodetech.mementocar.data.ReminderTrigger
import io.nodetech.mementocar.data.VehicleEntity
import io.nodetech.mementocar.domain.SystemSetupAction
import io.nodetech.mementocar.ui.theme.MementoDanger
import io.nodetech.mementocar.ui.theme.MementoDangerContainer
import io.nodetech.mementocar.ui.theme.MementoWarning
import io.nodetech.mementocar.ui.theme.MementoWarningContainer
import io.nodetech.mementocar.ui.theme.MementoMutedText
import io.nodetech.mementocar.ui.theme.MementoOnPrimaryContainer
import io.nodetech.mementocar.ui.theme.MementoOutline
import io.nodetech.mementocar.ui.theme.MementoPrimaryContainer
import io.nodetech.mementocar.ui.theme.MementoSuccess
import io.nodetech.mementocar.ui.theme.MementoSuccessContainer
import io.nodetech.mementocar.ui.theme.MementoSurface
import io.nodetech.mementocar.ui.theme.MementoSurfaceVariant
import io.nodetech.mementocar.ui.theme.MementoText
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlinx.coroutines.delay

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MementoMutedText,
    )
}

@Composable
internal fun AppHeader(
    vehicleName: String?,
    diagnostics: DiagnosticsUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(modifier = Modifier.size(32.dp), emphasized = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                vehicleName ?: "Memento Car",
                style = MaterialTheme.typography.bodyMedium,
                color = MementoText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (diagnostics.setupAction == SystemSetupAction.Ready &&
            diagnostics.level != DiagnosticsLevelUiState.Attention
        ) {
            StatusBadge(text = diagnostics.level.shortLabel(), level = diagnostics.level)
        }
    }
}

@Composable
internal fun BrandMark(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (emphasized) MaterialTheme.colorScheme.primary else MementoPrimaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.memento_car_icon_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.memento_car_icon_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun StatusBadge(
    text: String,
    level: DiagnosticsLevelUiState,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = level.statusBackground(),
        border = BorderStroke(1.dp, level.statusColor().copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(level.statusColor(), CircleShape),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = level.statusColor(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun IconTile(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    level: DiagnosticsLevelUiState? = null,
    selected: Boolean = false,
) {
    val background = when {
        level != null -> level.statusBackground()
        selected -> MementoPrimaryContainer
        else -> MementoSurfaceVariant
    }
    val tint = when {
        level != null -> level.statusColor()
        selected -> MaterialTheme.colorScheme.primary
        else -> MementoMutedText
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .background(background, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun AppBottomNavigation(
    selectedPage: AppPage,
    onSelected: (AppPage) -> Unit,
    reminderCount: Int,
    alertCount: Int,
) {
    NavigationBar(
        containerColor = MementoSurface,
        tonalElevation = 0.dp,
    ) {
        bottomNavigationPages.forEach { page ->
            val selected = selectedPage == page ||
                (page == AppPage.Reminders && selectedPage == AppPage.NewReminder)
            val badgeCount = when (page) {
                AppPage.Reminders -> reminderCount
                AppPage.Alerts -> alertCount
                else -> 0
            }
            val badgeDescription = if (badgeCount > 0) {
                pluralStringResource(
                    if (page == AppPage.Alerts) R.plurals.open_alerts_count else R.plurals.reminders_count,
                    badgeCount, badgeCount,
                )
            } else null
            val accessibleLabel = stringResource(page.labelRes)
            val compactLabel = stringResource(when (page) {
                AppPage.Reminders, AppPage.NewReminder -> R.string.navigation_list
                AppPage.Alerts -> R.string.navigation_alerts
                AppPage.Verification -> R.string.navigation_status
                AppPage.Settings -> R.string.navigation_settings
            })
            NavigationBarItem(
                modifier = Modifier.semantics {
                    contentDescription = accessibleLabel
                    badgeDescription?.let { stateDescription = it }
                },
                selected = selected,
                onClick = { onSelected(page) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text(badgeCount.toString())
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = page.icon(),
                            contentDescription = null,
                        )
                    }
                },
                label = { Text(compactLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.secondary,
                    unselectedTextColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }
}

@Composable
internal fun MessageBanner(
    message: String,
    kind: MessageKind,
    modifier: Modifier = Modifier,
) {
    val container = when (kind) {
        MessageKind.Success, MessageKind.Info -> MementoPrimaryContainer
        MessageKind.Warning -> MementoWarningContainer
        MessageKind.Error -> MementoDangerContainer
    }
    val accent = when (kind) {
        MessageKind.Success -> MaterialTheme.colorScheme.primary
        MessageKind.Info -> MaterialTheme.colorScheme.secondary
        MessageKind.Warning -> MementoWarning
        MessageKind.Error -> MementoDanger
    }
    val icon = when (kind) {
        MessageKind.Success -> Icons.Outlined.Check
        MessageKind.Info -> Icons.Outlined.Info
        MessageKind.Warning -> Icons.Outlined.WarningAmber
        MessageKind.Error -> Icons.Outlined.ErrorOutline
    }
    val iconDescription = when (kind) {
        MessageKind.Success -> null
        MessageKind.Info -> stringResource(R.string.message_kind_info)
        MessageKind.Warning -> stringResource(R.string.message_kind_warning)
        MessageKind.Error -> stringResource(R.string.message_kind_error)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = when (kind) {
                    MessageKind.Success, MessageKind.Info -> MementoOnPrimaryContainer
                    MessageKind.Warning -> MementoText
                    MessageKind.Error -> MaterialTheme.colorScheme.onErrorContainer
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [MessageBanner] that closes on tap or horizontal swipe, and exposes the same action to TalkBack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DismissibleMessageBanner(
    message: String,
    kind: MessageKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissLabel = stringResource(R.string.dismiss_message)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDismiss()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {},
    ) {
        MessageBanner(
            message = message,
            kind = kind,
            modifier = Modifier.clickable(onClickLabel = dismissLabel, onClick = onDismiss),
        )
    }
}

@Composable
internal fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
internal fun ListCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    ListCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(imageVector = icon, selected = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MementoText,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
internal fun VehiclesSection(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: Long?,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var vehicleIdPendingDeletion by rememberSaveable { mutableStateOf<Long?>(null) }
    val vehiclePendingDeletion = vehicles.firstOrNull { it.id == vehicleIdPendingDeletion }
    if (vehiclePendingDeletion != null) {
        AlertDialog(
            onDismissRequest = { vehicleIdPendingDeletion = null },
            title = { Text(stringResource(R.string.delete_car_title)) },
            text = {
                Text(stringResource(R.string.delete_car_detail, vehiclePendingDeletion.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val vehicleId = vehiclePendingDeletion.id
                        vehicleIdPendingDeletion = null
                        onRemove(vehicleId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MementoDanger),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { vehicleIdPendingDeletion = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(
            title = stringResource(R.string.settings_title),
            detail = stringResource(R.string.settings_detail),
        )
        SectionTitle(pluralStringResource(R.plurals.configured_cars_count, vehicles.size, vehicles.size))
        if (vehicles.isNotEmpty()) {
            ListCard {
                vehicles.forEachIndexed { index, vehicle ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MementoOutline,
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_notification_car),
                                selected = vehicle.id == selectedVehicleId,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vehicle.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (vehicle.id == selectedVehicleId) {
                                        stringResource(R.string.active_car)
                                    } else {
                                        stringResource(R.string.available)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (vehicle.id == selectedVehicleId) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MementoSuccessContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.active),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MementoSuccess,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            IconButton(onClick = { vehicleIdPendingDeletion = vehicle.id }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete_car_named, vehicle.name),
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        if (vehicle.id != selectedVehicleId) {
                            SecondaryButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(R.string.activate),
                                onClick = { onSelect(vehicle.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NotificationDelaySection(
    selectedMinutes: Int,
    onSelected: (Int) -> Unit,
) {
    Section(stringResource(R.string.notification_section)) {
        ListCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.notification_delay),
                    style = MaterialTheme.typography.titleMedium,
                    color = MementoText,
                )
                Text(
                    selectedMinutes.notificationDelayDetail(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.notification_delay_why),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Column(
                    modifier = Modifier.selectableGroup()
                        .background(MementoSurfaceVariant, RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    notificationDelayOptions.chunked(3).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowOptions.forEach { minutes ->
                                TriggerButton(
                                    modifier = Modifier.weight(1f),
                                    text = minutes.notificationDelayShortLabel(),
                                    selected = selectedMinutes == minutes,
                                    onClick = { onSelected(minutes) },
                                )
                            }
                            repeat(3 - rowOptions.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DataManagementSection(
    inProgress: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Section(stringResource(R.string.data_section)) {
        Text(
            text = stringResource(R.string.backup_data_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        ListCard {
            SettingsActionRow(
                text = stringResource(R.string.export_backup),
                icon = Icons.Outlined.FileUpload,
                onClick = onExport,
                enabled = !inProgress,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MementoOutline,
            )
            SettingsActionRow(
                text = stringResource(R.string.import_backup),
                icon = Icons.Outlined.FileDownload,
                onClick = onImport,
                enabled = !inProgress,
            )
        }
        Text(
            text = stringResource(R.string.backup_private_data_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun SettingsActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.primary else MementoMutedText
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

@Composable
internal fun BackupImportDialog(
    summary: BackupSummary,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formattedDate = remember(summary.exportedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(summary.exportedAtEpochMillis))
    }
    val vehicles = pluralStringResource(
        R.plurals.backup_vehicle_count,
        summary.vehicleCount,
        summary.vehicleCount,
    )
    val reminders = pluralStringResource(
        R.plurals.backup_reminder_count,
        summary.reminderCount,
        summary.reminderCount,
    )
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(stringResource(R.string.import_backup_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.backup_created_at, formattedDate))
                Text(stringResource(R.string.backup_contents_summary, vehicles, reminders))
                Text(stringResource(R.string.import_backup_replace_warning))
                Text(
                    text = stringResource(R.string.import_backup_runtime_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !inProgress) {
                Text(stringResource(R.string.import_and_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun ChecklistHeader(
    itemCount: Int,
    setupAction: SystemSetupAction,
    onSetupAction: (SystemSetupAction) -> Unit,
    onExample: (ReminderExample) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(
            title = stringResource(R.string.reminders_title),
            detail = pluralStringResource(R.plurals.reminders_count, itemCount, itemCount),
        )
        val showWelcome = itemCount == 0 && setupAction == SystemSetupAction.ConfigureBluetoothVehicle
        if (showWelcome) {
            WelcomeCard(onChooseCar = { onSetupAction(setupAction) })
        } else if (setupAction != SystemSetupAction.Ready) {
            SetupGuideCard(action = setupAction, onAction = { onSetupAction(setupAction) })
        }
        if (itemCount == 0 && setupAction == SystemSetupAction.CreateFirstReminder) {
            ReminderExamplesSection(onExample = onExample)
        }
        // The setup card already says what to do next when there is no car or no reminder yet.
        val setupCardCoversEmptyList = setupAction == SystemSetupAction.CreateFirstReminder ||
            setupAction == SystemSetupAction.ConfigureBluetoothVehicle
        if (itemCount == 0 && !setupCardCoversEmptyList) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                title = stringResource(R.string.no_reminders_title),
                detail = stringResource(R.string.no_reminders_detail),
                actions = {},
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderActions(
    isListeningForVoice: Boolean,
    onCreate: () -> Unit,
    onCreateByVoice: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        FlowRow(
            maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(
                modifier = Modifier.weight(1f).fillMaxRowHeight(),
                text = if (isListeningForVoice) stringResource(R.string.listening) else stringResource(R.string.dictate),
                icon = Icons.Outlined.Mic,
                onClick = onCreateByVoice,
                enabled = !isListeningForVoice,
            )
            PrimaryButton(
                modifier = Modifier.weight(1f).fillMaxRowHeight(),
                text = stringResource(R.string.new_item),
                icon = Icons.Outlined.Add,
                onClick = onCreate,
            )
        }
    }
}

@Composable
internal fun SetupGuideCard(
    action: SystemSetupAction,
    onAction: () -> Unit,
) {
    ListCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(imageVector = action.icon(), selected = true)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.title(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MementoText,
                    )
                    Text(
                        text = action.detail(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = action.buttonLabel(),
                icon = action.icon(),
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun NoCarNotice(onConfigureVehicle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MementoWarningContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MementoWarning.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MementoWarning,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.new_reminder_no_car_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MementoWarning,
                    )
                    Text(
                        stringResource(R.string.new_reminder_no_car_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MementoText,
                    )
                }
            }
            SecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.setup_configure_bluetooth_vehicle_button),
                onClick = onConfigureVehicle,
            )
        }
    }
}

@Composable
internal fun NewReminderSection(
    editingItem: ChecklistItemEntity? = null,
    externalExitRequested: Boolean = false,
    onExternalExitCancelled: () -> Unit = {},
    currentTimeMillis: () -> Long = System::currentTimeMillis,
    voiceDraft: VoiceReminderDraftUiState? = null,
    onVoiceDraftConsumed: () -> Unit,
    onVoiceInput: () -> Unit,
    isListeningForVoice: Boolean,
    hasVehicles: Boolean = true,
    onConfigureVehicle: () -> Unit = {},
    onBack: () -> Unit,
    onAdd: (String, ReminderTrigger, ReminderRecurrence, Int, ReminderTarget, Boolean, Long?) -> Unit,
    onUpdate: (Long, String, ReminderTrigger, ReminderRecurrence, Int, ReminderTarget, Boolean, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var label by rememberSaveable(editingItem?.id) { mutableStateOf(editingItem?.label.orEmpty()) }
    var trigger by rememberSaveable(editingItem?.id) {
        mutableStateOf(editingItem?.trigger ?: ReminderTrigger.Disconnect)
    }
    var recurrence by rememberSaveable(editingItem?.id) {
        mutableStateOf(editingItem?.recurrence ?: ReminderRecurrence.Once)
    }
    var activeDaysMask by rememberSaveable(editingItem?.id) {
        mutableIntStateOf(editingItem?.activeDaysMask ?: ActiveDays.All)
    }
    var critical by rememberSaveable(editingItem?.id) {
        mutableStateOf(editingItem?.critical == true)
    }
    var eligibleFromMillis by rememberSaveable(editingItem?.id) {
        mutableStateOf(editingItem?.eligibleFromMillis)
    }
    var futureTripValidationError by rememberSaveable(editingItem?.id) { mutableStateOf(false) }
    var target by rememberSaveable(editingItem?.id) { mutableStateOf(ReminderTarget.SelectedVehicle) }
    var showDeleteConfirmation by rememberSaveable(editingItem?.id) { mutableStateOf(false) }
    val isEditing = editingItem != null
    var showDiscardConfirmation by rememberSaveable(editingItem?.id) { mutableStateOf(false) }
    var showAdvancedOptions by rememberSaveable(editingItem?.id) {
        mutableStateOf(editingItem?.critical == true || editingItem?.eligibleFromMillis != null)
    }
    val hasChanges = label != editingItem?.label.orEmpty() ||
        trigger != (editingItem?.trigger ?: ReminderTrigger.Disconnect) ||
        recurrence != (editingItem?.recurrence ?: ReminderRecurrence.Once) ||
        activeDaysMask != (editingItem?.activeDaysMask ?: ActiveDays.All) ||
        critical != (editingItem?.critical == true) ||
        eligibleFromMillis != editingItem?.eligibleFromMillis || target != ReminderTarget.SelectedVehicle
    // Leaving to set up a car goes through the same discard check as going back.
    var exitToConfigureVehicle by rememberSaveable(editingItem?.id) { mutableStateOf(false) }
    val finishExit = {
        if (exitToConfigureVehicle) {
            exitToConfigureVehicle = false
            onConfigureVehicle()
        } else {
            onBack()
        }
    }
    val requestExit = { if (hasChanges) showDiscardConfirmation = true else finishExit() }
    BackHandler { requestExit() }
    LaunchedEffect(externalExitRequested) {
        if (externalExitRequested) requestExit()
    }
    val keepEditing = {
        showDiscardConfirmation = false
        exitToConfigureVehicle = false
        if (externalExitRequested) onExternalExitCancelled()
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = keepEditing,
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    finishExit()
                }) {
                    Text(stringResource(R.string.discard_changes), color = MementoDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = keepEditing) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }

    LaunchedEffect(voiceDraft?.draftId) {
        voiceDraft?.let { draft ->
            label = draft.label
            trigger = draft.trigger
            recurrence = draft.recurrence
            activeDaysMask = draft.activeDaysMask
            critical = draft.critical
            eligibleFromMillis = null
            futureTripValidationError = false
            target = ReminderTarget.SelectedVehicle
            onVoiceDraftConsumed()
        }
    }

    if (showDeleteConfirmation && editingItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_reminder_title)) },
            text = { Text(stringResource(R.string.delete_reminder_detail)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(editingItem.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MementoDanger),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            PageHeader(
                title = if (isEditing) stringResource(R.string.edit_reminder_title) else stringResource(R.string.new_reminder_title),
                detail = if (isEditing) {
                    stringResource(R.string.edit_reminder_detail)
                } else {
                    stringResource(R.string.new_reminder_detail)
                },
                modifier = Modifier.weight(1f),
                compact = true,
            )
            IconButton(
                enabled = !isListeningForVoice,
                onClick = onVoiceInput,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.dictate_reminder),
                    tint = if (isListeningForVoice) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                )
            }
            if (isEditing) {
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_reminder_title),
                        tint = MementoDanger,
                    )
                }
            }
        }
        if (!hasVehicles && !isEditing) {
            NoCarNotice(onConfigureVehicle = {
                exitToConfigureVehicle = true
                requestExit()
            })
        }
        ListCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = label,
                    onValueChange = { label = it.take(DataLimits.ReminderLabelMaxChars) },
                    label = { Text(stringResource(R.string.reminder_field_label)) },
                    placeholder = { Text(stringResource(R.string.reminder_field_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormOptionLabel(stringResource(R.string.car_field_label))
                    ReminderTargetSelector(
                        selected = target,
                        onSelected = { target = it },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormOptionLabel(stringResource(R.string.condition_field_label))
                    TriggerSelector(
                        selected = trigger,
                        onSelected = { trigger = it },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormOptionLabel(stringResource(R.string.frequency_field_label))
                    RecurrenceSelector(
                        selected = recurrence,
                        onSelected = {
                            recurrence = it
                            if (it != ReminderRecurrence.Once) {
                                eligibleFromMillis = null
                                futureTripValidationError = false
                            }
                        },
                    )
                }
                if (recurrence == ReminderRecurrence.EveryTrigger) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormOptionLabel(stringResource(R.string.days_field_label))
                        ActiveDaysSelector(
                            selectedMask = activeDaysMask,
                            onSelected = { activeDaysMask = it },
                        )
                    }
                }
                TextButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                    Text(stringResource(if (showAdvancedOptions) R.string.hide_advanced_options else R.string.show_advanced_options))
                }
                if (showAdvancedOptions) {
                    if (recurrence == ReminderRecurrence.Once) {
                        FutureTripScheduleEditor(
                            eligibleFromMillis = eligibleFromMillis,
                            validationError = futureTripValidationError,
                            onSelected = {
                                eligibleFromMillis = it
                                futureTripValidationError = false
                            },
                            onClear = {
                                eligibleFromMillis = null
                                futureTripValidationError = false
                            },
                            onInvalid = { futureTripValidationError = true },
                        )
                    }
                    CriticalReminderToggle(
                        selected = critical,
                        onSelected = { critical = it },
                    )
                }
            }
        }
        FormActionRow(
            onCancel = requestExit,
            onSave = {
                val scheduledAtMillis = eligibleFromMillis
                val keepsExistingPastSchedule = scheduledAtMillis != null &&
                    scheduledAtMillis == editingItem?.eligibleFromMillis
                val invalidNewSchedule = scheduledAtMillis != null &&
                    scheduledAtMillis <= currentTimeMillis() &&
                    !keepsExistingPastSchedule
                if (invalidNewSchedule) {
                    showAdvancedOptions = true
                    futureTripValidationError = true
                } else {
                    val savedDaysMask = if (recurrence == ReminderRecurrence.EveryTrigger) {
                        activeDaysMask
                    } else {
                        ActiveDays.All
                    }
                    if (editingItem == null) {
                        onAdd(label, trigger, recurrence, savedDaysMask, target, critical, scheduledAtMillis)
                    } else {
                        onUpdate(
                            editingItem.id,
                            label,
                            trigger,
                            recurrence,
                            savedDaysMask,
                            target,
                            critical,
                            scheduledAtMillis,
                        )
                    }
                }
            },
            saveEnabled = label.isNotBlank() &&
                (recurrence != ReminderRecurrence.EveryTrigger || activeDaysMask != 0),
        )
    }
}

@Composable
internal fun FormActionRow(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ButtonRow(spacing = 10.dp) {
            TextButton(
                modifier = Modifier
                    .weight(0.8f)
                    .heightIn(min = 48.dp),
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(stringResource(R.string.cancel))
            }
            PrimaryButton(
                modifier = Modifier.weight(1.2f),
                text = stringResource(R.string.save),
                icon = Icons.Outlined.Check,
                onClick = onSave,
                enabled = saveEnabled,
                height = 48.dp,
                textFontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun FutureTripScheduleEditor(
    eligibleFromMillis: Long?,
    validationError: Boolean,
    onSelected: (Long) -> Unit,
    onClear: () -> Unit,
    onInvalid: () -> Unit,
) {
    val context = LocalContext.current
    val errorRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(validationError) {
        if (validationError) errorRequester.bringIntoView()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormOptionLabel(stringResource(R.string.future_trip_when_label))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) {
                    showFutureTripPicker(
                        context = context,
                        initialMillis = eligibleFromMillis,
                        onSelected = onSelected,
                        onInvalid = onInvalid,
                    )
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = eligibleFromMillis?.uiShortDateTime()
                        ?: stringResource(R.string.future_trip_next_trip),
                    style = MaterialTheme.typography.titleSmall,
                    color = MementoText,
                )
                Text(
                    text = when {
                        eligibleFromMillis != null -> stringResource(R.string.future_trip_change_detail)
                        else -> stringResource(R.string.future_trip_schedule_detail)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            CompactPill(
                text = when {
                    eligibleFromMillis != null -> stringResource(R.string.active)
                    else -> stringResource(R.string.optional)
                },
            )
        }
        if (eligibleFromMillis != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.future_trip_remove_schedule))
            }
        }
        if (validationError) {
            Text(
                text = stringResource(R.string.future_trip_future_date_error),
                modifier = Modifier.bringIntoViewRequester(errorRequester).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.bodySmall,
                color = MementoDanger,
            )
        }
    }
}

private fun showFutureTripPicker(
    context: Context,
    initialMillis: Long?,
    onSelected: (Long) -> Unit,
    onInvalid: () -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zoneId)
    val initial = initialMillis
        ?.takeIf { it > System.currentTimeMillis() }
        ?.let { Instant.ofEpochMilli(it).atZone(zoneId) }
        ?: now.plusHours(1)
    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selectedMillis = LocalDateTime.of(
                        year,
                        month + 1,
                        dayOfMonth,
                        hourOfDay,
                        minute,
                    ).atZone(zoneId).toInstant().toEpochMilli()
                    if (selectedMillis > System.currentTimeMillis()) {
                        onSelected(selectedMillis)
                    } else {
                        onInvalid()
                    }
                },
                initial.hour,
                initial.minute,
                android.text.format.DateFormat.is24HourFormat(context),
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    )
    dateDialog.datePicker.minDate = now.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    dateDialog.show()
}

@Composable
internal fun CriticalReminderToggle(
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .selectable(
                selected = selected,
                onClick = { onSelected(!selected) },
                role = Role.Switch,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.important),
                style = MaterialTheme.typography.titleSmall,
                color = MementoText,
            )
            Text(
                text = stringResource(R.string.critical_enabled_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        CompactPill(
            text = when {
                selected -> stringResource(R.string.active)
                else -> stringResource(R.string.optional)
            },
        )
    }
}

@Composable
internal fun ReminderTargetSelector(
    selected: ReminderTarget,
    onSelected: (ReminderTarget) -> Unit,
) {
    SegmentedOptions {
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.this_car),
            icon = ImageVector.vectorResource(R.drawable.ic_notification_car),
            selected = selected == ReminderTarget.SelectedVehicle,
            onClick = { onSelected(ReminderTarget.SelectedVehicle) },
        )
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.all),
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            selected = selected == ReminderTarget.AllVehicles,
            onClick = { onSelected(ReminderTarget.AllVehicles) },
        )
    }
}

@Composable
internal fun RecurrenceSelector(
    selected: ReminderRecurrence,
    onSelected: (ReminderRecurrence) -> Unit,
) {
    SegmentedOptions {
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = ReminderRecurrence.Once.label(),
            icon = Icons.Outlined.Check,
            selected = selected == ReminderRecurrence.Once,
            onClick = { onSelected(ReminderRecurrence.Once) },
        )
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = ReminderRecurrence.EveryTrigger.label(),
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            selected = selected == ReminderRecurrence.EveryTrigger,
            onClick = { onSelected(ReminderRecurrence.EveryTrigger) },
        )
    }
}

@Composable
internal fun ActiveDaysSelector(
    selectedMask: Int,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedOptions {
            TriggerButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.all),
                selected = selectedMask == ActiveDays.All,
                onClick = { onSelected(ActiveDays.All) },
            )
            TriggerButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.week),
                selected = selectedMask == ActiveDays.Weekdays,
                onClick = { onSelected(ActiveDays.Weekdays) },
            )
            TriggerButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.weekend_short),
                selected = selectedMask == ActiveDays.Weekend,
                onClick = { onSelected(ActiveDays.Weekend) },
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 6.dp
            val minToggle = 48.dp
            // One row of 7 when it fits, otherwise 4 + 3 on a 4-column grid (never a lone toggle).
            val singleRow = maxWidth >= minToggle * 7 + gap * 6
            val columns = if (singleRow) 7 else 4
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                activeDayOptions.chunked(columns).forEach { rowOptions ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowOptions.forEach { option ->
                            DayToggle(
                                modifier = Modifier.weight(1f),
                                label = stringResource(option.labelRes),
                                fullLabel = stringResource(option.fullLabelRes),
                                selected = selectedMask and option.mask != 0,
                                onClick = { onSelected(selectedMask xor option.mask) },
                            )
                        }
                        // Empty slots keep the incomplete last row on the same grid, start aligned.
                        repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DayToggle(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    fullLabel: String = label,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = fullLabel }
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp),
            )
            .toggleable(
                value = selected,
                onValueChange = { onClick() },
                role = Role.Checkbox,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MementoText,
        )
    }
}

@Composable
internal fun PageHeader(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            color = MementoText,
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
internal fun ReminderRow(
    item: ChecklistItemEntity,
    onEdit: () -> Unit,
) {
    val futureTripReady = rememberFutureTripReady(item.eligibleFromMillis)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReminderTriggerIcon(trigger = item.trigger)
            Text(item.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactPill(text = item.trigger.label())
            CompactPill(text = item.recurrence.label())
            if (item.critical) {
                StatusBadge(text = stringResource(R.string.important), level = DiagnosticsLevelUiState.Attention)
            }
        }
        if (item.recurrence == ReminderRecurrence.EveryTrigger) {
            Text(
                text = item.activeDaysMask.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MementoMutedText,
            )
        }
        item.eligibleFromMillis?.let { eligibleFromMillis ->
            Text(
                text = if (!futureTripReady) {
                    stringResource(R.string.future_trip_starts_at, eligibleFromMillis.uiShortDateTime())
                } else {
                    stringResource(R.string.future_trip_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MementoMutedText,
            )
        }
    }
}

@Composable
private fun rememberFutureTripReady(eligibleFromMillis: Long?): Boolean {
    val isReady by produceState(
        initialValue = eligibleFromMillis?.let { it <= System.currentTimeMillis() } ?: false,
        key1 = eligibleFromMillis,
    ) {
        val targetMillis = eligibleFromMillis ?: return@produceState
        while (!value) {
            val remainingMillis = targetMillis - System.currentTimeMillis()
            if (remainingMillis <= 0L) {
                value = true
            } else {
                delay(minOf(remainingMillis, FUTURE_TRIP_CLOCK_REFRESH_MILLIS))
            }
        }
    }
    return isReady
}

private const val FUTURE_TRIP_CLOCK_REFRESH_MILLIS = 60_000L

@Composable
internal fun ReminderTriggerIcon(trigger: ReminderTrigger) {
    IconTile(imageVector = trigger.icon(), selected = true)
}

@Composable
internal fun CompactPill(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MementoSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MementoMutedText,
        )
    }
}

@Composable
internal fun FormOptionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MementoMutedText,
    )
}

/** Shared grouping keeps every exclusive selector visually consistent. */
@Composable
private fun SegmentedOptions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()
            .background(MementoSurfaceVariant, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun TriggerSelector(
    selected: ReminderTrigger,
    onSelected: (ReminderTrigger) -> Unit,
) {
    SegmentedOptions {
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.trigger_disconnect),
            icon = Icons.AutoMirrored.Outlined.Logout,
            selected = selected == ReminderTrigger.Disconnect,
            onClick = { onSelected(ReminderTrigger.Disconnect) },
        )
        TriggerButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.trigger_connect),
            icon = Icons.AutoMirrored.Outlined.Login,
            selected = selected == ReminderTrigger.Connect,
            onClick = { onSelected(ReminderTrigger.Connect) },
        )
    }
}

@Composable
internal fun TriggerButton(
    modifier: Modifier,
    text: String,
    icon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        // Always placed among sibling options in an intrinsic-height row: fill it so every option
        // matches the tallest one when a label wraps.
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MementoSurface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MementoMutedText,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MementoText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun VerificationSection(
    diagnostics: DiagnosticsUiState,
    onSetupAction: (SystemSetupAction) -> Unit,
    onTestConnect: () -> Unit,
    onTestDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(
            title = stringResource(R.string.verification_title),
            detail = stringResource(R.string.verification_detail),
        )
        ListCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(
                        imageVector = diagnostics.level.icon(),
                        level = diagnostics.level,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(diagnostics.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            diagnostics.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (diagnostics.setupAction != SystemSetupAction.Ready) {
                    PrimaryButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = diagnostics.setupAction.buttonLabel(),
                        icon = diagnostics.setupAction.icon(),
                        onClick = { onSetupAction(diagnostics.setupAction) },
                    )
                }
            }
        }
        Section(stringResource(R.string.test_section)) {
            ListCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.send_test_alert),
                        style = MaterialTheme.typography.titleMedium,
                        color = MementoText,
                    )
                    Text(
                        stringResource(R.string.test_alert_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    ButtonRow {
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.trigger_connect),
                            icon = Icons.AutoMirrored.Outlined.Login,
                            onClick = onTestConnect,
                            enabled = diagnostics.canTestAlerts,
                        )
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.trigger_disconnect),
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            onClick = onTestDisconnect,
                            enabled = diagnostics.canTestAlerts,
                        )
                    }
                    diagnostics.testAlertsBlockedReason?.let { reason ->
                        val (reasonText, actionText, onAction) = when (reason) {
                            TestAlertsBlockedReason.NoVehicle -> Triple(
                                stringResource(R.string.test_alerts_blocked_no_vehicle),
                                stringResource(R.string.setup_configure_bluetooth_vehicle_button),
                                { onSetupAction(SystemSetupAction.ConfigureBluetoothVehicle) },
                            )
                            TestAlertsBlockedReason.NotificationsBlocked -> Triple(
                                stringResource(R.string.test_alerts_blocked_notifications),
                                stringResource(R.string.setup_open_notification_settings_button),
                                onOpenNotificationSettings,
                            )
                        }
                        Text(
                            reasonText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        TextButton(onClick = onAction) {
                            Text(actionText)
                        }
                    }
                }
            }
        }
        diagnostics.latestEvent?.let { latest ->
            LatestDiagnosticActivity(latest)
        }
        Section(stringResource(R.string.status_section)) {
            ListCard {
                Column {
                    diagnostics.checks.forEachIndexed { index, check ->
                        DiagnosticCheckRow(check)
                        if (index != diagnostics.checks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 48.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
        Section(stringResource(R.string.settings_section)) {
            ButtonRow {
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.bluetooth_section),
                    icon = Icons.Outlined.Bluetooth,
                    onClick = onOpenBluetoothSettings,
                )
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.notifications),
                    icon = Icons.Outlined.Settings,
                    onClick = onOpenNotificationSettings,
                )
            }
        }
    }
}

@Composable
internal fun LatestDiagnosticActivity(event: LatestDiagnosticEventUiState) {
    Section(stringResource(R.string.latest_activity)) {
        ListCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${event.detail} · ${event.occurredAtMillis.uiShortDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
internal fun DiagnosticCheckRow(check: DiagnosticCheckUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            imageVector = check.level.icon(),
            level = check.level,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(check.label, style = MaterialTheme.typography.titleMedium)
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
internal fun AlertsSection(
    events: List<OpenReminderEventUiState>,
    onComplete: (Long) -> Unit,
    onCompleteItem: (Long, Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader(
            title = stringResource(R.string.alerts_title),
            detail = pluralStringResource(R.plurals.open_alerts_count, events.size, events.size),
        )
        if (events.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Check,
                title = stringResource(R.string.all_done),
                detail = stringResource(R.string.no_open_alerts_detail),
            )
        } else {
            ListCard {
                Column {
                    events.forEachIndexed { index, event ->
                        AlertRow(
                            openEvent = event,
                            onComplete = onComplete,
                            onCompleteItem = onCompleteItem,
                        )
                        if (index != events.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 40.dp),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlertRow(
    openEvent: OpenReminderEventUiState,
    onComplete: (Long) -> Unit,
    onCompleteItem: (Long, Long) -> Unit,
) {
    val event = openEvent.event
    val title = when (openEvent.items.size) {
        0 -> if (event.status == ReminderStatus.Snoozed) {
            stringResource(R.string.snoozed_alert)
        } else {
            stringResource(R.string.pending_alert)
        }
        1 -> openEvent.items.first().label
        else -> pluralStringResource(R.plurals.pending_reminders_count, openEvent.items.size, openEvent.items.size)
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconTile(imageVector = event.trigger.icon(), selected = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${event.trigger.label()} - ${event.disconnectedAtMillis.uiShortDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                CompactPill(text = event.status.label())
                if (openEvent.items.size > 1) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        openEvent.items.forEach { item ->
                            ReminderItemCompletionRow(
                                item = item,
                                onComplete = { onCompleteItem(event.id, item.id) },
                            )
                        }
                    }
                }
            }
        }
        PrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.complete),
            icon = Icons.Outlined.Check,
            onClick = { onComplete(event.id) },
        )
    }
}

@Composable
internal fun ReminderItemCompletionRow(
    item: OpenReminderItemUiState,
    onComplete: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MementoText,
        )
        TextButton(onClick = onComplete) {
            Text(stringResource(R.string.complete))
        }
    }
}

@Composable
internal fun PrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    textFontWeight: FontWeight? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        modifier = modifier.heightIn(min = height.coerceAtLeast(48.dp)),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = textFontWeight ?: FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SecondaryButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MementoPrimaryContainer,
            contentColor = MementoOnPrimaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
        ),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Scope for [ButtonRow]: children share the row width in proportion to [weight] (default 1). */
@LayoutScopeMarker
@Immutable
internal object ButtonRowScope {
    fun Modifier.weight(weight: Float): Modifier {
        require(weight > 0f) { "weight must be positive" }
        return this.then(ButtonRowWeightElement(weight))
    }
}

private data class ButtonRowWeightElement(val weight: Float) : ModifierNodeElement<ButtonRowWeightNode>() {
    override fun create() = ButtonRowWeightNode(weight)
    override fun update(node: ButtonRowWeightNode) {
        node.weight = weight
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "weight"
        value = weight
    }
}

private class ButtonRowWeightNode(var weight: Float) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ButtonRowWeightNode
}

/**
 * Side-by-side action buttons that always share one height: the tallest label (e.g. one wrapped
 * onto two lines) sets the height of every button. When a single word of any label cannot fit in
 * its share of the width (large font scales, long translations), the buttons stack full width
 * instead of breaking the word mid-way.
 */
@Composable
internal fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable ButtonRowScope.() -> Unit,
) {
    Layout(
        content = { ButtonRowScope.content() },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        check(constraints.hasBoundedWidth) { "ButtonRow needs a bounded width" }
        val width = constraints.maxWidth
        if (measurables.isEmpty()) return@Layout layout(width, constraints.minHeight) {}
        val gap = spacing.roundToPx()
        val weights = measurables.map { (it.parentData as? ButtonRowWeightNode)?.weight ?: 1f }
        val available = (width - gap * (measurables.size - 1)).coerceAtLeast(0)
        val slots = IntArray(measurables.size) { (available * weights[it] / weights.sum()).toInt() }
        slots[slots.lastIndex] = available - slots.sum() + slots.last()
        // A child's min intrinsic width is its longest unbreakable word plus padding and icon.
        val fitsSideBySide = measurables.indices.all {
            measurables[it].minIntrinsicWidth(Constraints.Infinity) <= slots[it]
        }
        if (fitsSideBySide) {
            val height = measurables.indices
                .maxOf { measurables[it].maxIntrinsicHeight(slots[it]) }
                .coerceIn(constraints.minHeight, constraints.maxHeight)
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(slots[i], height)) }
            layout(width, height) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gap
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}


internal enum class PermissionRationale { Bluetooth, Notifications }

/** Explains an Android permission in plain words before the system dialog appears. */
@Composable
internal fun PermissionRationaleDialog(
    rationale: PermissionRationale,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (rationale) {
                        PermissionRationale.Bluetooth -> R.string.permission_bluetooth_title
                        PermissionRationale.Notifications -> R.string.permission_notifications_title
                    },
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    when (rationale) {
                        PermissionRationale.Bluetooth -> R.string.permission_bluetooth_detail
                        PermissionRationale.Notifications -> R.string.permission_notifications_detail
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.permission_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Paired Bluetooth devices the user can pick as their car. The MAC address is not shown. */
@Composable
internal fun AddCarSection(
    devices: List<io.nodetech.mementocar.platform.PairedBluetoothDevice>,
    onUse: (io.nodetech.mementocar.platform.PairedBluetoothDevice) -> Unit,
    needsPermission: Boolean,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Section(stringResource(R.string.add_car_section)) {
        Text(
            text = stringResource(R.string.add_car_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (devices.isEmpty()) {
            ListCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            if (needsPermission) R.string.permission_bluetooth_title else R.string.add_car_empty_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (needsPermission) R.string.add_car_permission_detail else R.string.add_car_empty_detail,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (needsPermission) {
                        PrimaryButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.setup_request_permissions_button),
                            onClick = onGrantPermission,
                        )
                    }
                }
            }
        } else {
            devices.forEach { device ->
                ListCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(imageVector = Icons.Outlined.Bluetooth)
                        Text(
                            device.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        PrimaryButton(text = stringResource(R.string.use), onClick = { onUse(device) })
                    }
                }
            }
        }
        ButtonRow {
            SecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.search_paired),
                icon = Icons.Outlined.Search,
                onClick = onRefresh,
            )
            SecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.bluetooth_settings),
                icon = Icons.Outlined.Bluetooth,
                onClick = onOpenBluetoothSettings,
            )
        }
    }
}

/** Collapsed-by-default container for rarely used settings (alert delay, backup). */
@Composable
internal fun AdvancedSettingsSection(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListCard {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.advanced_section), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.advanced_section_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        if (expanded) content()
    }
}


/** A suggested reminder: tapping it opens the editor pre-filled; nothing is saved automatically. */
internal data class ReminderExample(
    @StringRes val labelRes: Int,
    val trigger: ReminderTrigger,
    val recurrence: ReminderRecurrence = ReminderRecurrence.Once,
    val activeDaysMask: Int = ActiveDays.All,
    val critical: Boolean = false,
)

internal val reminderExamples = listOf(
    ReminderExample(R.string.example_trash, ReminderTrigger.Disconnect),
    ReminderExample(R.string.example_groceries, ReminderTrigger.Disconnect),
    ReminderExample(R.string.example_call, ReminderTrigger.Connect),
    ReminderExample(R.string.example_blue_zone, ReminderTrigger.Connect),
    ReminderExample(
        R.string.example_gym_bag,
        ReminderTrigger.Connect,
        ReminderRecurrence.EveryTrigger,
        activeDaysMask = ActiveDays.Monday or ActiveDays.Wednesday or ActiveDays.Friday,
    ),
)

@Composable
internal fun WelcomeCard(onChooseCar: () -> Unit) {
    ListCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.titleLarge,
                color = MementoText,
            )
            Text(
                text = stringResource(R.string.welcome_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.welcome_button),
                icon = ImageVector.vectorResource(R.drawable.ic_notification_car),
                onClick = onChooseCar,
            )
        }
    }
}

/** Closing "About" section of the settings page: language, version, source code, maker and privacy policy. */
@Composable
internal fun AboutSection(
    onOpenLanguageSettings: (() -> Unit)?,
    onOpenSourceCode: () -> Unit,
    onOpenNodetech: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrNull()
    }
    Section(stringResource(R.string.about_section)) {
        ListCard {
            if (onOpenLanguageSettings != null) {
                val locale = LocalConfiguration.current.locales[0]
                AboutRow(
                    title = stringResource(R.string.about_language),
                    detail = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
                    onClick = onOpenLanguageSettings,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MementoOutline)
            }
            if (version != null) {
                AboutRow(title = stringResource(R.string.about_version), detail = version)
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MementoOutline)
            }
            AboutRow(
                title = stringResource(R.string.about_source_code),
                detail = stringResource(R.string.about_source_code_detail),
                onClick = onOpenSourceCode,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MementoOutline)
            AboutRow(
                title = stringResource(R.string.about_created_by),
                detail = "Nodetech",
                onClick = onOpenNodetech,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MementoOutline)
            AboutRow(
                title = stringResource(R.string.privacy_policy),
                detail = stringResource(R.string.about_privacy_detail),
                onClick = onOpenPrivacyPolicy,
            )
        }
    }
}

/** A settings row with a title and an optional muted value under it; tappable when [onClick] is set. */
@Composable
private fun AboutRow(
    title: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MementoText)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MementoMutedText)
        }
    }
}

@Composable
internal fun ReminderExamplesSection(onExample: (ReminderExample) -> Unit) {
    Section(stringResource(R.string.examples_title)) {
        Text(
            text = stringResource(R.string.examples_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        ListCard {
            reminderExamples.forEachIndexed { index, example ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MementoOutline)
                }
                SettingsActionRow(
                    text = stringResource(example.labelRes),
                    icon = if (example.trigger == ReminderTrigger.Disconnect) {
                        Icons.AutoMirrored.Outlined.Logout
                    } else {
                        Icons.AutoMirrored.Outlined.Login
                    },
                    onClick = { onExample(example) },
                    enabled = true,
                )
            }
        }
    }
}
