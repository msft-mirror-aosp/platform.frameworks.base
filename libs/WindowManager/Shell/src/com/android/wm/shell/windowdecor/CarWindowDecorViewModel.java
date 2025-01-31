/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.wm.shell.windowdecor;

import android.annotation.SuppressLint;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.InsetsState;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.annotations.ShellBackgroundThread;
import com.android.wm.shell.common.annotations.ShellMainThread;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.splitscreen.SplitScreenController;

import java.util.HashMap;
import java.util.Map;

/**
 * Works with decorations that extend {@link CarWindowDecoration}.
 */
public abstract class CarWindowDecorViewModel
        implements WindowDecorViewModel, DisplayController.OnDisplaysChangedListener {
    private static final String TAG = "CarWindowDecorViewModel";

    private final ShellTaskOrganizer mTaskOrganizer;
    private final Context mContext;
    private final @ShellBackgroundThread ShellExecutor mBgExecutor;
    private final @ShellMainThread ShellExecutor mMainExecutor;
    private final DisplayController mDisplayController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final SyncTransactionQueue mSyncQueue;
    private final SparseArray<CarWindowDecoration> mWindowDecorByTaskId = new SparseArray<>();
    private final Map<Integer, DisplayInsetsController.OnInsetsChangedListener>
            mDisplayIdToInsetsChangedListenerMap = new HashMap<>();

    public CarWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue) {
        mContext = context;
        mMainExecutor = mainExecutor;
        mBgExecutor = bgExecutor;
        mTaskOrganizer = taskOrganizer;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mSyncQueue = syncQueue;

        mDisplayController.addDisplayWindowListener(this);
    }

    @Override
    public void onDisplayAdded(int displayId) {
        DisplayInsetsController.OnInsetsChangedListener listener =
                mDisplayIdToInsetsChangedListenerMap.computeIfAbsent(displayId,
                        key -> new InsetsChangedListener(key));
        mDisplayInsetsController.addInsetsChangedListener(displayId, listener);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        DisplayInsetsController.OnInsetsChangedListener listener =
                mDisplayIdToInsetsChangedListenerMap.remove(displayId);
        if (listener == null) {
            return;
        }
        mDisplayInsetsController.removeInsetsChangedListener(displayId, listener);
    }

    private class InsetsChangedListener implements DisplayInsetsController.OnInsetsChangedListener {
        private final int mDisplayId;

        private InsetsChangedListener(int displayId) {
            mDisplayId = displayId;
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            ActivityTaskManager.getInstance().getTasks(
                            Integer.MAX_VALUE, /* filterOnlyVisibleRecents= */ false,
                            /* keepIntentExtra= */ false, mDisplayId)
                    .stream().filter(taskInfo -> taskInfo.isRunning && taskInfo.isVisible)
                    .forEach(taskInfo -> {
                        onTaskInfoChanged(taskInfo);
                    });
        }

    }

    @Override
    public void setFreeformTaskTransitionStarter(FreeformTaskTransitionStarter transitionStarter) {
        // no-op
    }

    @Override
    public void setSplitScreenController(SplitScreenController splitScreenController) {
        // no-op
    }

    @Override
    public boolean onTaskOpening(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        return true;
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);

        if (decoration == null) {
            return;
        }

        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        decoration.relayout(taskInfo, t, t,
                /* isCaptionVisible= */ shouldShowWindowDecor(taskInfo));
    }

    @Override
    public void onTaskChanging(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);

        if (!shouldShowWindowDecor(taskInfo)) {
            if (decoration != null) {
                destroyWindowDecoration(taskInfo);
            }
            return;
        }

        if (decoration == null) {
            createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        } else {
            decoration.relayout(taskInfo, startT, finishT);
        }
    }

    @Override
    public void onTaskClosing(
            RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) {
            return;
        }
        decoration.relayout(taskInfo, startT, finishT);
    }

    @Override
    public void destroyWindowDecoration(RunningTaskInfo taskInfo) {
        final CarWindowDecoration decoration =
                mWindowDecorByTaskId.removeReturnOld(taskInfo.taskId);
        if (decoration == null) {
            return;
        }

        decoration.close();
    }

    /**
     * @return {@code true} if the task/activity associated with {@code taskInfo} should show
     * window decoration.
     */
    protected abstract boolean shouldShowWindowDecor(RunningTaskInfo taskInfo);

    private void createWindowDecoration(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final CarWindowDecoration oldDecoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (oldDecoration != null) {
            // close the old decoration if it exists to avoid two window decorations being added
            oldDecoration.close();
        }
        final CarWindowDecoration windowDecoration =
                new CarWindowDecoration(
                        mContext,
                        mDisplayController,
                        mTaskOrganizer,
                        taskInfo,
                        taskSurface,
                        mBgExecutor,
                        new ButtonClickListener(taskInfo));
        mWindowDecorByTaskId.put(taskInfo.taskId, windowDecoration);
        windowDecoration.relayout(taskInfo, startT, finishT,
                /* isCaptionVisible= */ shouldShowWindowDecor(taskInfo));
    }

    private class ButtonClickListener implements View.OnClickListener {
        private final WindowContainerToken mTaskToken;
        private final int mDisplayId;

        private ButtonClickListener(RunningTaskInfo taskInfo) {
            mTaskToken = taskInfo.token;
            mDisplayId = taskInfo.displayId;
        }

        @Override
        public void onClick(View v) {
            final int id = v.getId();
            if (id == R.id.close_window) {
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.removeTask(mTaskToken);
                mSyncQueue.queue(wct);
            } else if (id == R.id.back_button) {
                sendBackEvent(KeyEvent.ACTION_DOWN, mDisplayId);
                sendBackEvent(KeyEvent.ACTION_UP, mDisplayId);
            }
        }

        @SuppressLint("MissingPermission")
        private void sendBackEvent(int action, int displayId) {
            final long when = SystemClock.uptimeMillis();
            final KeyEvent ev = new KeyEvent(when, when, action, KeyEvent.KEYCODE_BACK,
                    0 /* repeat */, 0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0 /* scancode */, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);

            ev.setDisplayId(displayId);
            if (!mContext.getSystemService(InputManager.class)
                    .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
                Log.e(TAG, "Inject input event fail");
            }
        }
    }
}
