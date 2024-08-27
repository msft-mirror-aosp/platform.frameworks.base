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
 // This file is generated by generate_java.py do not directly modify!
package android.libcore.varhandles;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class VarHandleSetReleaseFieldLittleEndianIntPerfTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();
    static final int FIELD_VALUE = 42;
    int mField = FIELD_VALUE;
    VarHandle mVh;

    public VarHandleSetReleaseFieldLittleEndianIntPerfTest() throws Throwable {
        mVh = MethodHandles.lookup().findVarHandle(this.getClass(), "mField", int.class);
    }

    @After
    public void teardown() {
        if (mField != FIELD_VALUE) {
            throw new RuntimeException("mField has unexpected value " + mField);
        }
    }

    @Test
    public void run() {
        int x;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mVh.setRelease(this, FIELD_VALUE);
            mVh.setRelease(this, FIELD_VALUE);
        }
    }
}
