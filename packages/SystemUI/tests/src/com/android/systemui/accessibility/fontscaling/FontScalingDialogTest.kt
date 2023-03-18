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
package com.android.systemui.accessibility.fontscaling

import android.os.Handler
import android.provider.Settings
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.test.filters.SmallTest
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.view.SeekBarWithIconButtonsView
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.settings.FakeSettings
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SystemSettings
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations

private const val ON: Int = 1
private const val OFF: Int = 0

/** Tests for [FontScalingDialog]. */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class FontScalingDialogTest : SysuiTestCase() {
    private lateinit var fontScalingDialog: FontScalingDialog
    private lateinit var systemSettings: SystemSettings
    private lateinit var secureSettings: SecureSettings
    private lateinit var backgroundExecutor: FakeExecutor
    private val fontSizeValueArray: Array<String> =
        mContext
            .getResources()
            .getStringArray(com.android.settingslib.R.array.entryvalues_font_size)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val mainHandler = Handler(TestableLooper.get(this).getLooper())
        systemSettings = FakeSettings()
        secureSettings = FakeSettings()
        backgroundExecutor = FakeExecutor(FakeSystemClock())
        fontScalingDialog =
            FontScalingDialog(mContext, systemSettings, secureSettings, backgroundExecutor)
    }

    @Test
    fun showTheDialog_seekbarIsShowingCorrectProgress() {
        fontScalingDialog.show()

        val seekBar: SeekBar = fontScalingDialog.findViewById<SeekBar>(R.id.seekbar)!!
        val progress: Int = seekBar.getProgress()
        val currentScale = systemSettings.getFloat(Settings.System.FONT_SCALE, /* def = */ 1.0f)

        assertThat(currentScale).isEqualTo(fontSizeValueArray[progress].toFloat())

        fontScalingDialog.dismiss()
    }

    @Test
    fun progressIsZero_clickIconEnd_seekBarProgressIncreaseOne_fontSizeScaled() {
        fontScalingDialog.show()

        val iconEndFrame: ViewGroup = fontScalingDialog.findViewById(R.id.icon_end_frame)!!
        val seekBarWithIconButtonsView: SeekBarWithIconButtonsView =
            fontScalingDialog.findViewById(R.id.font_scaling_slider)!!
        val seekBar: SeekBar = fontScalingDialog.findViewById(R.id.seekbar)!!

        seekBarWithIconButtonsView.setProgress(0)

        iconEndFrame.performClick()
        backgroundExecutor.runAllReady()

        val currentScale = systemSettings.getFloat(Settings.System.FONT_SCALE, /* def = */ 1.0f)
        assertThat(seekBar.getProgress()).isEqualTo(1)
        assertThat(currentScale).isEqualTo(fontSizeValueArray[1].toFloat())

        fontScalingDialog.dismiss()
    }

    @Test
    fun progressIsMax_clickIconStart_seekBarProgressDecreaseOne_fontSizeScaled() {
        fontScalingDialog.show()

        val iconStartFrame: ViewGroup = fontScalingDialog.findViewById(R.id.icon_start_frame)!!
        val seekBarWithIconButtonsView: SeekBarWithIconButtonsView =
            fontScalingDialog.findViewById(R.id.font_scaling_slider)!!
        val seekBar: SeekBar = fontScalingDialog.findViewById(R.id.seekbar)!!

        seekBarWithIconButtonsView.setProgress(fontSizeValueArray.size - 1)

        iconStartFrame.performClick()
        backgroundExecutor.runAllReady()

        val currentScale = systemSettings.getFloat(Settings.System.FONT_SCALE, /* def = */ 1.0f)
        assertThat(seekBar.getProgress()).isEqualTo(fontSizeValueArray.size - 2)
        assertThat(currentScale)
            .isEqualTo(fontSizeValueArray[fontSizeValueArray.size - 2].toFloat())

        fontScalingDialog.dismiss()
    }

    @Test
    fun progressChanged_keyWasNotSetBefore_fontScalingHasBeenChangedIsOn() {
        fontScalingDialog.show()

        val seekBarWithIconButtonsView: SeekBarWithIconButtonsView =
            fontScalingDialog.findViewById(R.id.font_scaling_slider)!!
        secureSettings.putInt(Settings.Secure.ACCESSIBILITY_FONT_SCALING_HAS_BEEN_CHANGED, OFF)

        // Default seekbar progress for font size is 1, set it to another progress 0
        seekBarWithIconButtonsView.setProgress(0)
        backgroundExecutor.runAllReady()

        val currentSettings =
            secureSettings.getInt(
                Settings.Secure.ACCESSIBILITY_FONT_SCALING_HAS_BEEN_CHANGED,
                /* def = */ OFF
            )
        assertThat(currentSettings).isEqualTo(ON)

        fontScalingDialog.dismiss()
    }
}
