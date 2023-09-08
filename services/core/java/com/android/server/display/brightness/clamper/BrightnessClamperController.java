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

package com.android.server.display.brightness.clamper;

import static com.android.server.display.brightness.clamper.BrightnessClamper.Type;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.PowerManager;
import android.provider.DeviceConfig;
import android.provider.DeviceConfigInterface;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.DisplayDeviceConfig.ThermalBrightnessThrottlingData;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.display.feature.DeviceConfigParameterProvider;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Clampers controller, all in DisplayControllerHandler
 */
public class BrightnessClamperController {
    private static final String TAG = "BrightnessClamperController";

    private final DeviceConfigParameterProvider mDeviceConfigParameterProvider;
    private final Handler mHandler;
    private final ClamperChangeListener mClamperChangeListenerExternal;

    private final Executor mExecutor;
    private final List<BrightnessClamper<? super DisplayDeviceData>> mClampers;

    private final List<BrightnessModifier> mModifiers;
    private final DeviceConfig.OnPropertiesChangedListener mOnPropertiesChangedListener;
    private float mBrightnessCap = PowerManager.BRIGHTNESS_MAX;
    @Nullable
    private Type mClamperType = null;
    private boolean mClamperApplied = false;

    public BrightnessClamperController(Handler handler,
            ClamperChangeListener clamperChangeListener, DisplayDeviceData data, Context context) {
        this(new Injector(), handler, clamperChangeListener, data, context);
    }

    @VisibleForTesting
    BrightnessClamperController(Injector injector, Handler handler,
            ClamperChangeListener clamperChangeListener, DisplayDeviceData data, Context context) {
        mDeviceConfigParameterProvider = injector.getDeviceConfigParameterProvider();
        mHandler = handler;
        mClamperChangeListenerExternal = clamperChangeListener;
        mExecutor = new HandlerExecutor(handler);

        Runnable clamperChangeRunnableInternal = this::recalculateBrightnessCap;

        ClamperChangeListener clamperChangeListenerInternal = () -> {
            if (!mHandler.hasCallbacks(clamperChangeRunnableInternal)) {
                mHandler.post(clamperChangeRunnableInternal);
            }
        };

        mClampers = injector.getClampers(handler, clamperChangeListenerInternal, data);
        mModifiers = injector.getModifiers(context);
        mOnPropertiesChangedListener =
                properties -> mClampers.forEach(BrightnessClamper::onDeviceConfigChanged);
        start();
    }

    /**
     * Should be called when display changed. Forwards the call to individual clampers
     */
    public void onDisplayChanged(DisplayDeviceData data) {
        mClampers.forEach(clamper -> clamper.onDisplayChanged(data));
    }

    /**
     * Applies clamping
     * Called in DisplayControllerHandler
     */
    public DisplayBrightnessState clamp(DisplayManagerInternal.DisplayPowerRequest request,
            float brightnessValue, boolean slowChange) {
        float cappedBrightness = Math.min(brightnessValue, mBrightnessCap);

        DisplayBrightnessState.Builder builder = DisplayBrightnessState.builder();
        builder.setIsSlowChange(slowChange);
        builder.setBrightness(cappedBrightness);
        builder.setMaxBrightness(mBrightnessCap);

        if (mClamperType != null) {
            builder.getBrightnessReason().addModifier(BrightnessReason.MODIFIER_THROTTLED);
            if (!mClamperApplied) {
                builder.setIsSlowChange(false);
            }
            mClamperApplied = true;
        } else {
            mClamperApplied = false;
        }

        for (int i = 0; i < mModifiers.size(); i++) {
            mModifiers.get(i).apply(request, builder);
        }

        return builder.build();
    }

    /**
     * See BrightnessThrottler.getBrightnessMaxReason:
     * used in:
     * 1) DPC2.CachedBrightnessInfo to determine changes
     * 2) DPC2.logBrightnessEvent
     * 3) HBMController - for logging
     * Method is called in mHandler thread (DisplayControllerHandler), in the same thread
     * recalculateBrightnessCap and DPC2.updatePowerStateInternal are called.
     * Should be moved to DisplayBrightnessState OR derived from DisplayBrightnessState
     * TODO: b/263362199
     */
    @BrightnessInfo.BrightnessMaxReason public int getBrightnessMaxReason() {
        if (mClamperType == null) {
            return BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;
        } else if (mClamperType == Type.THERMAL) {
            return BrightnessInfo.BRIGHTNESS_MAX_REASON_THERMAL;
        } else {
            Slog.wtf(TAG, "BrightnessMaxReason not mapped for type=" + mClamperType);
            return BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;
        }
    }

