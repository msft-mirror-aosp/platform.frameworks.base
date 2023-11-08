/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.compose.animation.scene

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Configures the swipeable behavior of a [SceneTransitionLayout] depending on the current state.
 */
internal fun Modifier.swipeToScene(gestureHandler: SceneGestureHandler): Modifier {
    /** Whether swipe should be enabled in the given [orientation]. */
    fun Scene.shouldEnableSwipes(orientation: Orientation): Boolean =
        userActions.keys.any { it is Swipe && it.direction.orientation == orientation }

    val currentScene = gestureHandler.currentScene
    val orientation = gestureHandler.orientation
    val canSwipe = currentScene.shouldEnableSwipes(orientation)
    val canOppositeSwipe =
        currentScene.shouldEnableSwipes(
            when (orientation) {
                Orientation.Vertical -> Orientation.Horizontal
                Orientation.Horizontal -> Orientation.Vertical
            }
        )

    return nestedScroll(connection = gestureHandler.nestedScroll.connection)
        .multiPointerDraggable(
            orientation = orientation,
            enabled = gestureHandler.isDrivingTransition || canSwipe,
            // Immediately start the drag if this our [transition] is currently animating to a scene
            // (i.e. the user released their input pointer after swiping in this orientation) and
            // the user can't swipe in the other direction.
            startDragImmediately =
                gestureHandler.isDrivingTransition &&
                    gestureHandler.isAnimatingOffset &&
                    !canOppositeSwipe,
            onDragStarted = gestureHandler.draggable::onDragStarted,
            onDragDelta = gestureHandler.draggable::onDelta,
            onDragStopped = gestureHandler.draggable::onDragStopped,
        )
}

@Composable
internal fun rememberSceneGestureHandler(
    layoutImpl: SceneTransitionLayoutImpl,
    orientation: Orientation,
): SceneGestureHandler {
    val coroutineScope = rememberCoroutineScope()

    val gestureHandler =
        remember(layoutImpl, orientation, coroutineScope) {
            SceneGestureHandler(layoutImpl, orientation, coroutineScope)
        }

    // Make sure we reset the scroll connection when this handler is removed from composition
    val connection = gestureHandler.nestedScroll.connection
    DisposableEffect(connection) { onDispose { connection.reset() } }

    return gestureHandler
}
