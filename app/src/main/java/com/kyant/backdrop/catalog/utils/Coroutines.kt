// Copyright (c) Kyant0
// SPDX-License-Identifier: Apache-2.0
// Adapted from AndroidLiquidGlass's catalog via Ward; local changes documented in THIRD_PARTY.md.
package com.kyant.backdrop.catalog.utils

import androidx.compose.runtime.withFrameNanos

/**
 * В библиотеке это expect/actual для нескольких платформ. У нас платформа одна,
 * поэтому здесь сразу реализация: ждать кадр умеет сам Compose, и тащить ради
 * этого kotlinx-coroutines-android незачем.
 */
suspend fun awaitFrame() {
    withFrameNanos { }
}
