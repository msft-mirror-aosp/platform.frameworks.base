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

package com.android.internal.os;

import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Maintains multidimensional multi-state stats.  States could be something like on-battery (0,1),
 * screen-on (0,1), process state etc.  Dimensions refer to the metrics themselves, e.g.
 * CPU residency, Network packet counts etc.  All metrics must be represented as <code>long</code>
 * values;
 */
public class MultiStateStats {
    private static final String TAG = "MultiStateStats";

    private static final String XML_TAG_STATS = "stats";

    /**
     * A set of states, e.g. on-battery, screen-on, procstate.  The state values are integers
     * from 0 to States.mLabels.length
     */
    public static class States {
        final String mName;
        final boolean mTracked;
        final String[] mLabels;

        public States(String name, boolean tracked, String... labels) {
            mName = name;
            this.mTracked = tracked;
            this.mLabels = labels;
        }

        public boolean isTracked() {
            return mTracked;
        }
    }

    /**
     * Factory for MultiStateStats containers. All generated containers retain their connection
     * to the Factory and the corresponding configuration.
     */
    public static class Factory {
        private static final int INVALID_SERIAL_STATE = -1;
        final int mDimensionCount;
        final States[] mStates;
        /*
         * The LongArrayMultiStateCounter object that is used for accumulation of per-state
         * stats thinks of "state" as a simple 0-based index. This Factory object's job is to
         * map a combination of individual states (e.g. on-battery, process state etc) to
         * such a simple index.
         *
         * This task is performed in two steps:
         * 1) We define "composite state" as an integer that combines all constituent States
         * into one integer as bit fields. This gives us a convenient mechanism for updating a
         * single constituent State at a time.  We maintain an array of bit field masks
         * corresponding to each constituent State.
         *
         * 2) We map composite states to "serial states", i.e. simple integer indexes, taking
         * into account which constituent states are configured as tracked.  If a state is not
         * tracked, there is no need to maintain separate counts for its values, thus
         * all values of that constituent state can be mapped to the same serial state.
         */
        private final int[] mStateBitFieldMasks;
        private final short[] mStateBitFieldShifts;
        final int[] mCompositeToSerialState;
        final int mSerialStateCount;

        public Factory(int dimensionCount, States... states) {
            mDimensionCount = dimensionCount;
            mStates = states;

            int serialStateCount = 1;
            for (States state : mStates) {
                if (state.mTracked) {
                    serialStateCount *= state.mLabels.length;
                }
            }
            mSerialStateCount = serialStateCount;

            mStateBitFieldMasks = new int[mStates.length];
            mStateBitFieldShifts = new short[mStates.length];

            int shift = 0;
            for (int i = 0; i < mStates.length; i++) {
                mStateBitFieldShifts[i] = (short) shift;
                if (mStates[i].mLabels.length < 2) {
                    throw new IllegalArgumentException("Invalid state: " + Arrays.toString(
                            mStates[i].mLabels) + ". Should have at least two values.");
                }
                int max = mStates[i].mLabels.length - 1;
                int bitcount = Integer.SIZE - Integer.numberOfLeadingZeros(max);
                mStateBitFieldMasks[i] = ((1 << bitcount) - 1) << shift;
                shift = shift + bitcount;
            }

            if (shift >= Integer.SIZE - 1) {
                throw new IllegalArgumentException("Too many states: " + shift
                        + " bits are required to represent the composite state, but only "
                        + (Integer.SIZE - 1) + " are available");
            }

            // Create a mask that filters out all non tracked states
            int trackedMask = 0xFFFFFFFF;
            for (int state = 0; state < mStates.length; state++) {
                if (!mStates[state].mTracked) {
                    trackedMask &= ~mStateBitFieldMasks[state];
                }
            }

            mCompositeToSerialState = new int[1 << shift];
            Arrays.fill(mCompositeToSerialState, INVALID_SERIAL_STATE);

            int nextSerialState = 0;
            for (int composite = 0; composite < mCompositeToSerialState.length; composite++) {
                if (!isValidCompositeState(composite)) continue;

                // Values of an untracked State map to different composite states, but must map to
                // the same serial state. Achieve that by computing a "base composite", which
                // is equivalent to the current composite, but has 0 for all untracked States.
                // See if the base composite already has a serial state assigned.  If so, just use
                // the same one for the current composite.
                int baseComposite = composite & trackedMask;
                if (mCompositeToSerialState[baseComposite] != INVALID_SERIAL_STATE) {
                    mCompositeToSerialState[composite] = mCompositeToSerialState[baseComposite];
                } else {
                    mCompositeToSerialState[composite] = nextSerialState++;
                }
            }
        }

