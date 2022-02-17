/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_DISMISSED;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_HIDDEN;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED;
import static android.app.TaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;

import android.annotation.Nullable;
import android.app.TaskInfo;
import android.app.TaskInfo.CameraCompatControlState;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController.CompatUICallback;
import com.android.wm.shell.compatui.letterboxedu.LetterboxEduWindowManager;

/**
 * Window manager for the Size Compat restart button and Camera Compat control.
 */
class CompatUIWindowManager extends CompatUIWindowManagerAbstract {

    /**
     * The Compat UI should be below the Letterbox Education.
     */
    private static final int Z_ORDER = LetterboxEduWindowManager.Z_ORDER - 1;

    private final CompatUICallback mCallback;

    // Remember the last reported states in case visibility changes due to keyguard or IME updates.
    @VisibleForTesting
    boolean mHasSizeCompat;

    @VisibleForTesting
    @CameraCompatControlState
    int mCameraCompatControlState = CAMERA_COMPAT_CONTROL_HIDDEN;

    @VisibleForTesting
    boolean mShouldShowSizeCompatHint;
    @VisibleForTesting
    boolean mShouldShowCameraCompatHint;

    @Nullable
    @VisibleForTesting
    CompatUILayout mLayout;

    CompatUIWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, CompatUICallback callback,
            ShellTaskOrganizer.TaskListener taskListener, DisplayLayout displayLayout,
            boolean hasShownSizeCompatHint, boolean hasShownCameraCompatHint) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        mCallback = callback;
        mHasSizeCompat = taskInfo.topActivityInSizeCompat;
        mCameraCompatControlState = taskInfo.cameraCompatControlState;
        mShouldShowSizeCompatHint = !hasShownSizeCompatHint;
        mShouldShowCameraCompatHint = !hasShownCameraCompatHint;
    }

    @Override
    protected int getZOrder() {
        return Z_ORDER;
    }

    @Override
    protected @Nullable View getLayout() {
        return mLayout;
    }

    @Override
    protected void removeLayout() {
        mLayout = null;
    }

    @Override
    protected boolean eligibleToShowLayout() {
        return mHasSizeCompat || shouldShowCameraControl();
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        mLayout.inject(this);

        updateVisibilityOfViews();

        if (mHasSizeCompat) {
            mCallback.onSizeCompatRestartButtonAppeared(mTaskId);
        }

        return mLayout;
    }

    @VisibleForTesting
    CompatUILayout inflateLayout() {
        return (CompatUILayout) LayoutInflater.from(mContext).inflate(R.layout.compat_ui_layout,
                null);
    }

    @Override
    public boolean updateCompatInfo(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener,
            boolean canShow) {
        final boolean prevHasSizeCompat = mHasSizeCompat;
        final int prevCameraCompatControlState = mCameraCompatControlState;
        mHasSizeCompat = taskInfo.topActivityInSizeCompat;
        mCameraCompatControlState = taskInfo.cameraCompatControlState;

        if (!super.updateCompatInfo(taskInfo, taskListener, canShow)) {
            return false;
        }

        if (prevHasSizeCompat != mHasSizeCompat
                || prevCameraCompatControlState != mCameraCompatControlState) {
            updateVisibilityOfViews();
        }

        return true;
    }

    /** Called when the restart button is clicked. */
    void onRestartButtonClicked() {
        mCallback.onSizeCompatRestartButtonClicked(mTaskId);
    }

    /** Called when the camera treatment button is clicked. */
    void onCameraTreatmentButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(getTag(), "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        // When a camera control is shown, only two states are allowed: "treament applied" and
        // "treatment suggested". Clicks on the conrol's treatment button toggle between these
        // two states.
        mCameraCompatControlState =
                mCameraCompatControlState == CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED
                        ? CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED
                        : CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mCallback.onCameraControlStateUpdated(mTaskId, mCameraCompatControlState);
        mLayout.updateCameraTreatmentButton(mCameraCompatControlState);
    }

    /** Called when the camera dismiss button is clicked. */
    void onCameraDismissButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(getTag(), "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        mCameraCompatControlState = CAMERA_COMPAT_CONTROL_DISMISSED;
        mCallback.onCameraControlStateUpdated(mTaskId, CAMERA_COMPAT_CONTROL_DISMISSED);
        mLayout.setCameraControlVisibility(/* show= */ false);
    }

    /** Called when the restart button is long clicked. */
    void onRestartButtonLongClicked() {
        if (mLayout == null) {
            return;
        }
        mLayout.setSizeCompatHintVisibility(/* show= */ true);
    }

    /** Called when either dismiss or treatment camera buttons is long clicked. */
    void onCameraButtonLongClicked() {
        if (mLayout == null) {
            return;
        }
        mLayout.setCameraCompatHintVisibility(/* show= */ true);
    }

    @Override
    @VisibleForTesting
    public void updateSurfacePosition() {
        if (mLayout == null) {
            return;
        }
        // Position of the button in the container coordinate.
        final Rect taskBounds = getTaskBounds();
        final Rect taskStableBounds = getTaskStableBounds();
        final int positionX = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? taskStableBounds.left - taskBounds.left
                : taskStableBounds.right - taskBounds.left - mLayout.getMeasuredWidth();
        final int positionY = taskStableBounds.bottom - taskBounds.top
                - mLayout.getMeasuredHeight();

        updateSurfacePosition(positionX, positionY);
    }

    private void updateVisibilityOfViews() {
        if (mLayout == null) {
            return;
        }
        // Size Compat mode restart button.
        mLayout.setRestartButtonVisibility(mHasSizeCompat);
        if (mHasSizeCompat && mShouldShowSizeCompatHint) {
            mLayout.setSizeCompatHintVisibility(/* show= */ true);
            // Only show by default for the first time.
            mShouldShowSizeCompatHint = false;
        }

        // Camera control for stretched issues.
        mLayout.setCameraControlVisibility(shouldShowCameraControl());
        if (shouldShowCameraControl() && mShouldShowCameraCompatHint) {
            mLayout.setCameraCompatHintVisibility(/* show= */ true);
            // Only show by default for the first time.
            mShouldShowCameraCompatHint = false;
        }
        if (shouldShowCameraControl()) {
            mLayout.updateCameraTreatmentButton(mCameraCompatControlState);
        }
    }

    private boolean shouldShowCameraControl() {
        return mCameraCompatControlState != CAMERA_COMPAT_CONTROL_HIDDEN
                && mCameraCompatControlState != CAMERA_COMPAT_CONTROL_DISMISSED;
    }
}
