/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.biometrics.ui;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.biometrics.AuthBiometricFingerprintIconController;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.biometrics.AuthDialog;
import com.android.systemui.biometrics.UdfpsDialogMeasureAdapter;

import kotlin.Pair;

/**
 * Contains the Biometric views (title, subtitle, icon, buttons, etc.).
 *
 * TODO(b/251476085): get the udfps junk out of here, at a minimum. Likely can be replaced with a
 * normal LinearLayout.
 */
public class BiometricPromptLayout extends LinearLayout {

    private static final String TAG = "BiometricPromptLayout";

    @Nullable
    private AuthController.ScaleFactorProvider mScaleFactorProvider;
    @Nullable
    private UdfpsDialogMeasureAdapter mUdfpsAdapter;

    private final boolean mUseCustomBpSize;
    private final int mCustomBpWidth;
    private final int mCustomBpHeight;

    public BiometricPromptLayout(Context context) {
        this(context, null);
    }

    public BiometricPromptLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        mUseCustomBpSize = getResources().getBoolean(R.bool.use_custom_bp_size);
        mCustomBpWidth = getResources().getDimensionPixelSize(R.dimen.biometric_dialog_width);
        mCustomBpHeight = getResources().getDimensionPixelSize(R.dimen.biometric_dialog_height);
    }

    @Deprecated
    public void setUdfpsAdapter(@NonNull UdfpsDialogMeasureAdapter adapter,
            @NonNull AuthController.ScaleFactorProvider scaleProvider) {
        mUdfpsAdapter = adapter;
        mScaleFactorProvider = scaleProvider != null ? scaleProvider : () -> 1.0f;
    }

    @Deprecated
    public boolean isUdfps() {
        return mUdfpsAdapter != null;
    }

    @Deprecated
    public void updateFingerprintAffordanceSize(
            @NonNull AuthBiometricFingerprintIconController iconController) {
        if (mUdfpsAdapter != null) {
            final int sensorDiameter = mUdfpsAdapter.getSensorDiameter(
                    mScaleFactorProvider.provide());
            iconController.setIconLayoutParamSize(new Pair(sensorDiameter, sensorDiameter));
        }
    }

    @NonNull
    private AuthDialog.LayoutParams onMeasureInternal(int width, int height) {
        int totalHeight = 0;
        final int numChildren = getChildCount();
        for (int i = 0; i < numChildren; i++) {
            final View child = getChildAt(i);

            if (child.getId() == R.id.space_above_icon
                    || child.getId() == R.id.space_below_icon
                    || child.getId() == R.id.button_bar) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(child.getLayoutParams().height,
                                MeasureSpec.EXACTLY));
            } else if (child.getId() == R.id.biometric_icon_frame) {
                final View iconView = findViewById(R.id.biometric_icon);
                child.measure(
                        MeasureSpec.makeMeasureSpec(iconView.getLayoutParams().width,
                                MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(iconView.getLayoutParams().height,
                                MeasureSpec.EXACTLY));
            } else if (child.getId() == R.id.biometric_icon) {
                child.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            } else {
                child.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            }

            if (child.getVisibility() != View.GONE) {
                totalHeight += child.getMeasuredHeight();
            }
        }

        final AuthDialog.LayoutParams params = new AuthDialog.LayoutParams(width, totalHeight);
        if (mUdfpsAdapter != null) {
            return mUdfpsAdapter.onMeasureInternal(width, height, params,
                    (mScaleFactorProvider != null) ? mScaleFactorProvider.provide() : 1.0f);
        } else {
            return params;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (mUseCustomBpSize) {
            width = mCustomBpWidth;
            height = mCustomBpHeight;
        } else {
            width = Math.min(width, height);
        }

        final AuthDialog.LayoutParams params = onMeasureInternal(width, height);
        setMeasuredDimension(params.mMediumWidth, params.mMediumHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mUdfpsAdapter != null) {
            // Move the UDFPS icon and indicator text if necessary. This probably only needs to
            // happen for devices where the UDFPS sensor is too low.
            // TODO(b/201510778): Update this logic to support cases where the sensor or text
            // overlap the button bar area.
            final float bottomSpacerHeight = mUdfpsAdapter.getBottomSpacerHeight();
            Log.w(TAG, "bottomSpacerHeight: " + bottomSpacerHeight);
            if (bottomSpacerHeight < 0) {
                final FrameLayout iconFrame = findViewById(R.id.biometric_icon_frame);
                iconFrame.setTranslationY(-bottomSpacerHeight);
                final TextView indicator = findViewById(R.id.indicator);
                indicator.setTranslationY(-bottomSpacerHeight);
            }
        }
    }
}
