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

package com.android.server.wm.flicker.launch

import android.platform.test.annotations.FlakyTest
import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import com.android.server.wm.flicker.FlickerBuilder
import com.android.server.wm.flicker.FlickerTest
import com.android.server.wm.flicker.FlickerTestFactory
import com.android.server.wm.flicker.annotation.FlickerServiceCompatible
import com.android.server.wm.flicker.helpers.setRotation
import com.android.server.wm.flicker.junit.FlickerParametersRunnerFactory
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test warm launching an app from launcher
 *
 * To run this test: `atest FlickerTests:OpenAppWarmTest`
 *
 * Actions:
 * ```
 *     Launch [testApp]
 *     Press home
 *     Relaunch an app [testApp] and wait animation to complete (only this action is traced)
 * ```
 * Notes:
 * ```
 *     1. Some default assertions (e.g., nav bar, status bar and screen covered)
 *        are inherited [OpenAppTransition]
 *     2. Part of the test setup occurs automatically via
 *        [com.android.server.wm.flicker.TransitionRunnerWithRules],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RequiresDevice
@FlickerServiceCompatible
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
open class OpenAppWarmTest(flicker: FlickerTest) : OpenAppFromLauncherTransition(flicker) {
    /** Defines the transition used to run the test */
    override val transition: FlickerBuilder.() -> Unit
        get() = {
            super.transition(this)
            setup {
                tapl.setExpectedRotationCheckEnabled(false)
                testApp.launchViaIntent(wmHelper)
                tapl.goHome()
                wmHelper.StateSyncBuilder().withHomeActivityVisible().waitForAndVerify()
                this.setRotation(flicker.scenario.startRotation)
            }
            teardown { testApp.exit(wmHelper) }
            transitions { testApp.launchViaIntent(wmHelper) }
        }

    /** {@inheritDoc} */
    @FlakyTest(bugId = 206753786)
    @Test
    override fun navBarLayerPositionAtStartAndEnd() = super.navBarLayerPositionAtStartAndEnd()

    /** {@inheritDoc} */
    @Presubmit
    @Test
    override fun appLayerBecomesVisible() = super.appLayerBecomesVisible_warmStart()

    /** {@inheritDoc} */
    @Presubmit
    @Test
    override fun appWindowBecomesVisible() = super.appWindowBecomesVisible_warmStart()

    companion object {
        /**
         * Creates the test configurations.
         *
         * See [FlickerTestFactory.nonRotationTests] for configuring screen orientation and
         * navigation modes.
         */
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun getParams(): Collection<FlickerTest> {
            return FlickerTestFactory.nonRotationTests()
        }
    }
}
