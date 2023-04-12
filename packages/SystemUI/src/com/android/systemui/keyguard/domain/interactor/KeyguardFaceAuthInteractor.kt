/*
 *   Copyright (C) 2023 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.android.systemui.keyguard.domain.interactor

import com.android.systemui.keyguard.shared.model.AuthenticationStatus
import com.android.systemui.keyguard.shared.model.DetectionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Interactor that exposes API to get the face authentication status and handle any events that can
 * cause face authentication to run.
 */
interface KeyguardFaceAuthInteractor {

    /** Current authentication status */
    val authenticationStatus: Flow<AuthenticationStatus>

    /** Current detection status */
    val detectionStatus: Flow<DetectionStatus>

    /** Can face auth be run right now */
    fun canFaceAuthRun(): Boolean

    /** Whether face auth is currently running or not. */
    fun isRunning(): Boolean

    /** Whether face auth is in lock out state. */
    fun isLockedOut(): Boolean

    /**
     * Register listener for use from code that cannot use [authenticationStatus] or
     * [detectionStatus]
     */
    fun registerListener(listener: FaceAuthenticationListener)

    /** Unregister previously registered listener */
    fun unregisterListener(listener: FaceAuthenticationListener)

    /** Whether the face auth interactor is enabled or not. */
    fun isEnabled(): Boolean

    fun onUdfpsSensorTouched()
    fun onAssistantTriggeredOnLockScreen()
    fun onDeviceLifted()
    fun onQsExpansionStared()
    fun onNotificationPanelClicked()
    fun onSwipeUpOnBouncer()
}

/**
 * Listener that can be registered with the [KeyguardFaceAuthInteractor] to receive updates about
 * face authentication & detection updates.
 *
 * This is present to make it easier for use the new face auth API for code that cannot use
 * [KeyguardFaceAuthInteractor.authenticationStatus] or [KeyguardFaceAuthInteractor.detectionStatus]
 * flows.
 */
interface FaceAuthenticationListener {
    /** Receive face authentication status updates */
    fun onAuthenticationStatusChanged(status: AuthenticationStatus)

    /** Receive status updates whenever face detection runs */
    fun onDetectionStatusChanged(status: DetectionStatus)
}