        private boolean isValidCompositeState(int composite) {
            for (int stateIndex = 0; stateIndex < mStates.length; stateIndex++) {
                int state = extractStateFromComposite(composite, stateIndex);
                if (state >= mStates[stateIndex].mLabels.length) {
                    return false;
                }
            }
            return true;
        }

        private int extractStateFromComposite(int compositeState, int stateIndex) {
            return (compositeState & mStateBitFieldMasks[stateIndex])
                   >>> mStateBitFieldShifts[stateIndex];
        }

        int setStateInComposite(int baseCompositeState, int stateIndex, int value) {
            return (baseCompositeState & ~mStateBitFieldMasks[stateIndex])
                    | (value << mStateBitFieldShifts[stateIndex]);
        }

        int setStateInComposite(int compositeState, String stateName, String stateLabel) {
            for (int stateIndex = 0; stateIndex < mStates.length; stateIndex++) {
                States stateConfig = mStates[stateIndex];
                if (stateConfig.mName.equals(stateName)) {
                    for (int state = 0; state < stateConfig.mLabels.length; state++) {
                        if (stateConfig.mLabels[state].equals(stateLabel)) {
                            return setStateInComposite(compositeState, stateIndex, state);
                        }
                    }
                    Slog.e(TAG, "Unexpected label '" + stateLabel + "' for state: " + stateName);
                    return -1;
                }
            }
            Slog.e(TAG, "Unsupported state: " + stateName);
            return -1;
        }

        /**
         * Allocates a new stats container using this Factory's configuration.
         */
        public MultiStateStats create() {
            return new MultiStateStats(this, mDimensionCount);
        }

        /**
         * Returns the total number of composite states handled by this container. For example,
         * if there are two states: on-battery (0,1) and screen-on (0,1), both tracked, then the
         * serial state count will be 2 * 2 = 4
         */
        @VisibleForTesting
        public int getSerialStateCount() {
            return mSerialStateCount;
        }

        /**
         * Returns the integer index used by this container to represent the supplied composite
         * state.
         */
        @VisibleForTesting
        public int getSerialState(int[] states) {
            Preconditions.checkArgument(states.length == mStates.length);
            int compositeState = 0;
            for (int i = 0; i < states.length; i++) {
                compositeState = setStateInComposite(compositeState, i, states[i]);
            }
            int serialState = mCompositeToSerialState[compositeState];
            if (serialState == INVALID_SERIAL_STATE) {
                throw new IllegalArgumentException("State values out of bounds: "
                                                   + Arrays.toString(states));
            }
            return serialState;
        }

        int getSerialState(int compositeState) {
            return mCompositeToSerialState[compositeState];
        }
    }

    private final Factory mFactory;
    private final LongArrayMultiStateCounter mCounter;
    private int mCompositeState;
    private boolean mTracking;

    public MultiStateStats(Factory factory, int dimensionCount) {
        this.mFactory = factory;
        mCounter = new LongArrayMultiStateCounter(factory.mSerialStateCount, dimensionCount);
    }

    /**
     * Updates the current composite state by changing one of the States supplied to the Factory
     * constructor.
     *
     * @param stateIndex  Corresponds to the index of the States supplied to the Factory constructor
     * @param state       The new value of the state (e.g. 0 or 1 for "on-battery")
     * @param timestampMs The time when the state change occurred
     */
    public void setState(int stateIndex, int state, long timestampMs) {
        if (!mTracking) {
            mCounter.updateValues(new long[mCounter.getArrayLength()], timestampMs);
            mTracking = true;
        }
        mCompositeState = mFactory.setStateInComposite(mCompositeState, stateIndex, state);
        mCounter.setState(mFactory.mCompositeToSerialState[mCompositeState], timestampMs);
    }

    /**
     * Adds the delta to the metrics.  The number of values must correspond to the dimension count
     * supplied to the Factory constructor
     */
    public void increment(long[] values, long timestampMs) {
        mCounter.incrementValues(values, timestampMs);
        mTracking = true;
    }

    /**
     * Returns accumulated stats for the specified composite state.
     */
    public void getStats(long[] outValues, int[] states) {
        mCounter.getCounts(outValues, mFactory.getSerialState(states));
    }

    /**
     * Resets the counters.
     */
    public void reset() {
        mCounter.reset();
        mTracking = false;
    }

    @Override
    public String toString() {
        return mCounter.toString();
    }

