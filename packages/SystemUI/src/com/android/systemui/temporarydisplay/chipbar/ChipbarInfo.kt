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

package com.android.systemui.temporarydisplay.chipbar

import android.os.VibrationEffect
import android.view.View
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.temporarydisplay.TemporaryViewInfo

/**
 * A container for all the state needed to display a chipbar via [ChipbarCoordinator].
 *
 * @property startIcon the icon to display at the start of the chipbar (on the left in LTR locales;
 * on the right in RTL locales).
 * @property text the text to display.
 * @property endItem an optional end item to display at the end of the chipbar (on the right in LTR
 * locales; on the left in RTL locales).
 * @property vibrationEffect an optional vibration effect when the chipbar is displayed
 */
data class ChipbarInfo(
    val startIcon: Icon,
    val text: Text,
    val endItem: ChipbarEndItem?,
    val vibrationEffect: VibrationEffect? = null,
) : TemporaryViewInfo

/** The possible items to display at the end of the chipbar. */
sealed class ChipbarEndItem {
    /** A loading icon should be displayed. */
    object Loading : ChipbarEndItem()

    /** An error icon should be displayed. */
    object Error : ChipbarEndItem()

    /**
     * A button with the provided [text] and [onClickListener] functionality should be displayed.
     */
    data class Button(val text: Text, val onClickListener: View.OnClickListener) : ChipbarEndItem()

    // TODO(b/245610654): Add support for a generic icon.
}
