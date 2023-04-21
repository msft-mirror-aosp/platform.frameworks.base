/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.pipeline.domain.interactor

import android.content.Context
import android.view.View
import com.android.internal.logging.InstanceId
import com.android.systemui.plugins.qs.QSIconView
import com.android.systemui.plugins.qs.QSTile

class FakeQSTile(
    var user: Int,
    var available: Boolean = true,
) : QSTile {
    private var tileSpec: String? = null
    var destroyed = false
    private val state = QSTile.State()

    override fun getTileSpec(): String? {
        return tileSpec
    }

    override fun isAvailable(): Boolean {
        return available
    }

    override fun setTileSpec(tileSpec: String?) {
        this.tileSpec = tileSpec
        state.spec = tileSpec
    }

    override fun refreshState() {}

    override fun addCallback(callback: QSTile.Callback?) {}

    override fun removeCallback(callback: QSTile.Callback?) {}

    override fun removeCallbacks() {}

    override fun createTileView(context: Context?): QSIconView? {
        return null
    }

    override fun click(view: View?) {}

    override fun secondaryClick(view: View?) {}

    override fun longClick(view: View?) {}

    override fun userSwitch(currentUser: Int) {
        user = currentUser
    }

    override fun getMetricsCategory(): Int {
        return 0
    }

    override fun setListening(client: Any?, listening: Boolean) {}

    override fun setDetailListening(show: Boolean) {}

    override fun destroy() {
        destroyed = true
    }

    override fun getTileLabel(): CharSequence {
        return ""
    }

    override fun getState(): QSTile.State {
        return state
    }

    override fun getInstanceId(): InstanceId {
        return InstanceId.fakeInstanceId(0)
    }

    override fun isListening(): Boolean {
        return false
    }
}
