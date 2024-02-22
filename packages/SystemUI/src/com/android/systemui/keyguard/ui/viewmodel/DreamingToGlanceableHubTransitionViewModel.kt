/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import com.android.app.animation.Interpolators.EMPHASIZED
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.KeyguardTransitionAnimationFlow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class DreamingToGlanceableHubTransitionViewModel
@Inject
constructor(animationFlow: KeyguardTransitionAnimationFlow) {

    private val transitionAnimation =
        animationFlow.setup(
            duration = TO_GLANCEABLE_HUB_DURATION,
            from = KeyguardState.DREAMING,
            to = KeyguardState.GLANCEABLE_HUB,
        )

    fun dreamOverlayTranslationX(translatePx: Int): Flow<Float> {
        return transitionAnimation.sharedFlow(
            duration = TO_GLANCEABLE_HUB_DURATION,
            onStep = { it * -translatePx },
            interpolator = EMPHASIZED,
            name = "DREAMING->GLANCEABLE_HUB: overlayTranslationX",
        )
    }

    val dreamOverlayAlpha: Flow<Float> =
        transitionAnimation.sharedFlow(
            duration = 167.milliseconds,
            onStep = { 1f - it },
            name = "DREAMING->GLANCEABLE_HUB: dreamOverlayAlpha",
        )

    private companion object {
        val TO_GLANCEABLE_HUB_DURATION = 1.seconds
    }
}
