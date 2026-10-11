/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountselect.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.toMatrixUser
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

@Inject
class AccountSelectPresenter(
    private val sessionStore: SessionStore,
) : Presenter<AccountSelectState> {
    @Composable
    override fun present(): AccountSelectState {
        val state by produceState(AccountSelectState(accounts = persistentListOf())) {
            sessionStore.sessionsFlow().collect { sessions ->
                // Keep rows in account order when last-usage ordering changes, while still observing
                // profile edits, account additions and removals.
                value = AccountSelectState(
                    accounts = sessions.sortedBy { it.position }.map { it.toMatrixUser() }.toImmutableList(),
                    currentAccount = sessions.firstOrNull()?.let { SessionId(it.userId) },
                    homeservers = sessions.associate { SessionId(it.userId) to it.homeserverUrl }.toImmutableMap(),
                )
            }
        }

        return state
    }
}
