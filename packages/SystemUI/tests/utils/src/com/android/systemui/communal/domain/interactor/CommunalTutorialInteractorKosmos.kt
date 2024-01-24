/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.communal.domain.interactor

import com.android.systemui.communal.data.repository.communalRepository
import com.android.systemui.communal.data.repository.communalTutorialRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope

val Kosmos.communalTutorialInteractor by
    Kosmos.Fixture {
        CommunalTutorialInteractor(
            scope = applicationCoroutineScope,
            communalTutorialRepository = communalTutorialRepository,
            keyguardInteractor = keyguardInteractor,
            communalRepository = communalRepository,
            communalInteractor = communalInteractor,
        )
    }