    /**
     * Used to dump ClampersController state.
     */
    public void dump(PrintWriter writer) {
        writer.println("BrightnessClampersController:");
        writer.println("  mBrightnessCap: " + mBrightnessCap);
        writer.println("  mClamperType: " + mClamperType);
        IndentingPrintWriter ipw = new IndentingPrintWriter(writer, "    ");
        mClampers.forEach(clamper -> clamper.dump(ipw));
        mModifiers.forEach(modifier -> modifier.dump(ipw));
    }

    /**
     * This method should be called when the ClamperController is no longer in use.
     * Called in DisplayControllerHandler
     */
    public void stop() {
        mDeviceConfigParameterProvider.removeOnPropertiesChangedListener(
                mOnPropertiesChangedListener);
        mClampers.forEach(BrightnessClamper::stop);
    }


    // Called in DisplayControllerHandler
    private void recalculateBrightnessCap() {
        float brightnessCap = PowerManager.BRIGHTNESS_MAX;
        Type clamperType = null;

        BrightnessClamper<?> minClamper = mClampers.stream()
                .filter(BrightnessClamper::isActive)
                .min((clamper1, clamper2) -> Float.compare(clamper1.getBrightnessCap(),
                        clamper2.getBrightnessCap())).orElse(null);

        if (minClamper != null) {
            brightnessCap = minClamper.getBrightnessCap();
            clamperType = minClamper.getType();
        }

        if (mBrightnessCap != brightnessCap || mClamperType != clamperType) {
            mBrightnessCap = brightnessCap;
            mClamperType = clamperType;
            mClamperChangeListenerExternal.onChanged();
        }
    }

    private void start() {
        if (!mClampers.isEmpty()) {
            mDeviceConfigParameterProvider.addOnPropertiesChangedListener(
                    mExecutor, mOnPropertiesChangedListener);
        }
    }

    /**
     * Clampers change listener
     */
    public interface ClamperChangeListener {
        /**
         * Notifies that clamper state changed
         */
        void onChanged();
    }

    @VisibleForTesting
    static class Injector {
        DeviceConfigParameterProvider getDeviceConfigParameterProvider() {
            return new DeviceConfigParameterProvider(DeviceConfigInterface.REAL);
        }

        List<BrightnessClamper<? super DisplayDeviceData>> getClampers(Handler handler,
                ClamperChangeListener clamperChangeListener, DisplayDeviceData data) {
            List<BrightnessClamper<? super DisplayDeviceData>> clampers = new ArrayList<>();
            clampers.add(
                    new BrightnessThermalClamper(handler, clamperChangeListener, data));
            return clampers;
        }

        List<BrightnessModifier> getModifiers(Context context) {
            List<BrightnessModifier> modifiers = new ArrayList<>();
            modifiers.add(new DisplayDimModifier(context));
            modifiers.add(new BrightnessLowPowerModeModifier());
            return modifiers;
        }
    }

    /**
     * Data for clampers
     */
    public static class DisplayDeviceData implements BrightnessThermalClamper.ThermalData {
        @NonNull
        private final String mUniqueDisplayId;
        @NonNull
        private final String mThermalThrottlingDataId;

        private final DisplayDeviceConfig mDisplayDeviceConfig;

        public DisplayDeviceData(@NonNull String uniqueDisplayId,
                @NonNull String thermalThrottlingDataId,
                @NonNull DisplayDeviceConfig displayDeviceConfig) {
            mUniqueDisplayId = uniqueDisplayId;
            mThermalThrottlingDataId = thermalThrottlingDataId;
            mDisplayDeviceConfig = displayDeviceConfig;
        }


        @NonNull
        @Override
        public String getUniqueDisplayId() {
            return mUniqueDisplayId;
        }

        @NonNull
        @Override
        public String getThermalThrottlingDataId() {
            return mThermalThrottlingDataId;
        }

        @Nullable
        @Override
        public ThermalBrightnessThrottlingData getThermalBrightnessThrottlingData() {
            return mDisplayDeviceConfig.getThermalBrightnessThrottlingDataMapByThrottlingId().get(
                    mThermalThrottlingDataId);
        }
    }
}
