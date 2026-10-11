/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.accountselect.impl

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

data class AccountSelectState(
    val accounts: ImmutableList<MatrixUser>,
    val currentAccount: SessionId? = null,
    val homeservers: ImmutableMap<SessionId, String> = persistentMapOf(),
)
