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

package com.android.systemui.flags

import android.content.Context
import android.os.Handler
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.SettingsUtilModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.function.Supplier

@Module(includes = [
    SettingsUtilModule::class
])
abstract class FlagsModule {
    @Binds
    abstract fun bindsFeatureFlagDebug(impl: FeatureFlagsDebug): FeatureFlags

    @Module
    companion object {
        @JvmStatic
        @Provides
        fun provideFlagManager(context: Context, @Main handler: Handler): FlagManager {
            return FlagManager(context, handler)
        }

        @JvmStatic
        @Provides
        fun providesFlagCollector(): Supplier<Map<Int, Flag<*>>>? = null
    }
}