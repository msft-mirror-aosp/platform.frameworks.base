/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.credentialmanager.getflow

import android.text.TextUtils
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.credentialmanager.CredentialSelectorViewModel
import com.android.credentialmanager.R
import com.android.credentialmanager.common.BaseEntry
import com.android.credentialmanager.common.CredentialType
import com.android.credentialmanager.common.ProviderActivityState
import com.android.credentialmanager.common.ui.ActionButton
import com.android.credentialmanager.common.ui.ConfirmButton
import com.android.credentialmanager.common.ui.Entry
import com.android.credentialmanager.common.ui.ModalBottomSheet
import com.android.credentialmanager.common.ui.TextOnSurface
import com.android.credentialmanager.common.ui.TextSecondary
import com.android.credentialmanager.common.ui.TextOnSurfaceVariant
import com.android.credentialmanager.common.ui.ContainerCard
import com.android.credentialmanager.common.ui.TransparentBackgroundEntry
import com.android.credentialmanager.ui.theme.EntryShape
import com.android.credentialmanager.ui.theme.LocalAndroidColorScheme

@Composable
fun GetCredentialScreen(
    viewModel: CredentialSelectorViewModel,
    getCredentialUiState: GetCredentialUiState,
    providerActivityLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
) {
    if (getCredentialUiState.currentScreenState == GetScreenState.REMOTE_ONLY) {
        RemoteCredentialSnackBarScreen(
            onClick = viewModel::getFlowOnMoreOptionOnSnackBarSelected,
            onCancel = viewModel::onUserCancel,
        )
    } else if (getCredentialUiState.currentScreenState
        == GetScreenState.UNLOCKED_AUTH_ENTRIES_ONLY) {
        EmptyAuthEntrySnackBarScreen(
            authenticationEntryList =
            getCredentialUiState.providerDisplayInfo.authenticationEntryList,
            onCancel = viewModel::silentlyFinishActivity,
            onLastLockedAuthEntryNotFound = viewModel::onLastLockedAuthEntryNotFoundError,
        )
    } else {
        ModalBottomSheet(
            sheetContent = {
                // Hide the sheet content as opposed to the whole bottom sheet to maintain the scrim
                // background color even when the content should be hidden while waiting for
                // results from the provider app.
                when (viewModel.uiState.providerActivityState) {
                    ProviderActivityState.NOT_APPLICABLE -> {
                        if (getCredentialUiState.currentScreenState
                            == GetScreenState.PRIMARY_SELECTION) {
                            PrimarySelectionCard(
                                requestDisplayInfo = getCredentialUiState.requestDisplayInfo,
                                providerDisplayInfo = getCredentialUiState.providerDisplayInfo,
                                providerInfoList = getCredentialUiState.providerInfoList,
                                activeEntry = getCredentialUiState.activeEntry,
                                onEntrySelected = viewModel::getFlowOnEntrySelected,
                                onConfirm = viewModel::getFlowOnConfirmEntrySelected,
                                onMoreOptionSelected = viewModel::getFlowOnMoreOptionSelected,
                            )
                        } else {
                            AllSignInOptionCard(
                                providerInfoList = getCredentialUiState.providerInfoList,
                                providerDisplayInfo = getCredentialUiState.providerDisplayInfo,
                                onEntrySelected = viewModel::getFlowOnEntrySelected,
                                onBackButtonClicked =
                                viewModel::getFlowOnBackToPrimarySelectionScreen,
                                onCancel = viewModel::onUserCancel,
                                isNoAccount = getCredentialUiState.isNoAccount,
                            )
                        }
                    }
                    ProviderActivityState.READY_TO_LAUNCH -> {
                        // Launch only once per providerActivityState change so that the provider
                        // UI will not be accidentally launched twice.
                        LaunchedEffect(viewModel.uiState.providerActivityState) {
                            viewModel.launchProviderUi(providerActivityLauncher)
                        }
                    }
                    ProviderActivityState.PENDING -> {
                        // Hide our content when the provider activity is active.
                    }
                }
            },
            onDismiss = viewModel::onUserCancel,
        )
    }
}

