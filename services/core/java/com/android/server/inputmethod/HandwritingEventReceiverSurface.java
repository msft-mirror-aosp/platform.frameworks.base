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

package com.android.server.inputmethod;

import static android.os.InputConstants.DEFAULT_DISPATCHING_TIMEOUT_MILLIS;

import android.annotation.NonNull;
import android.os.Process;
import android.view.InputApplicationHandle;
import android.view.InputChannel;
import android.view.InputWindowHandle;
import android.view.SurfaceControl;
import android.view.WindowManager;


final class HandwritingEventReceiverSurface {

    public static final String TAG = HandwritingEventReceiverSurface.class.getSimpleName();
    static final boolean DEBUG = HandwritingModeController.DEBUG;

    // Place the layer below the highest layer to place it under gesture monitors. If the surface
    // is above gesture monitors, then edge-back and swipe-up gestures won't work when this surface
    // is intercepting.
    // TODO(b/217538817): Specify the ordering in WM by usage.
    private static final int HANDWRITING_SURFACE_LAYER = Integer.MAX_VALUE - 1;

    private final InputApplicationHandle mApplicationHandle;
    private final InputWindowHandle mWindowHandle;
    private final InputChannel mClientChannel;
    private final SurfaceControl mInputSurface;
    private boolean mIsIntercepting;

    HandwritingEventReceiverSurface(String name, int displayId, @NonNull SurfaceControl sc,
            @NonNull InputChannel inputChannel) {
        mApplicationHandle = new InputApplicationHandle(null, name,
                DEFAULT_DISPATCHING_TIMEOUT_MILLIS);

        mClientChannel = inputChannel;
        mInputSurface = sc;

        mWindowHandle = new InputWindowHandle(mApplicationHandle, displayId);
        mWindowHandle.name = name;
        mWindowHandle.token = mClientChannel.getToken();
        mWindowHandle.layoutParamsType = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
        mWindowHandle.layoutParamsFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        mWindowHandle.dispatchingTimeoutMillis = DEFAULT_DISPATCHING_TIMEOUT_MILLIS;
        mWindowHandle.visible = true;
        mWindowHandle.focusable = false;
        mWindowHandle.hasWallpaper = false;
        mWindowHandle.paused = false;
        mWindowHandle.ownerPid = Process.myPid();
        mWindowHandle.ownerUid = Process.myUid();
        mWindowHandle.inputFeatures = WindowManager.LayoutParams.INPUT_FEATURE_SPY
                | WindowManager.LayoutParams.INPUT_FEATURE_INTERCEPTS_STYLUS;
        mWindowHandle.scaleFactor = 1.0f;
        mWindowHandle.trustedOverlay = true;
        mWindowHandle.replaceTouchableRegionWithCrop(null /* use this surface's bounds */);

        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setInputWindowInfo(mInputSurface, mWindowHandle);
        t.setLayer(mInputSurface, HANDWRITING_SURFACE_LAYER);
        t.setPosition(mInputSurface, 0, 0);
        t.setCrop(mInputSurface, null /* crop to parent surface */);
        t.show(mInputSurface);
        t.apply();

        mIsIntercepting = false;
    }

    void startIntercepting() {
        // TODO(b/210978621): Update the spy window's PID and UID to be associated with the IME so
        //  that ANRs are correctly attributed to the IME.
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        mWindowHandle.inputFeatures &= ~WindowManager.LayoutParams.INPUT_FEATURE_SPY;
        t.setInputWindowInfo(mInputSurface, mWindowHandle);
        t.apply();
        mIsIntercepting = true;
    }

    boolean isIntercepting() {
        return mIsIntercepting;
    }

    void remove() {
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.remove(mInputSurface);
        t.apply();
    }

    InputChannel getInputChannel() {
        return mClientChannel;
    }

    SurfaceControl getSurface() {
        return mInputSurface;
    }
}
