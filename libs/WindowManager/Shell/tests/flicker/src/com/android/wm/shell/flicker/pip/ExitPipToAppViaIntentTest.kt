/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.FlakyTest
import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.FlickerBuilder
import com.android.server.wm.flicker.FlickerTest
import com.android.server.wm.flicker.helpers.isShellTransitionsEnabled
import com.android.server.wm.flicker.junit.FlickerParametersRunnerFactory
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test expanding a pip window back to full screen via an intent
 *
 * To run this test: `atest WMShellFlickerTests:ExitPipViaIntentTest`
 *
 * Actions:
 * ```
 *     Launch an app in pip mode [pipApp],
 *     Launch another full screen mode [testApp]
 *     Expand [pipApp] app to full screen via an intent
 * ```
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited from [PipTransition]
 *     2. Part of the test setup occurs automatically via
 *        [com.android.server.wm.flicker.TransitionRunnerWithRules],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class ExitPipToAppViaIntentTest(flicker: FlickerTest) : ExitPipToAppTransition(flicker) {

    /** Defines the transition used to run the test */
    override val transition: FlickerBuilder.() -> Unit
        get() = buildTransition {
            setup {
                // launch an app behind the pip one
                testApp.launchViaIntent(wmHelper)
            }
            transitions {
                // This will bring PipApp to fullscreen
                pipApp.exitPipToFullScreenViaIntent(wmHelper)
                // Wait until the other app is no longer visible
                wmHelper.StateSyncBuilder().withWindowSurfaceDisappeared(testApp).waitForAndVerify()
            }
        }

    /** {@inheritDoc} */
    @Presubmit @Test override fun entireScreenCovered() = super.entireScreenCovered()

    /** {@inheritDoc} */
    @Presubmit
    @Test
    override fun statusBarLayerPositionAtStartAndEnd() {
        Assume.assumeFalse(isShellTransitionsEnabled)
        super.statusBarLayerPositionAtStartAndEnd()
    }

    @Presubmit
    @Test
    fun statusBarLayerRotatesScales_ShellTransit() {
        Assume.assumeTrue(isShellTransitionsEnabled)
        super.statusBarLayerPositionAtStartAndEnd()
    }

    /** {@inheritDoc} */
    @FlakyTest(bugId = 197726610)
    @Test
    override fun pipLayerExpands() {
        Assume.assumeFalse(isShellTransitionsEnabled)
        super.pipLayerExpands()
    }

    @Presubmit
    @Test
    fun pipLayerExpands_ShellTransit() {
        Assume.assumeTrue(isShellTransitionsEnabled)
        super.pipLayerExpands()
    }
}
