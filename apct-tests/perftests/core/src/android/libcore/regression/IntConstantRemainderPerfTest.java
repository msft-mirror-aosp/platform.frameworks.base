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

package android.libcore.regression;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class IntConstantRemainderPerfTest {
    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    @Test
    public void timeRemainderIntByConstant2() {
        int result = 1;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= 2;
        }
    }

    @Test
    public void timeRemainderIntByConstant8() {
        int result = 1;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= 8;
        }
    }

    @Test
    public void timeRemainderIntByConstant10() {
        int result = 1;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= 10;
        }
    }

    @Test
    public void timeRemainderIntByConstant100() {
        int result = 1;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= 100;
        }
    }

    @Test
    public void timeRemainderIntByConstant2048() {
        int result = 1;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= 2048;
        }
    }

    @Test
    public void timeRemainderIntByVariable2() {
        int result = 1;
        int factor = 2;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= factor;
        }
    }

    @Test
    public void timeRemainderIntByVariable10() {
        int result = 1;
        int factor = 10;
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            result %= factor;
        }
    }
}
