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
public class VarHandleSetArrayLittleEndianStringPerfTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();
    static final String ELEMENT_VALUE = "qwerty";
    String[] mArray = { ELEMENT_VALUE };
    VarHandle mVh;

    public VarHandleSetArrayLittleEndianStringPerfTest() throws Throwable {
        mVh = MethodHandles.arrayElementVarHandle(String[].class);
    }

    @After
    public void teardown() {
        if (mArray[0] != null) {
            throw new RuntimeException("array element has unexpected value: " + mArray[0]);
        }
    }

    @Test
    public void run() {
        String[] a = mArray;
        String x;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mVh.set(a, 0, null);
            mVh.set(a, 0, null);
        }
    }
}
