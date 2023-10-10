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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.statusbar.notification.data.repository

import androidx.test.filters.SmallTest
import com.android.SysUITestModule
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.mockito.withArgCaptor
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.Mockito.verify

@SmallTest
class NotificationsKeyguardViewStateRepositoryTest : SysuiTestCase() {

    private val testComponent: TestComponent =
        DaggerNotificationsKeyguardViewStateRepositoryTest_TestComponent.factory()
            .create(test = this)

    @Test
    fun areNotifsFullyHidden_reflectsWakeUpCoordinator() =
        with(testComponent) {
            testScope.runTest {
                whenever(mockWakeUpCoordinator.notificationsFullyHidden).thenReturn(false)
                val notifsFullyHidden by collectLastValue(underTest.areNotificationsFullyHidden)
                runCurrent()

                assertThat(notifsFullyHidden).isFalse()

                withArgCaptor { verify(mockWakeUpCoordinator).addListener(capture()) }
                    .onFullyHiddenChanged(true)
                runCurrent()

                assertThat(notifsFullyHidden).isTrue()
            }
        }

    @Test
    fun isPulseExpanding_reflectsWakeUpCoordinator() =
        with(testComponent) {
            testScope.runTest {
                whenever(mockWakeUpCoordinator.isPulseExpanding()).thenReturn(false)
                val isPulseExpanding by collectLastValue(underTest.isPulseExpanding)
                runCurrent()

                assertThat(isPulseExpanding).isFalse()

                withArgCaptor { verify(mockWakeUpCoordinator).addListener(capture()) }
                    .onPulseExpansionChanged(true)
                runCurrent()

                assertThat(isPulseExpanding).isTrue()
            }
        }

    @SysUISingleton
    @Component(
        modules =
            [
                SysUITestModule::class,
            ]
    )
    interface TestComponent {

        val underTest: NotificationsKeyguardViewStateRepositoryImpl

        val mockWakeUpCoordinator: NotificationWakeUpCoordinator
        val testScope: TestScope

        @Component.Factory
        interface Factory {
            fun create(
                @BindsInstance test: SysuiTestCase,
            ): TestComponent
        }
    }
}