    /**
     * Stores contents in an XML doc.
     */
    public void writeXml(TypedXmlSerializer serializer) throws IOException {
        long[] tmpArray = new long[mCounter.getArrayLength()];
        writeXmlAllStates(serializer, new int[mFactory.mStates.length], 0, tmpArray);
    }

    private void writeXmlAllStates(TypedXmlSerializer serializer, int[] states, int stateIndex,
            long[] values) throws IOException {
        if (stateIndex < states.length) {
            if (!mFactory.mStates[stateIndex].mTracked) {
                writeXmlAllStates(serializer, states, stateIndex + 1, values);
                return;
            }

            for (int i = 0; i < mFactory.mStates[stateIndex].mLabels.length; i++) {
                states[stateIndex] = i;
                writeXmlAllStates(serializer, states, stateIndex + 1, values);
            }
            return;
        }

        mCounter.getCounts(values, mFactory.getSerialState(states));
        boolean nonZero = false;
        for (long value : values) {
            if (value != 0) {
                nonZero = true;
                break;
            }
        }
        if (!nonZero) {
            return;
        }

        serializer.startTag(null, XML_TAG_STATS);

        for (int i = 0; i < states.length; i++) {
            if (mFactory.mStates[i].mTracked && states[i] != 0) {
                serializer.attribute(null, mFactory.mStates[i].mName,
                        mFactory.mStates[i].mLabels[states[i]]);
            }
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) {
                serializer.attributeLong(null, "_" + i, values[i]);
            }
        }
        serializer.endTag(null, XML_TAG_STATS);
    }

    /**
     * Populates the object with contents in an XML doc. The parser is expected to be
     * positioned on the opening tag of the corresponding element.
     */
    public boolean readFromXml(TypedXmlPullParser parser) throws XmlPullParserException,
            IOException {
        String outerTag = parser.getName();
        long[] tmpArray = new long[mCounter.getArrayLength()];
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT
               && !(eventType == XmlPullParser.END_TAG && parser.getName().equals(outerTag))) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals(XML_TAG_STATS)) {
                    Arrays.fill(tmpArray, 0);
                    int compositeState = 0;
                    int attributeCount = parser.getAttributeCount();
                    for (int i = 0; i < attributeCount; i++) {
                        String attributeName = parser.getAttributeName(i);
                        if (attributeName.startsWith("_")) {
                            int index;
                            try {
                                index = Integer.parseInt(attributeName.substring(1));
                            } catch (NumberFormatException e) {
                                throw new XmlPullParserException(
                                        "Unexpected index syntax: " + attributeName, parser, e);
                            }
                            if (index < 0 || index >= tmpArray.length) {
                                Slog.e(TAG, "State index out of bounds: " + index
                                            + " length: " + tmpArray.length);
                                return false;
                            }
                            tmpArray[index] = parser.getAttributeLong(i);
                        } else {
                            String attributeValue = parser.getAttributeValue(i);
                            compositeState = mFactory.setStateInComposite(compositeState,
                                    attributeName, attributeValue);
                            if (compositeState == -1) {
                                return false;
                            }
                        }
                    }
                    mCounter.setValues(mFactory.getSerialState(compositeState), tmpArray);
                }
            }
            eventType = parser.next();
        }
        return true;
    }

    /**
     * Prints the accumulated stats, one line of every combination of states that has data.
     */
    public void dump(PrintWriter pw) {
        long[] tmpArray = new long[mCounter.getArrayLength()];
        dumpAllStates(pw, new int[mFactory.mStates.length], 0, tmpArray);
    }

    private void dumpAllStates(PrintWriter pw, int[] states, int stateIndex, long[] values) {
        if (stateIndex < states.length) {
            if (!mFactory.mStates[stateIndex].mTracked) {
                dumpAllStates(pw, states, stateIndex + 1, values);
                return;
            }

            for (int i = 0; i < mFactory.mStates[stateIndex].mLabels.length; i++) {
                states[stateIndex] = i;
                dumpAllStates(pw, states, stateIndex + 1, values);
            }
            return;
        }

        mCounter.getCounts(values, mFactory.getSerialState(states));
        boolean nonZero = false;
        for (long value : values) {
            if (value != 0) {
                nonZero = true;
                break;
            }
        }
        if (!nonZero) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < states.length; i++) {
            if (mFactory.mStates[i].mTracked) {
                if (sb.length() != 0) {
                    sb.append(" ");
                }
                sb.append(mFactory.mStates[i].mLabels[states[i]]);
            }
        }
        sb.append(" ");
        sb.append(Arrays.toString(values));
        pw.println(sb);
    }
}
