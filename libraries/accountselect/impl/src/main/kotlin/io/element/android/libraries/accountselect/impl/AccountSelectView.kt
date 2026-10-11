/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountselect.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings

@Suppress("MultipleEmitters") // False positive
@Composable
fun AccountSelectView(
    state: AccountSelectState,
    onSelectAccount: (SessionId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = { onDismiss() })
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(CommonStrings.common_select_account),
                navigationIcon = {
                    BackButton(onClick = { onDismiss() })
                },
            )
        }
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            LazyColumn {
                items(state.accounts, key = { it.userId }) { matrixUser ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectAccount(matrixUser.userId) },
                    ) {
                        MatrixUserRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            matrixUser = matrixUser,
                            trailingContent = {
                                if (matrixUser.userId == state.currentAccount) {
                                    Icon(
                                        imageVector = CompoundIcons.Check(),
                                        contentDescription = stringResource(CommonStrings.screen_account_select_current_account),
                                        tint = ElementTheme.colors.iconPrimary,
                                    )
                                }
                            },
                        )
                        state.homeservers[matrixUser.userId]?.let { homeserver ->
                            Text(
                                text = homeserver,
                                style = ElementTheme.typography.fontBodySmRegular,
                                color = ElementTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 28.dp + AvatarSize.UserListItem.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun AccountSelectViewPreview(@PreviewParameter(AccountSelectStatePreviewParam::class) state: AccountSelectState) = ElementPreview {
    AccountSelectView(
        state = state,
        onSelectAccount = {},
        onDismiss = {},
    )
}
