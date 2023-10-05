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

package com.android.systemui.qs.tiles.viewmodel

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.internal.logging.InstanceId
import com.android.systemui.RoboPilotTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.base.analytics.QSTileAnalytics
import com.android.systemui.qs.tiles.base.interactor.FakeDisabledByPolicyInteractor
import com.android.systemui.qs.tiles.base.interactor.FakeQSTileDataInteractor
import com.android.systemui.qs.tiles.base.interactor.FakeQSTileUserActionInteractor
import com.android.systemui.qs.tiles.base.interactor.QSTileDataRequest
import com.android.systemui.qs.tiles.base.interactor.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.base.interactor.StateUpdateTrigger
import com.android.systemui.qs.tiles.base.logging.QSTileLogger
import com.android.systemui.qs.tiles.base.viewmodel.BaseQSTileViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

// TODO(b/299909368): Add more tests
@MediumTest
@RoboPilotTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class QSTileViewModelInterfaceComplianceTest : SysuiTestCase() {

    @Mock private lateinit var qsTileLogger: QSTileLogger
    @Mock private lateinit var qsTileAnalytics: QSTileAnalytics

    private val fakeQSTileDataInteractor = FakeQSTileDataInteractor<Any>()
    private val fakeQSTileUserActionInteractor = FakeQSTileUserActionInteractor<Any>()
    private val fakeDisabledByPolicyInteractor = FakeDisabledByPolicyInteractor()
    private val fakeFalsingManager = FalsingManagerFake()

    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testCoroutineDispatcher)

    private lateinit var underTest: QSTileViewModel

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = createViewModel(testScope)
    }

    @Test
    fun testDoesntListenStateUntilCreated() =
        testScope.runTest {
            assertThat(fakeQSTileDataInteractor.dataRequests).isEmpty()

            underTest.onLifecycle(QSTileLifecycle.ALIVE)
            underTest.onUserIdChanged(1)

            assertThat(fakeQSTileDataInteractor.dataRequests).isEmpty()

            underTest.state.launchIn(backgroundScope)
            runCurrent()

            assertThat(fakeQSTileDataInteractor.dataRequests).isNotEmpty()
            assertThat(fakeQSTileDataInteractor.dataRequests.first())
                .isEqualTo(QSTileDataRequest(1, StateUpdateTrigger.InitialRequest))
        }

    private fun createViewModel(
        scope: TestScope,
        config: QSTileConfig = TEST_QS_TILE_CONFIG,
    ): QSTileViewModel =
        BaseQSTileViewModel(
            config,
            fakeQSTileUserActionInteractor,
            fakeQSTileDataInteractor,
            object : QSTileDataToStateMapper<Any> {
                override fun map(config: QSTileConfig, data: Any): QSTileState =
                    QSTileState.build(Icon.Resource(0, ContentDescription.Resource(0)), "") {}
            },
            fakeDisabledByPolicyInteractor,
            fakeFalsingManager,
            qsTileAnalytics,
            qsTileLogger,
            testCoroutineDispatcher,
            scope.backgroundScope,
        )

    private companion object {

        val TEST_QS_TILE_CONFIG =
            QSTileConfig(
                TileSpec.create("default"),
                Icon.Resource(0, null),
                0,
                InstanceId.fakeInstanceId(0),
            )
    }
}
