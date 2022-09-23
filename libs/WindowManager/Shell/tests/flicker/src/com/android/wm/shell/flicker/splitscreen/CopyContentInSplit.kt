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

package com.android.wm.shell.flicker.splitscreen

import android.platform.test.annotations.IwTest
import android.platform.test.annotations.Postsubmit
import android.platform.test.annotations.Presubmit
import android.view.WindowManagerPolicyConstants
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.FlickerParametersRunnerFactory
import com.android.server.wm.flicker.FlickerTestParameter
import com.android.server.wm.flicker.FlickerTestParameterFactory
import com.android.server.wm.flicker.annotation.Group1
import com.android.server.wm.flicker.dsl.FlickerBuilder
import com.android.wm.shell.flicker.SPLIT_SCREEN_DIVIDER_COMPONENT
import com.android.wm.shell.flicker.appWindowKeepVisible
import com.android.wm.shell.flicker.layerKeepVisible
import com.android.wm.shell.flicker.splitAppLayerBoundsKeepVisible
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test copy content from the left to the right side of the split-screen.
 *
 * To run this test: `atest WMShellFlickerTests:CopyContentInSplit`
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Group1
class CopyContentInSplit(testSpec: FlickerTestParameter) : SplitScreenBase(testSpec) {
    private val textEditApp = SplitScreenUtils.getIme(instrumentation)

    override val transition: FlickerBuilder.() -> Unit
        get() = {
            super.transition(this)
            setup {
                SplitScreenUtils.enterSplit(wmHelper, tapl, primaryApp, textEditApp)
            }
            transitions {
                SplitScreenUtils.copyContentInSplit(
                    instrumentation, device, primaryApp, textEditApp)
            }
        }

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun splitScreenDividerKeepVisible() = testSpec.layerKeepVisible(SPLIT_SCREEN_DIVIDER_COMPONENT)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun primaryAppLayerKeepVisible() = testSpec.layerKeepVisible(primaryApp)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun textEditAppLayerKeepVisible() = testSpec.layerKeepVisible(textEditApp)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun primaryAppBoundsKeepVisible() = testSpec.splitAppLayerBoundsKeepVisible(
        primaryApp, landscapePosLeft = tapl.isTablet, portraitPosTop = false)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun textEditAppBoundsKeepVisible() = testSpec.splitAppLayerBoundsKeepVisible(
        textEditApp, landscapePosLeft = !tapl.isTablet, portraitPosTop = true)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun primaryAppWindowKeepVisible() = testSpec.appWindowKeepVisible(primaryApp)

    @IwTest(focusArea = "sysui")
    @Presubmit
    @Test
    fun textEditAppWindowKeepVisible() = testSpec.appWindowKeepVisible(textEditApp)

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun entireScreenCovered() =
        super.entireScreenCovered()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarLayerIsVisibleAtStartAndEnd() =
        super.navBarLayerIsVisibleAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarLayerPositionAtStartAndEnd() =
        super.navBarLayerPositionAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun navBarWindowIsAlwaysVisible() =
        super.navBarWindowIsAlwaysVisible()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun statusBarLayerIsVisibleAtStartAndEnd() =
        super.statusBarLayerIsVisibleAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun statusBarLayerPositionAtStartAndEnd() =
        super.statusBarLayerPositionAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun statusBarWindowIsAlwaysVisible() =
        super.statusBarWindowIsAlwaysVisible()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun taskBarLayerIsVisibleAtStartAndEnd() =
        super.taskBarLayerIsVisibleAtStartAndEnd()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun taskBarWindowIsAlwaysVisible() =
        super.taskBarWindowIsAlwaysVisible()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() =
        super.visibleLayersShownMoreThanOneConsecutiveEntry()

    /** {@inheritDoc} */
    @Postsubmit
    @Test
    override fun visibleWindowsShownMoreThanOneConsecutiveEntry() =
        super.visibleWindowsShownMoreThanOneConsecutiveEntry()

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): List<FlickerTestParameter> {
            return FlickerTestParameterFactory.getInstance().getConfigNonRotationTests(
                // TODO(b/176061063):The 3 buttons of nav bar do not exist in the hierarchy.
                supportedNavigationModes =
                    listOf(WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL_OVERLAY))
        }
    }
}
