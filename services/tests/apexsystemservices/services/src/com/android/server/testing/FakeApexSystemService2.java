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

package com.android.server.testing;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.SystemService;

/**
 * A fake system service that just logs when it is started.
 */
public class FakeApexSystemService2 extends SystemService {

    private static final String TAG = "FakeApexSystemService";

    public FakeApexSystemService2(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "FakeApexSystemService2 onStart");
    }
}
