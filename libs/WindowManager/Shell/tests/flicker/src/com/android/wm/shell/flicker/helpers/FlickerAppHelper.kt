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

package com.android.wm.shell.flicker.helpers

import android.app.Instrumentation
import android.support.test.launcherhelper.ILauncherStrategy
import com.android.server.wm.flicker.helpers.StandardAppHelper

abstract class FlickerAppHelper(
    instr: Instrumentation,
    launcherName: String,
    launcherStrategy: ILauncherStrategy
) : StandardAppHelper(instr, sFlickerPackage, launcherName, launcherStrategy) {
    companion object {
        var sFlickerPackage = "com.android.wm.shell.flicker.testapp"
    }
}