/** Draws the primary credential selection page. */
@Composable
fun PrimarySelectionCard(
    requestDisplayInfo: RequestDisplayInfo,
    providerDisplayInfo: ProviderDisplayInfo,
    providerInfoList: List<ProviderInfo>,
    activeEntry: BaseEntry?,
    onEntrySelected: (BaseEntry) -> Unit,
    onConfirm: () -> Unit,
    onMoreOptionSelected: () -> Unit,
) {
    val sortedUserNameToCredentialEntryList =
        providerDisplayInfo.sortedUserNameToCredentialEntryList
    val authenticationEntryList = providerDisplayInfo.authenticationEntryList
    ContainerCard() {
        Column() {
            TextOnSurface(
                modifier = Modifier.padding(all = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                text = stringResource(
                    if (sortedUserNameToCredentialEntryList
                            .size == 1 && authenticationEntryList.isEmpty()
                    ) {
                        if (sortedUserNameToCredentialEntryList.first()
                                .sortedCredentialEntryList.first().credentialType
                            == CredentialType.PASSKEY
                        ) R.string.get_dialog_title_use_passkey_for
                        else R.string.get_dialog_title_use_sign_in_for
                    } else if (
                        sortedUserNameToCredentialEntryList
                            .isEmpty() && authenticationEntryList.size == 1
                    ) {
                        R.string.get_dialog_title_use_sign_in_for
                    } else R.string.get_dialog_title_choose_sign_in_for,
                    requestDisplayInfo.appName
                ),
            )

            ContainerCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .align(alignment = Alignment.CenterHorizontally)
            ) {
                val usernameForCredentialSize = sortedUserNameToCredentialEntryList
                    .size
                val authenticationEntrySize = authenticationEntryList.size
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Show max 4 entries in this primary page
                    if (usernameForCredentialSize + authenticationEntrySize <= 4) {
                        items(sortedUserNameToCredentialEntryList) {
                            CredentialEntryRow(
                                credentialEntryInfo = it.sortedCredentialEntryList.first(),
                                onEntrySelected = onEntrySelected,
                            )
                        }
                        items(authenticationEntryList) {
                            AuthenticationEntryRow(
                                authenticationEntryInfo = it,
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    } else if (usernameForCredentialSize < 4) {
                        items(sortedUserNameToCredentialEntryList) {
                            CredentialEntryRow(
                                credentialEntryInfo = it.sortedCredentialEntryList.first(),
                                onEntrySelected = onEntrySelected,
                            )
                        }
                        items(authenticationEntryList.take(4 - usernameForCredentialSize)) {
                            AuthenticationEntryRow(
                                authenticationEntryInfo = it,
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    } else {
                        items(sortedUserNameToCredentialEntryList.take(4)) {
                            CredentialEntryRow(
                                credentialEntryInfo = it.sortedCredentialEntryList.first(),
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    }
                }
            }
            Divider(
                thickness = 24.dp,
                color = Color.Transparent
            )
            var totalEntriesCount = sortedUserNameToCredentialEntryList
                .flatMap { it.sortedCredentialEntryList }.size + authenticationEntryList
                .size + providerInfoList.flatMap { it.actionEntryList }.size
            if (providerDisplayInfo.remoteEntry != null) totalEntriesCount += 1
            // Row horizontalArrangement differs on only one actionButton(should place on most
            // left)/only one confirmButton(should place on most right)/two buttons exist the same
            // time(should be one on the left, one on the right)
            Row(
                horizontalArrangement =
                if (totalEntriesCount <= 1 && activeEntry != null) Arrangement.End
                else if (totalEntriesCount > 1 && activeEntry == null) Arrangement.Start
                else Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                if (totalEntriesCount > 1) {
                    ActionButton(
                        stringResource(R.string.get_dialog_use_saved_passkey_for),
                        onMoreOptionSelected
                    )
                }
                // Only one sign-in options exist
                if (activeEntry != null) {
                    ConfirmButton(
                        stringResource(R.string.string_continue),
                        onClick = onConfirm
                    )
                }
            }
            Divider(
                thickness = 18.dp,
                color = Color.Transparent,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

/** Draws the secondary credential selection page, where all sign-in options are listed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSignInOptionCard(
    providerInfoList: List<ProviderInfo>,
    providerDisplayInfo: ProviderDisplayInfo,
    onEntrySelected: (BaseEntry) -> Unit,
    onBackButtonClicked: () -> Unit,
    onCancel: () -> Unit,
    isNoAccount: Boolean,
) {
    val sortedUserNameToCredentialEntryList =
        providerDisplayInfo.sortedUserNameToCredentialEntryList
    val authenticationEntryList = providerDisplayInfo.authenticationEntryList
    ContainerCard() {
        Column() {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                title = {
                    TextOnSurface(
                        text = stringResource(R.string.get_dialog_title_sign_in_options),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isNoAccount) onCancel else onBackButtonClicked) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.accessibility_back_arrow_button)
                        )
                    }
                },
                modifier = Modifier.padding(top = 12.dp)
            )

            ContainerCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .align(alignment = Alignment.CenterHorizontally),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // For username
                    items(sortedUserNameToCredentialEntryList) { item ->
                        PerUserNameCredentials(
                            perUserNameCredentialEntryList = item,
                            onEntrySelected = onEntrySelected,
                        )
                    }
                    // Locked password manager
                    if (authenticationEntryList.isNotEmpty()) {
                        item {
                            LockedCredentials(
                                authenticationEntryList = authenticationEntryList,
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    }
                    item {
                        Divider(
                            thickness = 8.dp,
                            color = Color.Transparent,
                        )
                    }
                    // From another device
                    val remoteEntry = providerDisplayInfo.remoteEntry
                    if (remoteEntry != null) {
                        item {
                            RemoteEntryCard(
                                remoteEntry = remoteEntry,
                                onEntrySelected = onEntrySelected,
                            )
                        }
                    }
                    item {
                        Divider(
                            thickness = 1.dp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    // Manage sign-ins (action chips)
                    item {
                        ActionChips(
                            providerInfoList = providerInfoList,
                            onEntrySelected = onEntrySelected
                        )
                    }
                }
            }
        }
    }
}

// TODO: create separate rows for primary and secondary pages.
// TODO: reuse rows and columns across types.

@Composable
fun ActionChips(
    providerInfoList: List<ProviderInfo>,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    val actionChips = providerInfoList.flatMap { it.actionEntryList }
    if (actionChips.isEmpty()) {
        return
    }

    TextSecondary(
        text = stringResource(R.string.get_dialog_heading_manage_sign_ins),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    // TODO: tweak padding.
    ContainerCard(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actionChips.forEach {
                ActionEntryRow(it, onEntrySelected)
            }
        }
    }
}

@Composable
fun RemoteEntryCard(
    remoteEntry: RemoteEntryInfo,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    TextSecondary(
        text = stringResource(R.string.get_dialog_heading_from_another_device),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    ContainerCard(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Entry(
                onClick = { onEntrySelected(remoteEntry) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_other_devices),
                        contentDescription = null,
                        tint = LocalAndroidColorScheme.current.colorAccentPrimaryVariant,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                },
                label = {
                    TextOnSurfaceVariant(
                        text = stringResource(
                            R.string.get_dialog_option_headline_use_a_different_device),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 18.dp)
                            .align(alignment = Alignment.CenterHorizontally)
                    )
                }
            )
        }
    }
}

@Composable
fun LockedCredentials(
    authenticationEntryList: List<AuthenticationEntryInfo>,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    TextSecondary(
        text = stringResource(R.string.get_dialog_heading_locked_password_managers),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    ContainerCard(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            authenticationEntryList.forEach {
                AuthenticationEntryRow(it, onEntrySelected)
            }
        }
    }
}

@Composable
fun PerUserNameCredentials(
    perUserNameCredentialEntryList: PerUserNameCredentialEntryList,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    TextSecondary(
        text = stringResource(
            R.string.get_dialog_heading_for_username, perUserNameCredentialEntryList.userName
        ),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    ContainerCard(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            perUserNameCredentialEntryList.sortedCredentialEntryList.forEach {
                CredentialEntryRow(it, onEntrySelected)
            }
        }
    }
}

@Composable
fun CredentialEntryRow(
    credentialEntryInfo: CredentialEntryInfo,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    Entry(
        onClick = { onEntrySelected(credentialEntryInfo) },
        icon = {
            if (credentialEntryInfo.icon != null) {
                Image(
                    modifier = Modifier.padding(start = 10.dp).size(32.dp),
                    bitmap = credentialEntryInfo.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                )
            } else {
                Icon(
                    modifier = Modifier.padding(start = 10.dp).size(32.dp),
                    painter = painterResource(R.drawable.ic_other_sign_in),
                    contentDescription = null,
                    tint = LocalAndroidColorScheme.current.colorAccentPrimaryVariant
                )
            }
        },
        label = {
            Column() {
                // TODO: fix the text values.
                TextOnSurfaceVariant(
                    text = credentialEntryInfo.userName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp, start = 5.dp)
                )
                TextSecondary(
                    text = if (
                        credentialEntryInfo.credentialType == CredentialType.PASSWORD) {
                        "••••••••••••"
                    } else {
                        if (TextUtils.isEmpty(credentialEntryInfo.displayName))
                            credentialEntryInfo.credentialTypeDisplayName
                        else
                            credentialEntryInfo.credentialTypeDisplayName +
                                stringResource(
                                    R.string.get_dialog_sign_in_type_username_separator
                                ) +
                                credentialEntryInfo.displayName
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp, start = 5.dp)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationEntryRow(
    authenticationEntryInfo: AuthenticationEntryInfo,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    Entry(
        onClick = { onEntrySelected(authenticationEntryInfo) },
        icon = {
            Image(
                modifier = Modifier.padding(start = 10.dp).size(32.dp),
                bitmap = authenticationEntryInfo.icon.toBitmap().asImageBitmap(),
                contentDescription = null
            )
        },
        label = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
            ) {
                Column() {
                    TextOnSurfaceVariant(
                        text = authenticationEntryInfo.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    TextSecondary(
                        text = stringResource(
                            if (authenticationEntryInfo.isUnlockedAndEmpty)
                                R.string.locked_credential_entry_label_subtext_no_sign_in
                            else R.string.locked_credential_entry_label_subtext_tap_to_unlock
                    ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                if (!authenticationEntryInfo.isUnlockedAndEmpty) {
                    Icon(
                        Icons.Outlined.Lock,
                        null,
                        Modifier.align(alignment = Alignment.CenterVertically).padding(end = 10.dp),
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionEntryRow(
    actionEntryInfo: ActionEntryInfo,
    onEntrySelected: (BaseEntry) -> Unit,
) {
    TransparentBackgroundEntry(
        icon = {
            Image(
                modifier = Modifier.padding(start = 10.dp).size(24.dp),
                bitmap = actionEntryInfo.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
            )
        },
        label = {
            Column() {
                TextOnSurfaceVariant(
                    text = actionEntryInfo.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (actionEntryInfo.subTitle != null) {
                    TextSecondary(
                        text = actionEntryInfo.subTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        onClick = { onEntrySelected(actionEntryInfo) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteCredentialSnackBarScreen(
    onClick: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    // TODO: Change the height, width and position according to the design
    Snackbar(
        modifier = Modifier.padding(horizontal = 40.dp).padding(top = 700.dp),
        shape = EntryShape.FullMediumRoundedCorner,
        containerColor = LocalAndroidColorScheme.current.colorBackground,
        contentColor = LocalAndroidColorScheme.current.colorAccentPrimaryVariant,
        action = {
            TextButton(
                onClick = { onClick(true) },
            ) {
                Text(text = stringResource(R.string.snackbar_action))
            }
        },
        dismissAction = {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(
                        R.string.accessibility_close_button
                    ),
                    tint = LocalAndroidColorScheme.current.colorAccentTertiary
                )
            }
        },
    ) {
        Text(text = stringResource(R.string.get_dialog_use_saved_passkey_for))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyAuthEntrySnackBarScreen(
    authenticationEntryList: List<AuthenticationEntryInfo>,
    onCancel: () -> Unit,
    onLastLockedAuthEntryNotFound: () -> Unit,
) {
    val lastLocked = authenticationEntryList.firstOrNull({it.isLastUnlocked})
    if (lastLocked == null) {
        onLastLockedAuthEntryNotFound()
        return
    }

    // TODO: Change the height, width and position according to the design
    Snackbar(
        modifier = Modifier.padding(horizontal = 40.dp).padding(top = 700.dp),
        shape = EntryShape.FullMediumRoundedCorner,
        containerColor = LocalAndroidColorScheme.current.colorBackground,
        contentColor = LocalAndroidColorScheme.current.colorAccentPrimaryVariant,
        dismissAction = {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.accessibility_close_button),
                    tint = LocalAndroidColorScheme.current.colorAccentTertiary
                )
            }
        },
    ) {
        Text(text = stringResource(R.string.no_sign_in_info_in, lastLocked.title))
    }
}