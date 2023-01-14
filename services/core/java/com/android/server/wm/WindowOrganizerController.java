/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm;

import static android.Manifest.permission.START_TASKS_FROM_RECENTS;
import static android.app.ActivityManager.isStartResultSuccessful;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOW_CONFIG_BOUNDS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.TaskFragmentOperation.OP_TYPE_SET_ANIMATION_PARAMS;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_ADD_RECT_INSETS_PROVIDER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_CHILDREN_TASKS_REPARENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_DELETE_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_FINISH_ACTIVITY;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_LAUNCH_TASK;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_INSETS_PROVIDER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REPARENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REPARENT_ACTIVITY_TO_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REPARENT_CHILDREN;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REQUEST_FOCUS_ON_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_RESTORE_TRANSIENT_ORDER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_ADJACENT_ROOTS;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_ADJACENT_TASK_FRAGMENTS;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_ALWAYS_ON_TOP;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_COMPANION_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ADJACENT_FLAG_ROOT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_REPARENT_LEAF_TASK_IF_RELAUNCH;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_START_ACTIVITY_IN_TASK_FRAGMENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_START_SHORTCUT;

import static com.android.internal.protolog.ProtoLogGroup.WM_DEBUG_WINDOW_ORGANIZER;
import static com.android.server.wm.ActivityTaskManagerService.LAYOUT_REASON_CONFIG_CHANGED;
import static com.android.server.wm.ActivityTaskManagerService.enforceTaskPermission;
import static com.android.server.wm.ActivityTaskSupervisor.PRESERVE_WINDOWS;
import static com.android.server.wm.DragResizeMode.DRAG_RESIZE_MODE_FREEFORM;
import static com.android.server.wm.Task.FLAG_FORCE_HIDDEN_FOR_PINNED_TASK;
import static com.android.server.wm.Task.FLAG_FORCE_HIDDEN_FOR_TASK_ORG;
import static com.android.server.wm.TaskFragment.EMBEDDING_ALLOWED;
import static com.android.server.wm.WindowContainer.POSITION_BOTTOM;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AndroidRuntimeException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IDisplayAreaOrganizerController;
import android.window.ITaskFragmentOrganizer;
import android.window.ITaskFragmentOrganizerController;
import android.window.ITaskOrganizerController;
import android.window.ITransitionMetricsReporter;
import android.window.ITransitionPlayer;
import android.window.IWindowContainerTransactionCallback;
import android.window.IWindowOrganizerController;
import android.window.TaskFragmentAnimationParams;
import android.window.TaskFragmentCreationParams;
import android.window.TaskFragmentOperation;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLogGroup;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.function.pooled.PooledConsumer;
import com.android.internal.util.function.pooled.PooledLambda;
import com.android.server.LocalServices;
import com.android.server.pm.LauncherAppsService.LauncherAppsServiceInternal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Server side implementation for the interface for organizing windows
 * @see android.window.WindowOrganizer
 */
class WindowOrganizerController extends IWindowOrganizerController.Stub
        implements BLASTSyncEngine.TransactionReadyListener {

    private static final String TAG = "WindowOrganizerController";

    /** Flag indicating that an applied transaction may have effected lifecycle */
    private static final int TRANSACT_EFFECTS_CLIENT_CONFIG = 1;
    private static final int TRANSACT_EFFECTS_LIFECYCLE = 1 << 1;

    /**
     * Masks specifying which configurations task-organizers can control. Incoming transactions
     * will be filtered to only include these.
     */
    static final int CONTROLLABLE_CONFIGS = ActivityInfo.CONFIG_WINDOW_CONFIGURATION
            | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE | ActivityInfo.CONFIG_SCREEN_SIZE
            | ActivityInfo.CONFIG_LAYOUT_DIRECTION;
    static final int CONTROLLABLE_WINDOW_CONFIGS = WINDOW_CONFIG_BOUNDS
            | WindowConfiguration.WINDOW_CONFIG_APP_BOUNDS;

    private final ActivityTaskManagerService mService;
    private final WindowManagerGlobalLock mGlobalLock;

    private final HashMap<Integer, IWindowContainerTransactionCallback>
            mTransactionCallbacksByPendingSyncId = new HashMap();

    final TaskOrganizerController mTaskOrganizerController;
    final DisplayAreaOrganizerController mDisplayAreaOrganizerController;
    final TaskFragmentOrganizerController mTaskFragmentOrganizerController;

    TransitionController mTransitionController;
    /**
     * A Map which manages the relationship between
     * {@link TaskFragmentCreationParams#getFragmentToken()} and {@link TaskFragment}
     */
    @VisibleForTesting
    final ArrayMap<IBinder, TaskFragment> mLaunchTaskFragments = new ArrayMap<>();

    private final Rect mTmpBounds0 = new Rect();
    private final Rect mTmpBounds1 = new Rect();

    WindowOrganizerController(ActivityTaskManagerService atm) {
        mService = atm;
        mGlobalLock = atm.mGlobalLock;
        mTaskOrganizerController = new TaskOrganizerController(mService);
        mDisplayAreaOrganizerController = new DisplayAreaOrganizerController(mService);
        mTaskFragmentOrganizerController = new TaskFragmentOrganizerController(atm, this);
    }

    void setWindowManager(WindowManagerService wms) {
        mTransitionController = new TransitionController(mService, wms.mTaskSnapshotController,
                wms.mTransitionTracer);
        mTransitionController.registerLegacyListener(wms.mActivityManagerAppTransitionNotifier);
    }

    TransitionController getTransitionController() {
        return mTransitionController;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            throw ActivityTaskManagerService.logAndRethrowRuntimeExceptionOnTransact(TAG, e);
        }
    }

    @Override
    public void applyTransaction(WindowContainerTransaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Null transaction passed to applyTransaction");
        }
        enforceTaskPermission("applyTransaction()");
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mGlobalLock) {
                applyTransaction(t, -1 /*syncId*/, null /*transition*/, caller);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public int applySyncTransaction(WindowContainerTransaction t,
            IWindowContainerTransactionCallback callback) {
        if (t == null) {
            throw new IllegalArgumentException("Null transaction passed to applySyncTransaction");
        }
        enforceTaskPermission("applySyncTransaction()");
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mGlobalLock) {
                if (callback == null) {
                    applyTransaction(t, -1 /* syncId*/, null /*transition*/, caller);
                    return -1;
                }

                /**
                 * If callback is non-null we are looking to synchronize this transaction by
                 * collecting all the results in to a SurfaceFlinger transaction and then delivering
                 * that to the given transaction ready callback. See {@link BLASTSyncEngine} for the
                 * details of the operation. But at a high level we create a sync operation with a
                 * given ID and an associated callback. Then we notify each WindowContainer in this
                 * WindowContainer transaction that it is participating in a sync operation with
                 * that ID. Once everything is notified we tell the BLASTSyncEngine "setSyncReady"
                 * which means that we have added everything to the set. At any point after this,
                 * all the WindowContainers will eventually finish applying their changes and notify
                 * the BLASTSyncEngine which will deliver the Transaction to the callback.
                 */
                final BLASTSyncEngine.SyncGroup syncGroup = prepareSyncWithOrganizer(callback);
                final int syncId = syncGroup.mSyncId;
                if (!mService.mWindowManager.mSyncEngine.hasActiveSync()) {
                    mService.mWindowManager.mSyncEngine.startSyncSet(syncGroup);
                    applyTransaction(t, syncId, null /*transition*/, caller);
                    setSyncReady(syncId);
                } else {
                    // Because the BLAST engine only supports one sync at a time, queue the
                    // transaction.
                    mService.mWindowManager.mSyncEngine.queueSyncSet(
                            () -> mService.mWindowManager.mSyncEngine.startSyncSet(syncGroup),
                            () -> {
                                applyTransaction(t, syncId, null /*transition*/, caller);
                                setSyncReady(syncId);
                            });
                }
                return syncId;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public IBinder startNewTransition(int type, @Nullable WindowContainerTransaction t) {
        return startTransition(type, null /* transitionToken */, t);
    }

    @Override
    public void startTransition(@NonNull IBinder transitionToken,
            @Nullable WindowContainerTransaction t) {
        startTransition(-1 /* unused type */, transitionToken, t);
    }

    private IBinder startTransition(@WindowManager.TransitionType int type,
            @Nullable IBinder transitionToken, @Nullable WindowContainerTransaction t) {
        enforceTaskPermission("startTransition()");
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mGlobalLock) {
                Transition transition = Transition.fromBinder(transitionToken);
                if (mTransitionController.getTransitionPlayer() == null && transition == null) {
                    Slog.w(TAG, "Using shell transitions API for legacy transitions.");
                    if (t == null) {
                        throw new IllegalArgumentException("Can't use legacy transitions in"
                                + " compatibility mode with no WCT.");
                    }
                    applyTransaction(t, -1 /* syncId */, null, caller);
                    return null;
                }
                // In cases where transition is already provided, the "readiness lifecycle" of the
                // transition is determined outside of this transaction. However, if this is a
                // direct call from shell, the entire transition lifecycle is contained in the
                // provided transaction and thus we can setReady immediately after apply.
                final boolean needsSetReady = transition == null && t != null;
                final WindowContainerTransaction wct =
                        t != null ? t : new WindowContainerTransaction();
                if (transition == null) {
                    if (type < 0) {
                        throw new IllegalArgumentException("Can't create transition with no type");
                    }
                    // If there is already a collecting transition, queue up a new transition and
                    // return that. The actual start and apply will then be deferred until that
                    // transition starts collecting. This should almost never happen except during
                    // tests.
                    if (mService.mWindowManager.mSyncEngine.hasActiveSync()) {
                        Slog.w(TAG, "startTransition() while one is already collecting.");
                        final Transition nextTransition = new Transition(type, 0 /* flags */,
                                mTransitionController, mService.mWindowManager.mSyncEngine);
                        ProtoLog.v(ProtoLogGroup.WM_DEBUG_WINDOW_TRANSITIONS,
                                "Creating Pending Transition: %s", nextTransition);
                        mService.mWindowManager.mSyncEngine.queueSyncSet(
                                // Make sure to collect immediately to prevent another transition
                                // from sneaking in before it. Note: moveToCollecting internally
                                // calls startSyncSet.
                                () -> mTransitionController.moveToCollecting(nextTransition),
                                () -> {
                                    nextTransition.start();
                                    applyTransaction(wct, -1 /*syncId*/, nextTransition, caller);
                                    if (needsSetReady) {
                                        nextTransition.setAllReady();
                                    }
                                });
                        return nextTransition.getToken();
                    }
                    transition = mTransitionController.createTransition(type);
                }
                transition.start();
                applyTransaction(wct, -1 /*syncId*/, transition, caller);
                if (needsSetReady) {
                    transition.setAllReady();
                }
                return transition.getToken();
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public int startLegacyTransition(int type, @NonNull RemoteAnimationAdapter adapter,
            @NonNull IWindowContainerTransactionCallback callback,
            @NonNull WindowContainerTransaction t) {
        enforceTaskPermission("startLegacyTransition()");
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        int syncId;
        try {
            synchronized (mGlobalLock) {
                if (type < 0) {
                    throw new IllegalArgumentException("Can't create transition with no type");
                }
                if (mTransitionController.getTransitionPlayer() != null) {
                    throw new IllegalArgumentException("Can't use legacy transitions in"
                            + " when shell transitions are enabled.");
                }
                final DisplayContent dc =
                        mService.mRootWindowContainer.getDisplayContent(DEFAULT_DISPLAY);
                if (dc.mAppTransition.isTransitionSet()) {
                    // a transition already exists, so the callback probably won't be called.
                    return -1;
                }
                adapter.setCallingPidUid(caller.mPid, caller.mUid);
                dc.prepareAppTransition(type);
                dc.mAppTransition.overridePendingAppTransitionRemote(adapter, true /* sync */,
                        false /* isActivityEmbedding */);
                syncId = startSyncWithOrganizer(callback);
                applyTransaction(t, syncId, null /* transition */, caller);
                setSyncReady(syncId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return syncId;
    }

    @Override
    public int finishTransition(@NonNull IBinder transitionToken,
            @Nullable WindowContainerTransaction t,
            @Nullable IWindowContainerTransactionCallback callback) {
        enforceTaskPermission("finishTransition()");
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mGlobalLock) {
                int syncId = -1;
                if (t != null && callback != null) {
                    syncId = startSyncWithOrganizer(callback);
                }
                final Transition transition = Transition.fromBinder(transitionToken);
                // apply the incoming transaction before finish in case it alters the visibility
                // of the participants.
                if (t != null) {
                    applyTransaction(t, syncId, null /*transition*/, caller, transition);
                }
                getTransitionController().finishTransition(transitionToken);
                if (syncId >= 0) {
                    setSyncReady(syncId);
                }
                return syncId;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * Applies the {@link WindowContainerTransaction} as a request from
     * {@link android.window.TaskFragmentOrganizer}.
     *
     * @param wct   {@link WindowContainerTransaction} to apply.
     * @param type  {@link WindowManager.TransitionType} if it needs to start a new transition.
     * @param shouldApplyIndependently  If {@code true}, the {@code wct} will request a new
     *                                  transition, which will be queued until the sync engine is
     *                                  free if there is any other active sync. If {@code false},
     *                                  the {@code wct} will be directly applied to the active sync.
     */
    void applyTaskFragmentTransactionLocked(@NonNull WindowContainerTransaction wct,
            @WindowManager.TransitionType int type, boolean shouldApplyIndependently) {
        enforceTaskFragmentOrganizerPermission("applyTaskFragmentTransaction()",
                Objects.requireNonNull(wct.getTaskFragmentOrganizer()),
                Objects.requireNonNull(wct));
        final CallerInfo caller = new CallerInfo();
        final long ident = Binder.clearCallingIdentity();
        try {
            if (mTransitionController.getTransitionPlayer() == null) {
                // No need to worry about transition when Shell transition is not enabled.
                applyTransaction(wct, -1 /* syncId */, null /* transition */, caller);
                return;
            }

            if (!mService.mWindowManager.mSyncEngine.hasActiveSync()) {
                // Sync is for either transition or applySyncTransaction(). We don't support
                // multiple sync at the same time because it may cause conflict.
                // Create a new transition when there is no active sync to collect the changes.
                final Transition transition = mTransitionController.createTransition(type);
                applyTransaction(wct, -1 /* syncId */, transition, caller);
                mTransitionController.requestStartTransition(transition, null /* startTask */,
                        null /* remoteTransition */, null /* displayChange */);
                return;
            }

            if (!shouldApplyIndependently) {
                // Although there is an active sync, we want to apply the transaction now.
                // TODO(b/232042367) Redesign the organizer update on activity callback so that we
                // we will know about the transition explicitly.
                final Transition transition = mTransitionController.getCollectingTransition();
                if (transition == null) {
                    // This should rarely happen, and we should try to avoid using
                    // {@link #applySyncTransaction} with Shell transition.
                    // We still want to apply and merge the transaction to the active sync
                    // because {@code shouldApplyIndependently} is {@code false}.
                    ProtoLog.w(ProtoLogGroup.WM_DEBUG_WINDOW_TRANSITIONS,
                            "TaskFragmentTransaction changes are not collected in transition"
                                    + " because there is an ongoing sync for"
                                    + " applySyncTransaction().");
                }
                applyTransaction(wct, -1 /* syncId */, transition, caller);
                return;
            }

            // It is ok to queue the WCT until the sync engine is free.
            final Transition nextTransition = new Transition(type, 0 /* flags */,
                    mTransitionController, mService.mWindowManager.mSyncEngine);
            ProtoLog.v(ProtoLogGroup.WM_DEBUG_WINDOW_TRANSITIONS,
                    "Creating Pending Transition for TaskFragment: %s", nextTransition);
            mService.mWindowManager.mSyncEngine.queueSyncSet(
                    // Make sure to collect immediately to prevent another transition
                    // from sneaking in before it. Note: moveToCollecting internally
                    // calls startSyncSet.
                    () -> mTransitionController.moveToCollecting(nextTransition),
                    () -> {
                        if (mTaskFragmentOrganizerController.isValidTransaction(wct)) {
                            applyTransaction(wct, -1 /*syncId*/, nextTransition, caller);
                            mTransitionController.requestStartTransition(nextTransition,
                                    null /* startTask */, null /* remoteTransition */,
                                    null /* displayChange */);
                        } else {
                            nextTransition.abort();
                        }
                    });
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void applyTransaction(@NonNull WindowContainerTransaction t, int syncId,
            @Nullable Transition transition, @NonNull CallerInfo caller) {
        applyTransaction(t, syncId, transition, caller, null /* finishTransition */);
    }

    /**
     * @param syncId If non-null, this will be a sync-transaction.
     * @param transition A transition to collect changes into.
     * @param caller Info about the calling process.
     * @param finishTransition The transition that is currently being finished.
     */
    private void applyTransaction(@NonNull WindowContainerTransaction t, int syncId,
            @Nullable Transition transition, @NonNull CallerInfo caller,
            @Nullable Transition finishTransition) {
        int effects = 0;
        ProtoLog.v(WM_DEBUG_WINDOW_ORGANIZER, "Apply window transaction, syncId=%d", syncId);
        mService.deferWindowLayout();
        mService.mTaskSupervisor.setDeferRootVisibilityUpdate(true /* deferUpdate */);
        try {
            if (transition != null && transition.applyDisplayChangeIfNeeded()) {
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
            }
            final List<WindowContainerTransaction.HierarchyOp> hops = t.getHierarchyOps();
            final int hopSize = hops.size();
            ArraySet<WindowContainer> haveConfigChanges = new ArraySet<>();
            Iterator<Map.Entry<IBinder, WindowContainerTransaction.Change>> entries =
                    t.getChanges().entrySet().iterator();
            while (entries.hasNext()) {
                final Map.Entry<IBinder, WindowContainerTransaction.Change> entry = entries.next();
                final WindowContainer wc = WindowContainer.fromBinder(entry.getKey());
                if (wc == null || !wc.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on detached container: " + wc);
                    continue;
                }
                // Make sure we add to the syncSet before performing
                // operations so we don't end up splitting effects between the WM
                // pending transaction and the BLASTSync transaction.
                if (syncId >= 0) {
                    addToSyncSet(syncId, wc);
                }
                if (transition != null) transition.collect(wc);

                if ((entry.getValue().getChangeMask()
                        & WindowContainerTransaction.Change.CHANGE_FORCE_NO_PIP) != 0) {
                    // Disable entering pip (eg. when recents pretends to finish itself)
                    if (finishTransition != null) {
                        finishTransition.setCanPipOnFinish(false /* canPipOnFinish */);
                    } else if (transition != null) {
                        transition.setCanPipOnFinish(false /* canPipOnFinish */);
                    }
                }
                // A bit hacky, but we need to detect "remove PiP" so that we can "wrap" the
                // setWindowingMode call in force-hidden.
                boolean forceHiddenForPip = false;
                if (wc.asTask() != null && wc.inPinnedWindowingMode()
                        && entry.getValue().getWindowingMode() == WINDOWING_MODE_UNDEFINED) {
                    // We are in pip and going to undefined. Now search hierarchy ops to determine
                    // whether we are removing pip or expanding pip.
                    for (int i = 0; i < hopSize; ++i) {
                        final WindowContainerTransaction.HierarchyOp hop = hops.get(i);
                        if (hop.getType() != HIERARCHY_OP_TYPE_REORDER) continue;
                        final WindowContainer hopWc = WindowContainer.fromBinder(
                                hop.getContainer());
                        if (!wc.equals(hopWc)) continue;
                        forceHiddenForPip = !hop.getToTop();
                    }
                }
                if (forceHiddenForPip) {
                    wc.asTask().setForceHidden(FLAG_FORCE_HIDDEN_FOR_PINNED_TASK, true /* set */);
                }

                int containerEffect = applyWindowContainerChange(wc, entry.getValue(),
                        t.getErrorCallbackToken());
                effects |= containerEffect;

                if (forceHiddenForPip) {
                    wc.asTask().setForceHidden(FLAG_FORCE_HIDDEN_FOR_PINNED_TASK, false /* set */);
                }

                // Lifecycle changes will trigger ensureConfig for everything.
                if ((effects & TRANSACT_EFFECTS_LIFECYCLE) == 0
                        && (containerEffect & TRANSACT_EFFECTS_CLIENT_CONFIG) != 0) {
                    haveConfigChanges.add(wc);
                }
            }
            // Hierarchy changes
            if (hopSize > 0) {
                final boolean isInLockTaskMode = mService.isInLockTaskMode();
                for (int i = 0; i < hopSize; ++i) {
                    effects |= applyHierarchyOp(hops.get(i), effects, syncId, transition,
                            isInLockTaskMode, caller, t.getErrorCallbackToken(),
                            t.getTaskFragmentOrganizer(), finishTransition);
                }
            }
            // Queue-up bounds-change transactions for tasks which are now organized. Do
            // this after hierarchy ops so we have the final organized state.
            entries = t.getChanges().entrySet().iterator();
            while (entries.hasNext()) {
                final Map.Entry<IBinder, WindowContainerTransaction.Change> entry = entries.next();
                final WindowContainer wc = WindowContainer.fromBinder(entry.getKey());
                if (wc == null || !wc.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on detached container: " + wc);
                    continue;
                }
                final Task task = wc.asTask();
                final Rect surfaceBounds = entry.getValue().getBoundsChangeSurfaceBounds();
                if (task == null || !task.isAttached() || surfaceBounds == null) {
                    continue;
                }
                if (!task.isOrganized()) {
                    final Task parent = task.getParent() != null ? task.getParent().asTask() : null;
                    // Also allow direct children of created-by-organizer tasks to be
                    // controlled. In the future, these will become organized anyways.
                    if (parent == null || !parent.mCreatedByOrganizer) {
                        throw new IllegalArgumentException(
                                "Can't manipulate non-organized task surface " + task);
                    }
                }
                final SurfaceControl.Transaction sft = new SurfaceControl.Transaction();
                final SurfaceControl sc = task.getSurfaceControl();
                sft.setPosition(sc, surfaceBounds.left, surfaceBounds.top);
                if (surfaceBounds.isEmpty()) {
                    sft.setWindowCrop(sc, null);
                } else {
                    sft.setWindowCrop(sc, surfaceBounds.width(), surfaceBounds.height());
                }
                task.setMainWindowSizeChangeTransaction(sft);
            }
            if ((effects & TRANSACT_EFFECTS_LIFECYCLE) != 0) {
                mService.mTaskSupervisor.setDeferRootVisibilityUpdate(false /* deferUpdate */);
                // Already calls ensureActivityConfig
                mService.mRootWindowContainer.ensureActivitiesVisible(null, 0, PRESERVE_WINDOWS);
                mService.mRootWindowContainer.resumeFocusedTasksTopActivities();
            } else if ((effects & TRANSACT_EFFECTS_CLIENT_CONFIG) != 0) {
                final PooledConsumer f = PooledLambda.obtainConsumer(
                        ActivityRecord::ensureActivityConfiguration,
                        PooledLambda.__(ActivityRecord.class), 0,
                        true /* preserveWindow */);
                try {
                    for (int i = haveConfigChanges.size() - 1; i >= 0; --i) {
                        haveConfigChanges.valueAt(i).forAllActivities(f);
                    }
                } finally {
                    f.recycle();
                }
            }

            if ((effects & TRANSACT_EFFECTS_CLIENT_CONFIG) != 0) {
                mService.addWindowLayoutReasons(LAYOUT_REASON_CONFIG_CHANGED);
            }
        } finally {
            mService.mTaskSupervisor.setDeferRootVisibilityUpdate(false /* deferUpdate */);
            mService.continueWindowLayout();
        }
    }

    private int applyChanges(WindowContainer<?> container,
            WindowContainerTransaction.Change change, @Nullable IBinder errorCallbackToken) {
        // The "client"-facing API should prevent bad changes; however, just in case, sanitize
        // masks here.
        final int configMask = change.getConfigSetMask() & CONTROLLABLE_CONFIGS;
        final int windowMask = change.getWindowSetMask() & CONTROLLABLE_WINDOW_CONFIGS;
        int effects = 0;
        final int windowingMode = change.getWindowingMode();
        if (configMask != 0) {

            adjustBoundsForMinDimensionsIfNeeded(container, change, errorCallbackToken);

            if (windowingMode > -1 && windowingMode != container.getWindowingMode()) {
                // Special handling for when we are setting a windowingMode in the same transaction.
                // Setting the windowingMode is going to call onConfigurationChanged so we don't
                // need it called right now. Additionally, some logic requires everything in the
                // configuration to change at the same time (ie. surface-freezer requires bounds
                // and mode to change at the same time).
                final Configuration c = container.getRequestedOverrideConfiguration();
                c.setTo(change.getConfiguration(), configMask, windowMask);
            } else {
                final Configuration c =
                        new Configuration(container.getRequestedOverrideConfiguration());
                c.setTo(change.getConfiguration(), configMask, windowMask);
                container.onRequestedOverrideConfigurationChanged(c);
            }
            effects |= TRANSACT_EFFECTS_CLIENT_CONFIG;
            if (windowMask != 0 && container.isEmbedded()) {
                // Changing bounds of the embedded TaskFragments may result in lifecycle changes.
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
            }
        }
        if ((change.getChangeMask() & WindowContainerTransaction.Change.CHANGE_FOCUSABLE) != 0) {
            if (container.setFocusable(change.getFocusable())) {
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
            }
        }

        if (windowingMode > -1) {
            if (mService.isInLockTaskMode()
                    && WindowConfiguration.inMultiWindowMode(windowingMode)) {
                throw new UnsupportedOperationException("Not supported to set multi-window"
                        + " windowing mode during locked task mode.");
            }

            if (windowingMode == WindowConfiguration.WINDOWING_MODE_PINNED) {
                // Do not directly put the container into PINNED mode as it may not support it or
                // the app may not want to enter it. Instead, send a signal to request PIP
                // mode to the app if they wish to support it below in #applyTaskChanges.
                return effects;
            }

            final int prevMode = container.getWindowingMode();
            container.setWindowingMode(windowingMode);
            if (prevMode != container.getWindowingMode()) {
                // The activity in the container may become focusable or non-focusable due to
                // windowing modes changes (such as entering or leaving pinned windowing mode),
                // so also apply the lifecycle effects to this transaction.
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
            }
        }
        return effects;
    }

    private void adjustBoundsForMinDimensionsIfNeeded(WindowContainer<?> container,
            WindowContainerTransaction.Change change, @Nullable IBinder errorCallbackToken) {
        final TaskFragment taskFragment = container.asTaskFragment();
        if (taskFragment == null || !taskFragment.isEmbedded()) {
            return;
        }
        if ((change.getWindowSetMask() & WINDOW_CONFIG_BOUNDS) == 0) {
            return;
        }
        final WindowConfiguration winConfig = change.getConfiguration().windowConfiguration;
        final Rect bounds = winConfig.getBounds();
        final Point minDimensions = taskFragment.calculateMinDimension();
        if (bounds.width() < minDimensions.x || bounds.height() < minDimensions.y) {
            sendMinimumDimensionViolation(taskFragment, minDimensions, errorCallbackToken,
                    "setBounds:" + bounds);
            // Sets the bounds to match parent bounds.
            winConfig.setBounds(new Rect());
        }
    }

    private int applyTaskChanges(Task tr, WindowContainerTransaction.Change c) {
        int effects = applyChanges(tr, c, null /* errorCallbackToken */);
        final SurfaceControl.Transaction t = c.getBoundsChangeTransaction();

        if ((c.getChangeMask() & WindowContainerTransaction.Change.CHANGE_HIDDEN) != 0) {
            if (tr.setForceHidden(FLAG_FORCE_HIDDEN_FOR_TASK_ORG, c.getHidden())) {
                effects = TRANSACT_EFFECTS_LIFECYCLE;
            }
        }

        if ((c.getChangeMask()
                & WindowContainerTransaction.Change.CHANGE_FORCE_TRANSLUCENT) != 0) {
            tr.setForceTranslucent(c.getForceTranslucent());
            effects = TRANSACT_EFFECTS_LIFECYCLE;
        }

        if ((c.getChangeMask() & WindowContainerTransaction.Change.CHANGE_DRAG_RESIZING) != 0) {
            tr.setDragResizing(c.getDragResizing(), DRAG_RESIZE_MODE_FREEFORM);
        }

        final int childWindowingMode = c.getActivityWindowingMode();
        if (childWindowingMode > -1) {
            tr.setActivityWindowingMode(childWindowingMode);
        }

        if (t != null) {
            tr.setMainWindowSizeChangeTransaction(t);
        }

        Rect enterPipBounds = c.getEnterPipBounds();
        if (enterPipBounds != null) {
            tr.mDisplayContent.mPinnedTaskController.setEnterPipBounds(enterPipBounds);
        }

        if (c.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_PINNED
                && !tr.inPinnedWindowingMode()) {
            final ActivityRecord activity = tr.getTopNonFinishingActivity();
            if (activity != null) {
                final boolean lastSupportsEnterPipOnTaskSwitch =
                        activity.supportsEnterPipOnTaskSwitch;
                // Temporarily force enable enter PIP on task switch so that PIP is requested
                // regardless of whether the activity is resumed or paused.
                activity.supportsEnterPipOnTaskSwitch = true;
                boolean canEnterPip = activity.checkEnterPictureInPictureState(
                        "applyTaskChanges", true /* beforeStopping */);
                if (canEnterPip) {
                    canEnterPip = mService.mActivityClientController
                            .requestPictureInPictureMode(activity);
                }
                if (!canEnterPip) {
                    // Restore the flag to its previous state when the activity cannot enter PIP.
                    activity.supportsEnterPipOnTaskSwitch = lastSupportsEnterPipOnTaskSwitch;
                }
            }
        }

        return effects;
    }

    private int applyDisplayAreaChanges(DisplayArea displayArea,
            WindowContainerTransaction.Change c) {
        final int[] effects = new int[1];
        effects[0] = applyChanges(displayArea, c, null /* errorCallbackToken */);

        if ((c.getChangeMask()
                & WindowContainerTransaction.Change.CHANGE_IGNORE_ORIENTATION_REQUEST) != 0) {
            if (displayArea.setIgnoreOrientationRequest(c.getIgnoreOrientationRequest())) {
                effects[0] |= TRANSACT_EFFECTS_LIFECYCLE;
            }
        }

        displayArea.forAllTasks(task -> {
            Task tr = (Task) task;
            if ((c.getChangeMask() & WindowContainerTransaction.Change.CHANGE_HIDDEN) != 0) {
                if (tr.setForceHidden(FLAG_FORCE_HIDDEN_FOR_TASK_ORG, c.getHidden())) {
                    effects[0] |= TRANSACT_EFFECTS_LIFECYCLE;
                }
            }
        });

        return effects[0];
    }

    private int applyTaskFragmentChanges(@NonNull TaskFragment taskFragment,
            @NonNull WindowContainerTransaction.Change c, @Nullable IBinder errorCallbackToken) {
        if (taskFragment.isEmbeddedTaskFragmentInPip()) {
            // No override from organizer for embedded TaskFragment in a PIP Task.
            return 0;
        }

        // When the TaskFragment is resized, we may want to create a change transition for it, for
        // which we want to defer the surface update until we determine whether or not to start
        // change transition.
        mTmpBounds0.set(taskFragment.getBounds());
        mTmpBounds1.set(taskFragment.getRelativeEmbeddedBounds());
        taskFragment.deferOrganizedTaskFragmentSurfaceUpdate();
        final int effects = applyChanges(taskFragment, c, errorCallbackToken);
        taskFragment.updateRelativeEmbeddedBounds();
        if (taskFragment.shouldStartChangeTransition(mTmpBounds0, mTmpBounds1)) {
            taskFragment.initializeChangeTransition(mTmpBounds0);
        }
        taskFragment.continueOrganizedTaskFragmentSurfaceUpdate();
        return effects;
    }

    private int applyHierarchyOp(WindowContainerTransaction.HierarchyOp hop, int effects,
            int syncId, @Nullable Transition transition, boolean isInLockTaskMode,
            @NonNull CallerInfo caller, @Nullable IBinder errorCallbackToken,
            @Nullable ITaskFragmentOrganizer organizer, @Nullable Transition finishTransition) {
        final int type = hop.getType();
        switch (type) {
            case HIERARCHY_OP_TYPE_REMOVE_TASK: {
                final WindowContainer wc = WindowContainer.fromBinder(hop.getContainer());
                final Task task = wc != null ? wc.asTask() : null;
                task.remove(true, "Applying remove task Hierarchy Op");
                break;
            }
            case HIERARCHY_OP_TYPE_SET_LAUNCH_ROOT: {
                final WindowContainer wc = WindowContainer.fromBinder(hop.getContainer());
                final Task task = wc != null ? wc.asTask() : null;
                if (task != null) {
                    task.getDisplayArea().setLaunchRootTask(task,
                            hop.getWindowingModes(), hop.getActivityTypes());
                } else {
                    throw new IllegalArgumentException("Cannot set non-task as launch root: " + wc);
                }
                break;
            }
            case HIERARCHY_OP_TYPE_SET_LAUNCH_ADJACENT_FLAG_ROOT: {
                final WindowContainer wc = WindowContainer.fromBinder(hop.getContainer());
                final Task task = wc != null ? wc.asTask() : null;
                final boolean clearRoot = hop.getToTop();
                if (task == null) {
                    throw new IllegalArgumentException("Cannot set non-task as launch root: " + wc);
                } else if (!task.mCreatedByOrganizer) {
                    throw new UnsupportedOperationException(
                            "Cannot set non-organized task as adjacent flag root: " + wc);
                } else if (task.getAdjacentTaskFragment() == null && !clearRoot) {
                    throw new UnsupportedOperationException(
                            "Cannot set non-adjacent task as adjacent flag root: " + wc);
                }

                task.getDisplayArea().setLaunchAdjacentFlagRootTask(clearRoot ? null : task);
                break;
            }
            case HIERARCHY_OP_TYPE_SET_ADJACENT_ROOTS: {
                effects |= setAdjacentRootsHierarchyOp(hop);
                break;
            }
            case HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT: {
                final TaskFragmentCreationParams taskFragmentCreationOptions =
                        hop.getTaskFragmentCreationOptions();
                createTaskFragment(taskFragmentCreationOptions, errorCallbackToken, caller,
                        transition);
                break;
            }
            case HIERARCHY_OP_TYPE_DELETE_TASK_FRAGMENT: {
                final WindowContainer wc = WindowContainer.fromBinder(hop.getContainer());
                if (wc == null || !wc.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on unknown or detached container: " + wc);
                    break;
                }
                final TaskFragment taskFragment = wc.asTaskFragment();
                if (taskFragment == null || taskFragment.asTask() != null) {
                    throw new IllegalArgumentException(
                            "Can only delete organized TaskFragment, but not Task.");
                }
                if (isInLockTaskMode) {
                    final ActivityRecord bottomActivity = taskFragment.getActivity(
                            a -> !a.finishing, false /* traverseTopToBottom */);
                    if (bottomActivity != null
                            && mService.getLockTaskController().activityBlockedFromFinish(
                                    bottomActivity)) {
                        Slog.w(TAG, "Skip removing TaskFragment due in lock task mode.");
                        sendTaskFragmentOperationFailure(organizer, errorCallbackToken,
                                taskFragment, type, new IllegalStateException(
                                        "Not allow to delete task fragment in lock task mode."));
                        break;
                    }
                }
                effects |= deleteTaskFragment(taskFragment, organizer, errorCallbackToken,
                        transition);
                break;
            }
            case HIERARCHY_OP_TYPE_START_ACTIVITY_IN_TASK_FRAGMENT: {
                final IBinder fragmentToken = hop.getContainer();
                final TaskFragment tf = mLaunchTaskFragments.get(fragmentToken);
                if (tf == null) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to operate with invalid fragment token");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, tf, type,
                            exception);
                    break;
                }
                if (tf.isEmbeddedTaskFragmentInPip()) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to start activity in PIP TaskFragment");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, tf, type,
                            exception);
                    break;
                }
                final Intent activityIntent = hop.getActivityIntent();
                final Bundle activityOptions = hop.getLaunchOptions();
                final int result = mService.getActivityStartController()
                        .startActivityInTaskFragment(tf, activityIntent, activityOptions,
                                hop.getCallingActivity(), caller.mUid, caller.mPid,
                                errorCallbackToken);
                if (!isStartResultSuccessful(result)) {
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, tf, type,
                            convertStartFailureToThrowable(result, activityIntent));
                } else {
                    effects |= TRANSACT_EFFECTS_LIFECYCLE;
                }
                break;
            }
            case HIERARCHY_OP_TYPE_REPARENT_ACTIVITY_TO_TASK_FRAGMENT: {
                final IBinder fragmentToken = hop.getNewParent();
                final IBinder activityToken = hop.getContainer();
                ActivityRecord activity = ActivityRecord.forTokenLocked(activityToken);
                if (activity == null) {
                    // The token may be a temporary token if the activity doesn't belong to
                    // the organizer process.
                    activity = mTaskFragmentOrganizerController
                            .getReparentActivityFromTemporaryToken(organizer, activityToken);
                }
                final TaskFragment parent = mLaunchTaskFragments.get(fragmentToken);
                if (parent == null || activity == null) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to operate with invalid fragment token or activity.");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, parent, type,
                            exception);
                    break;
                }
                if (parent.isEmbeddedTaskFragmentInPip()) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to reparent activity to PIP TaskFragment");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, parent, type,
                            exception);
                    break;
                }
                if (parent.isAllowedToEmbedActivity(activity) != EMBEDDING_ALLOWED) {
                    final Throwable exception = new SecurityException(
                            "The task fragment is not allowed to embed the given activity.");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, parent, type,
                            exception);
                    break;
                }
                if (parent.getTask() != activity.getTask()) {
                    final Throwable exception = new SecurityException("The reparented activity is"
                            + " not in the same Task as the target TaskFragment.");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, parent, type,
                            exception);
                    break;
                }

                if (transition != null) {
                    transition.collect(activity);
                    if (activity.getParent() != null) {
                        // Collect the current parent. Its visibility may change as a result of
                        // this reparenting.
                        transition.collect(activity.getParent());
                    }
                    transition.collect(parent);
                }
                activity.reparent(parent, POSITION_TOP);
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
                break;
            }
            case HIERARCHY_OP_TYPE_SET_ADJACENT_TASK_FRAGMENTS: {
                final IBinder fragmentToken = hop.getContainer();
                final IBinder adjacentFragmentToken = hop.getAdjacentRoot();
                final TaskFragment tf1 = mLaunchTaskFragments.get(fragmentToken);
                final TaskFragment tf2 = adjacentFragmentToken != null
                        ? mLaunchTaskFragments.get(adjacentFragmentToken)
                        : null;
                if (tf1 == null || (adjacentFragmentToken != null && tf2 == null)) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to set adjacent on invalid fragment tokens");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, tf1, type,
                            exception);
                    break;
                }
                if (tf1.isEmbeddedTaskFragmentInPip()
                        || (tf2 != null && tf2.isEmbeddedTaskFragmentInPip())) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to set adjacent on TaskFragment in PIP Task");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, tf1, type,
                            exception);
                    break;
                }
                tf1.setAdjacentTaskFragment(tf2);
                effects |= TRANSACT_EFFECTS_LIFECYCLE;

                // Clear the focused app if the focused app is no longer visible after reset the
                // adjacent TaskFragments.
                if (tf2 == null && tf1.getDisplayContent().mFocusedApp != null
                        && tf1.hasChild(tf1.getDisplayContent().mFocusedApp)
                        && !tf1.shouldBeVisible(null /* starting */)) {
                    tf1.getDisplayContent().setFocusedApp(null);
                }

                final Bundle bundle = hop.getLaunchOptions();
                final WindowContainerTransaction.TaskFragmentAdjacentParams adjacentParams =
                        bundle != null ? new WindowContainerTransaction.TaskFragmentAdjacentParams(
                                bundle) : null;
                if (adjacentParams == null) {
                    break;
                }

                tf1.setDelayLastActivityRemoval(
                        adjacentParams.shouldDelayPrimaryLastActivityRemoval());
                if (tf2 != null) {
                    tf2.setDelayLastActivityRemoval(
                            adjacentParams.shouldDelaySecondaryLastActivityRemoval());
                }
                break;
            }
            case HIERARCHY_OP_TYPE_REQUEST_FOCUS_ON_TASK_FRAGMENT: {
                final TaskFragment tf = mLaunchTaskFragments.get(hop.getContainer());
                if (tf == null || !tf.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on detached container: " + tf);
                    break;
                }
                final ActivityRecord curFocus = tf.getDisplayContent().mFocusedApp;
                if (curFocus != null && curFocus.getTaskFragment() == tf) {
                    Slog.d(TAG, "The requested TaskFragment already has the focus.");
                    break;
                }
                if (curFocus != null && curFocus.getTask() != tf.getTask()) {
                    Slog.d(TAG, "The Task of the requested TaskFragment doesn't have focus.");
                    break;
                }
                final ActivityRecord targetFocus = tf.getTopResumedActivity();
                if (targetFocus == null) {
                    Slog.d(TAG, "There is no resumed activity in the requested TaskFragment.");
                    break;
                }
                tf.getDisplayContent().setFocusedApp(targetFocus);
                break;
            }
            case HIERARCHY_OP_TYPE_CHILDREN_TASKS_REPARENT: {
                effects |= reparentChildrenTasksHierarchyOp(hop, transition, syncId,
                        isInLockTaskMode);
                break;
            }
            case HIERARCHY_OP_TYPE_FINISH_ACTIVITY: {
                final ActivityRecord activity = ActivityRecord.forTokenLocked(hop.getContainer());
                if (activity == null || activity.finishing) {
                    break;
                }
                if (activity.isVisible() || activity.isVisibleRequested()) {
                    // Prevent the transition from being executed too early if the activity is
                    // visible.
                    activity.finishIfPossible("finish-activity-op", false /* oomAdj */);
                } else {
                    activity.destroyIfPossible("finish-activity-op");
                }
                break;
            }
            case HIERARCHY_OP_TYPE_LAUNCH_TASK: {
                mService.mAmInternal.enforceCallingPermission(START_TASKS_FROM_RECENTS,
                        "launchTask HierarchyOp");
                final Bundle launchOpts = hop.getLaunchOptions();
                final int taskId = launchOpts.getInt(
                        WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_TASK_ID);
                launchOpts.remove(WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_TASK_ID);
                final SafeActivityOptions safeOptions =
                        SafeActivityOptions.fromBundle(launchOpts, caller.mPid, caller.mUid);
                waitAsyncStart(() -> mService.mTaskSupervisor.startActivityFromRecents(
                        caller.mPid, caller.mUid, taskId, safeOptions));
                break;
            }
            case HIERARCHY_OP_TYPE_REORDER:
            case HIERARCHY_OP_TYPE_REPARENT: {
                final WindowContainer wc = WindowContainer.fromBinder(hop.getContainer());
                if (wc == null || !wc.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on detached container: " + wc);
                    break;
                }
                // There is no use case to ask the reparent operation in lock-task mode now, so keep
                // skipping this operation as usual.
                if (isInLockTaskMode && type == HIERARCHY_OP_TYPE_REPARENT) {
                    Slog.w(TAG, "Skip applying hierarchy operation " + hop
                            + " while in lock task mode");
                    break;
                }
                if (isLockTaskModeViolation(wc.getParent(), wc.asTask(), isInLockTaskMode)) {
                    break;
                }
                if (syncId >= 0) {
                    addToSyncSet(syncId, wc);
                }
                if (transition != null) {
                    transition.collect(wc);
                    if (hop.isReparent()) {
                        if (wc.getParent() != null) {
                            // Collect the current parent. It's visibility may change as
                            // a result of this reparenting.
                            transition.collect(wc.getParent());
                        }
                        if (hop.getNewParent() != null) {
                            final WindowContainer parentWc =
                                    WindowContainer.fromBinder(hop.getNewParent());
                            if (parentWc == null) {
                                Slog.e(TAG, "Can't resolve parent window from token");
                                break;
                            }
                            transition.collect(parentWc);
                        }
                    }
                }
                effects |= sanitizeAndApplyHierarchyOp(wc, hop);
                break;
            }
            case HIERARCHY_OP_TYPE_SET_COMPANION_TASK_FRAGMENT: {
                final IBinder fragmentToken = hop.getContainer();
                final IBinder companionToken = hop.getCompanionContainer();
                final TaskFragment fragment = mLaunchTaskFragments.get(fragmentToken);
                final TaskFragment companion = companionToken != null ? mLaunchTaskFragments.get(
                        companionToken) : null;
                if (fragment == null || !fragment.isAttached()) {
                    final Throwable exception = new IllegalArgumentException(
                            "Not allowed to set companion on invalid fragment tokens");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, fragment, type,
                            exception);
                    break;
                }
                fragment.setCompanionTaskFragment(companion);
                break;
            }
            case HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION: {
                effects |= applyTaskFragmentOperation(hop, errorCallbackToken, organizer);
                break;
            }
            default: {
                // The other operations may change task order so they are skipped while in lock
                // task mode. The above operations are still allowed because they don't move
                // tasks. And it may be necessary such as clearing launch root after entering
                // lock task mode.
                if (isInLockTaskMode) {
                    Slog.w(TAG, "Skip applying hierarchy operation " + hop
                            + " while in lock task mode");
                    return effects;
                }
            }
        }

        switch (type) {
            case HIERARCHY_OP_TYPE_PENDING_INTENT: {
                String resolvedType = hop.getActivityIntent() != null
                        ? hop.getActivityIntent().resolveTypeIfNeeded(
                        mService.mContext.getContentResolver())
                        : null;

                ActivityOptions activityOptions = null;
                if (hop.getPendingIntent().isActivity()) {
                    // Set the context display id as preferred for this activity launches, so that
                    // it can land on caller's display. Or just brought the task to front at the
                    // display where it was on since it has higher preference.
                    activityOptions = hop.getLaunchOptions() != null
                            ? new ActivityOptions(hop.getLaunchOptions())
                            : ActivityOptions.makeBasic();
                    activityOptions.setCallerDisplayId(DEFAULT_DISPLAY);
                }
                final Bundle options = activityOptions != null ? activityOptions.toBundle() : null;
                waitAsyncStart(() -> mService.mAmInternal.sendIntentSender(
                        hop.getPendingIntent().getTarget(),
                        hop.getPendingIntent().getWhitelistToken(), 0 /* code */,
                        hop.getActivityIntent(), resolvedType, null /* finishReceiver */,
                        null /* requiredPermission */, options));
                break;
            }
            case HIERARCHY_OP_TYPE_START_SHORTCUT: {
                final Bundle launchOpts = hop.getLaunchOptions();
                final String callingPackage = launchOpts.getString(
                        WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_SHORTCUT_CALLING_PACKAGE);
                launchOpts.remove(
                        WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_SHORTCUT_CALLING_PACKAGE);

                final LauncherAppsServiceInternal launcherApps = LocalServices.getService(
                        LauncherAppsServiceInternal.class);

                final boolean success = launcherApps.startShortcut(caller.mUid, caller.mPid,
                        callingPackage, hop.getShortcutInfo().getPackage(), null /* featureId */,
                        hop.getShortcutInfo().getId(), null /* sourceBounds */, launchOpts,
                        hop.getShortcutInfo().getUserId());
                if (success) {
                    effects |= TRANSACT_EFFECTS_LIFECYCLE;
                }
                break;
            }
            case HIERARCHY_OP_TYPE_REPARENT_CHILDREN: {
                final WindowContainer oldParent = WindowContainer.fromBinder(hop.getContainer());
                final WindowContainer newParent = hop.getNewParent() != null
                        ? WindowContainer.fromBinder(hop.getNewParent())
                        : null;
                if (oldParent == null || oldParent.asTaskFragment() == null
                        || !oldParent.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on unknown or detached container: "
                            + oldParent);
                    break;
                }
                reparentTaskFragment(oldParent.asTaskFragment(), newParent, organizer,
                        errorCallbackToken, transition);
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
                break;
            }
            case HIERARCHY_OP_TYPE_RESTORE_TRANSIENT_ORDER: {
                if (finishTransition == null) break;
                final WindowContainer container = WindowContainer.fromBinder(hop.getContainer());
                if (container == null) break;
                final Task thisTask = container.asActivityRecord() != null
                        ? container.asActivityRecord().getTask() : container.asTask();
                if (thisTask == null) break;
                final Task restoreAt = finishTransition.getTransientLaunchRestoreTarget(container);
                if (restoreAt == null) break;
                final TaskDisplayArea taskDisplayArea = thisTask.getTaskDisplayArea();
                taskDisplayArea.moveRootTaskBehindRootTask(thisTask.getRootTask(), restoreAt);
                break;
            }
            case HIERARCHY_OP_TYPE_ADD_RECT_INSETS_PROVIDER:
                final Rect insetsProviderWindowContainer = hop.getInsetsProviderFrame();
                final WindowContainer receiverWindowContainer =
                        WindowContainer.fromBinder(hop.getContainer());
                receiverWindowContainer.addLocalRectInsetsSourceProvider(
                        insetsProviderWindowContainer, hop.getInsetsTypes());
                break;
            case HIERARCHY_OP_TYPE_REMOVE_INSETS_PROVIDER:
                WindowContainer.fromBinder(hop.getContainer())
                        .removeLocalInsetsSourceProvider(hop.getInsetsTypes());
                break;
            case HIERARCHY_OP_TYPE_SET_ALWAYS_ON_TOP: {
                final WindowContainer container = WindowContainer.fromBinder(hop.getContainer());
                if (container == null || container.asDisplayArea() == null
                        || !container.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on unknown or detached display area: "
                            + container);
                    break;
                }
                container.setAlwaysOnTop(hop.isAlwaysOnTop());
                effects |= TRANSACT_EFFECTS_LIFECYCLE;
                break;
            }
            case HIERARCHY_OP_TYPE_SET_REPARENT_LEAF_TASK_IF_RELAUNCH: {
                final WindowContainer container = WindowContainer.fromBinder(hop.getContainer());
                final Task task = container != null ? container.asTask() : null;
                if (task == null || !task.isAttached()) {
                    Slog.e(TAG, "Attempt to operate on unknown or detached container: "
                            + container);
                    break;
                }
                if (!task.mCreatedByOrganizer) {
                    throw new UnsupportedOperationException(
                            "Cannot set reparent leaf task flag on non-organized task : " + task);
                }
                if (!task.isRootTask()) {
                    throw new UnsupportedOperationException(
                            "Cannot set reparent leaf task flag on non-root task : " + task);
                }
                task.setReparentLeafTaskIfRelaunch(hop.isReparentLeafTaskIfRelaunch());
                break;
            }
        }
        return effects;
    }

    /** Applies change set through {@link WindowContainerTransaction#setTaskFragmentOperation}. */
    private int applyTaskFragmentOperation(@NonNull WindowContainerTransaction.HierarchyOp hop,
            @Nullable IBinder errorCallbackToken, @Nullable ITaskFragmentOrganizer organizer) {
        final IBinder fragmentToken = hop.getContainer();
        final TaskFragment taskFragment = mLaunchTaskFragments.get(fragmentToken);
        final TaskFragmentOperation operation = hop.getTaskFragmentOperation();
        if (operation == null) {
            final Throwable exception = new IllegalArgumentException(
                    "TaskFragmentOperation must be non-null");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, taskFragment,
                    HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION, exception);
            return 0;
        }
        final int opType = operation.getOpType();
        if (taskFragment == null || !taskFragment.isAttached()) {
            final Throwable exception = new IllegalArgumentException(
                    "Not allowed to apply operation on invalid fragment tokens opType=" + opType);
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, taskFragment,
                    HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION, exception);
            return 0;
        }

        int effect = 0;
        switch (opType) {
            case OP_TYPE_SET_ANIMATION_PARAMS: {
                final TaskFragmentAnimationParams animationParams = operation.getAnimationParams();
                if (animationParams == null) {
                    final Throwable exception = new IllegalArgumentException(
                            "TaskFragmentAnimationParams must be non-null");
                    sendTaskFragmentOperationFailure(organizer, errorCallbackToken, taskFragment,
                            HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION, exception);
                    break;
                }
                taskFragment.setAnimationParams(animationParams);
                break;
            }
            // TODO(b/263436063): move other TaskFragment related operation here.
        }
        return effect;
    }

    /** A helper method to send minimum dimension violation error to the client. */
    private void sendMinimumDimensionViolation(TaskFragment taskFragment, Point minDimensions,
            IBinder errorCallbackToken, String reason) {
        if (taskFragment == null || taskFragment.getTaskFragmentOrganizer() == null) {
            return;
        }
        final Throwable exception = new SecurityException("The task fragment's bounds:"
                + taskFragment.getBounds() + " does not satisfy minimum dimensions:"
                + minDimensions + " " + reason);
        sendTaskFragmentOperationFailure(taskFragment.getTaskFragmentOrganizer(),
                errorCallbackToken, taskFragment, -1 /* opType */, exception);
    }

    /**
     * Post and wait for the result of the activity start to prevent potential deadlock against
     * {@link WindowManagerGlobalLock}.
     */
    private void waitAsyncStart(IntSupplier startActivity) {
        final Integer[] starterResult = {null};
        mService.mH.post(() -> {
            try {
                starterResult[0] = startActivity.getAsInt();
            } catch (Throwable t) {
                starterResult[0] = ActivityManager.START_CANCELED;
                Slog.w(TAG, t);
            }
            synchronized (mGlobalLock) {
                mGlobalLock.notifyAll();
            }
        });
        while (starterResult[0] == null) {
            try {
                mGlobalLock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private int sanitizeAndApplyHierarchyOp(WindowContainer container,
            WindowContainerTransaction.HierarchyOp hop) {
        final Task task = container.asTask();
        if (task == null) {
            throw new IllegalArgumentException("Invalid container in hierarchy op");
        }
        final DisplayContent dc = task.getDisplayContent();
        if (dc == null) {
            Slog.w(TAG, "Container is no longer attached: " + task);
            return 0;
        }
        final Task as = task;

        if (hop.isReparent()) {
            final boolean isNonOrganizedRootableTask =
                    task.isRootTask() || task.getParent().asTask().mCreatedByOrganizer;
            if (isNonOrganizedRootableTask) {
                WindowContainer newParent = hop.getNewParent() == null
                        ? dc.getDefaultTaskDisplayArea()
                        : WindowContainer.fromBinder(hop.getNewParent());
                if (newParent == null) {
                    Slog.e(TAG, "Can't resolve parent window from token");
                    return 0;
                }
                if (task.getParent() != newParent) {
                    if (newParent.asTaskDisplayArea() != null) {
                        // For now, reparenting to displayarea is different from other reparents...
                        as.reparent(newParent.asTaskDisplayArea(), hop.getToTop());
                    } else if (newParent.asTask() != null) {
                        if (newParent.inMultiWindowMode() && task.isLeafTask()) {
                            if (newParent.inPinnedWindowingMode()) {
                                Slog.w(TAG, "Can't support moving a task to another PIP window..."
                                        + " newParent=" + newParent + " task=" + task);
                                return 0;
                            }
                            if (!task.supportsMultiWindowInDisplayArea(
                                    newParent.asTask().getDisplayArea())) {
                                Slog.w(TAG, "Can't support task that doesn't support multi-window"
                                        + " mode in multi-window mode... newParent=" + newParent
                                        + " task=" + task);
                                return 0;
                            }
                        }
                        task.reparent((Task) newParent,
                                hop.getToTop() ? POSITION_TOP : POSITION_BOTTOM,
                                false /*moveParents*/, "sanitizeAndApplyHierarchyOp");
                    } else {
                        throw new RuntimeException("Can only reparent task to another task or"
                                + " taskDisplayArea, but not " + newParent);
                    }
                } else {
                    final Task rootTask = (Task) (
                            (newParent != null && !(newParent instanceof TaskDisplayArea))
                                    ? newParent : task.getRootTask());
                    as.getDisplayArea().positionChildAt(
                            hop.getToTop() ? POSITION_TOP : POSITION_BOTTOM, rootTask,
                            false /* includingParents */);
                }
            } else {
                throw new RuntimeException("Reparenting leaf Tasks is not supported now. " + task);
            }
        } else {
            task.getParent().positionChildAt(
                    hop.getToTop() ? POSITION_TOP : POSITION_BOTTOM,
                    task, false /* includingParents */);
        }
        return TRANSACT_EFFECTS_LIFECYCLE;
    }

    private boolean isLockTaskModeViolation(WindowContainer parent, Task task,
            boolean isInLockTaskMode) {
        if (!isInLockTaskMode || parent == null || task == null) {
            return false;
        }
        final LockTaskController lockTaskController = mService.getLockTaskController();
        boolean taskViolation = lockTaskController.isLockTaskModeViolation(task);
        if (!taskViolation && parent.asTask() != null) {
            taskViolation = lockTaskController.isLockTaskModeViolation(parent.asTask());
        }
        if (taskViolation) {
            Slog.w(TAG, "Can't support the operation since in lock task mode violation. "
                    + " Task: " + task + " Parent : " + parent);
        }
        return taskViolation;
    }

    private int reparentChildrenTasksHierarchyOp(WindowContainerTransaction.HierarchyOp hop,
            @Nullable Transition transition, int syncId, boolean isInLockTaskMode) {
        WindowContainer<?> currentParent = hop.getContainer() != null
                ? WindowContainer.fromBinder(hop.getContainer()) : null;
        WindowContainer newParent = hop.getNewParent() != null
                ? WindowContainer.fromBinder(hop.getNewParent()) : null;
        if (currentParent == null && newParent == null) {
            throw new IllegalArgumentException("reparentChildrenTasksHierarchyOp: " + hop);
        } else if (currentParent == null) {
            currentParent = newParent.asTask().getDisplayContent().getDefaultTaskDisplayArea();
        } else if (newParent == null) {
            newParent = currentParent.asTask().getDisplayContent().getDefaultTaskDisplayArea();
        }

        if (currentParent == newParent) {
            Slog.e(TAG, "reparentChildrenTasksHierarchyOp parent not changing: " + hop);
            return 0;
        }
        if (!currentParent.isAttached()) {
            Slog.e(TAG, "reparentChildrenTasksHierarchyOp currentParent detached="
                    + currentParent + " hop=" + hop);
            return 0;
        }
        if (!newParent.isAttached()) {
            Slog.e(TAG, "reparentChildrenTasksHierarchyOp newParent detached="
                    + newParent + " hop=" + hop);
            return 0;
        }
        if (newParent.inPinnedWindowingMode()) {
            Slog.e(TAG, "reparentChildrenTasksHierarchyOp newParent in PIP="
                    + newParent + " hop=" + hop);
            return 0;
        }

        final boolean newParentInMultiWindow = newParent.inMultiWindowMode();
        final TaskDisplayArea newParentTda = newParent.asTask() != null
                ? newParent.asTask().getDisplayArea()
                : newParent.asTaskDisplayArea();
        final WindowContainer finalCurrentParent = currentParent;
        final WindowContainer finalNewParent = newParent;
        Slog.i(TAG, "reparentChildrenTasksHierarchyOp"
                + " currentParent=" + currentParent + " newParent=" + newParent + " hop=" + hop);

        // We want to collect the tasks first before re-parenting to avoid array shifting on us.
        final ArrayList<Task> tasksToReparent = new ArrayList<>();

        currentParent.forAllTasks(task -> {
            Slog.i(TAG, " Processing task=" + task);
            final boolean reparent;
            if (task.mCreatedByOrganizer || task.getParent() != finalCurrentParent) {
                // We only care about non-organized task that are direct children of the thing we
                // are reparenting from.
                return false;
            }
            if (newParentInMultiWindow && !task.supportsMultiWindowInDisplayArea(newParentTda)) {
                Slog.e(TAG, "reparentChildrenTasksHierarchyOp non-resizeable task to multi window,"
                        + " task=" + task);
                return false;
            }
            if (!ArrayUtils.isEmpty(hop.getActivityTypes())
                    && !ArrayUtils.contains(hop.getActivityTypes(), task.getActivityType())) {
                return false;
            }
            if (!ArrayUtils.isEmpty(hop.getWindowingModes())
                    && !ArrayUtils.contains(hop.getWindowingModes(), task.getWindowingMode())) {
                return false;
            }
            if (isLockTaskModeViolation(finalNewParent, task, isInLockTaskMode)) {
                return false;
            }

            if (hop.getToTop()) {
                tasksToReparent.add(0, task);
            } else {
                tasksToReparent.add(task);
            }
            return hop.getReparentTopOnly() && tasksToReparent.size() == 1;
        });

        final int count = tasksToReparent.size();
        for (int i = 0; i < count; ++i) {
            final Task task = tasksToReparent.get(i);
            if (syncId >= 0) {
                addToSyncSet(syncId, task);
            }
            if (transition != null) transition.collect(task);

            if (newParent instanceof TaskDisplayArea) {
                // For now, reparenting to display area is different from other reparents...
                task.reparent((TaskDisplayArea) newParent, hop.getToTop());
            } else {
                task.reparent((Task) newParent,
                        hop.getToTop() ? POSITION_TOP : POSITION_BOTTOM,
                        false /*moveParents*/, "processChildrenTaskReparentHierarchyOp");
            }
        }

        if (transition != null) transition.collect(newParent);

        return TRANSACT_EFFECTS_LIFECYCLE;
    }

    private int setAdjacentRootsHierarchyOp(WindowContainerTransaction.HierarchyOp hop) {
        final TaskFragment root1 = WindowContainer.fromBinder(hop.getContainer()).asTaskFragment();
        final TaskFragment root2 =
                WindowContainer.fromBinder(hop.getAdjacentRoot()).asTaskFragment();
        if (!root1.mCreatedByOrganizer || !root2.mCreatedByOrganizer) {
            throw new IllegalArgumentException("setAdjacentRootsHierarchyOp: Not created by"
                    + " organizer root1=" + root1 + " root2=" + root2);
        }
        if (root1.isEmbeddedTaskFragmentInPip() || root2.isEmbeddedTaskFragmentInPip()) {
            Slog.e(TAG, "Attempt to set adjacent TaskFragment in PIP Task");
            return 0;
        }
        root1.setAdjacentTaskFragment(root2);
        return TRANSACT_EFFECTS_LIFECYCLE;
    }

    private void sanitizeWindowContainer(WindowContainer wc) {
        if (!(wc instanceof TaskFragment) && !(wc instanceof DisplayArea)) {
            throw new RuntimeException("Invalid token in task fragment or displayArea transaction");
        }
    }

    private int applyWindowContainerChange(WindowContainer wc,
            WindowContainerTransaction.Change c, @Nullable IBinder errorCallbackToken) {
        sanitizeWindowContainer(wc);
        if (wc.asDisplayArea() != null) {
            return applyDisplayAreaChanges(wc.asDisplayArea(), c);
        } else if (wc.asTask() != null) {
            return applyTaskChanges(wc.asTask(), c);
        } else if (wc.asTaskFragment() != null) {
            return applyTaskFragmentChanges(wc.asTaskFragment(), c, errorCallbackToken);
        } else {
            return applyChanges(wc, c, errorCallbackToken);
        }
    }

    @Override
    public ITaskOrganizerController getTaskOrganizerController() {
        enforceTaskPermission("getTaskOrganizerController()");
        return mTaskOrganizerController;
    }

    @Override
    public IDisplayAreaOrganizerController getDisplayAreaOrganizerController() {
        enforceTaskPermission("getDisplayAreaOrganizerController()");
        return mDisplayAreaOrganizerController;
    }

    @Override
    public ITaskFragmentOrganizerController getTaskFragmentOrganizerController() {
        return mTaskFragmentOrganizerController;
    }

    /**
     * This will prepare a {@link BLASTSyncEngine.SyncGroup} for the organizer to track, but the
     * {@link BLASTSyncEngine.SyncGroup} may not be active until the {@link BLASTSyncEngine} is
     * free.
     */
    private BLASTSyncEngine.SyncGroup prepareSyncWithOrganizer(
            IWindowContainerTransactionCallback callback) {
        final BLASTSyncEngine.SyncGroup s = mService.mWindowManager.mSyncEngine
                .prepareSyncSet(this, "", BLASTSyncEngine.METHOD_BLAST);
        mTransactionCallbacksByPendingSyncId.put(s.mSyncId, callback);
        return s;
    }

    @VisibleForTesting
    int startSyncWithOrganizer(IWindowContainerTransactionCallback callback) {
        final BLASTSyncEngine.SyncGroup s = prepareSyncWithOrganizer(callback);
        mService.mWindowManager.mSyncEngine.startSyncSet(s);
        return s.mSyncId;
    }

    @VisibleForTesting
    void setSyncReady(int id) {
        ProtoLog.v(WM_DEBUG_WINDOW_ORGANIZER, "Set sync ready, syncId=%d", id);
        mService.mWindowManager.mSyncEngine.setReady(id);
    }

    @VisibleForTesting
    void addToSyncSet(int syncId, WindowContainer wc) {
        mService.mWindowManager.mSyncEngine.addToSyncSet(syncId, wc);
    }

    @Override
    public void onTransactionReady(int syncId, SurfaceControl.Transaction t) {
        ProtoLog.v(WM_DEBUG_WINDOW_ORGANIZER, "Transaction ready, syncId=%d", syncId);
        final IWindowContainerTransactionCallback callback =
                mTransactionCallbacksByPendingSyncId.get(syncId);

        try {
            callback.onTransactionReady(syncId, t);
        } catch (RemoteException e) {
            // If there's an exception when trying to send the mergedTransaction to the client, we
            // should immediately apply it here so the transactions aren't lost.
            t.apply();
        }

        mTransactionCallbacksByPendingSyncId.remove(syncId);
    }

    @Override
    public void registerTransitionPlayer(ITransitionPlayer player) {
        enforceTaskPermission("registerTransitionPlayer()");
        final int callerPid = Binder.getCallingPid();
        final int callerUid = Binder.getCallingUid();
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mGlobalLock) {
                final WindowProcessController wpc =
                        mService.getProcessController(callerPid, callerUid);
                mTransitionController.registerTransitionPlayer(player, wpc);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public ITransitionMetricsReporter getTransitionMetricsReporter() {
        return mTransitionController.mTransitionMetricsReporter;
    }

    /** Whether the configuration changes are important to report back to an organizer. */
    static boolean configurationsAreEqualForOrganizer(
            Configuration newConfig, @Nullable Configuration oldConfig) {
        if (oldConfig == null) {
            return false;
        }
        int cfgChanges = newConfig.diff(oldConfig);
        final int winCfgChanges = (cfgChanges & ActivityInfo.CONFIG_WINDOW_CONFIGURATION) != 0
                ? (int) newConfig.windowConfiguration.diff(oldConfig.windowConfiguration,
                true /* compareUndefined */) : 0;
        if ((winCfgChanges & CONTROLLABLE_WINDOW_CONFIGS) == 0) {
            cfgChanges &= ~ActivityInfo.CONFIG_WINDOW_CONFIGURATION;
        }
        return (cfgChanges & CONTROLLABLE_CONFIGS) == 0;
    }

    /**
     * Makes sure that the transaction only contains operations that are allowed for the
     * {@link WindowContainerTransaction#getTaskFragmentOrganizer()}.
     */
    private void enforceTaskFragmentOrganizerPermission(@NonNull String func,
            @NonNull ITaskFragmentOrganizer organizer, @NonNull WindowContainerTransaction t) {
        // Configuration changes
        final Iterator<Map.Entry<IBinder, WindowContainerTransaction.Change>> entries =
                t.getChanges().entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<IBinder, WindowContainerTransaction.Change> entry = entries.next();
            // Only allow to apply changes to TaskFragment that is created by this organizer.
            final WindowContainer wc = WindowContainer.fromBinder(entry.getKey());
            enforceTaskFragmentOrganized(func, wc, organizer);
            enforceTaskFragmentConfigChangeAllowed(func, wc, entry.getValue(), organizer);
        }

        // Hierarchy changes
        final List<WindowContainerTransaction.HierarchyOp> hops = t.getHierarchyOps();
        for (int i = hops.size() - 1; i >= 0; i--) {
            final WindowContainerTransaction.HierarchyOp hop = hops.get(i);
            final int type = hop.getType();
            // Check for each type of the operations that are allowed for TaskFragmentOrganizer.
            switch (type) {
                case HIERARCHY_OP_TYPE_DELETE_TASK_FRAGMENT:
                    enforceTaskFragmentOrganized(func,
                            WindowContainer.fromBinder(hop.getContainer()), organizer);
                    break;
                case HIERARCHY_OP_TYPE_SET_ADJACENT_ROOTS:
                    enforceTaskFragmentOrganized(func,
                            WindowContainer.fromBinder(hop.getContainer()), organizer);
                    enforceTaskFragmentOrganized(func,
                            WindowContainer.fromBinder(hop.getAdjacentRoot()),
                            organizer);
                    break;
                case HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT:
                    // We are allowing organizer to create TaskFragment. We will check the
                    // ownerToken in #createTaskFragment, and trigger error callback if that is not
                    // valid.
                    break;
                case HIERARCHY_OP_TYPE_START_ACTIVITY_IN_TASK_FRAGMENT:
                case HIERARCHY_OP_TYPE_REQUEST_FOCUS_ON_TASK_FRAGMENT:
                case HIERARCHY_OP_TYPE_SET_TASK_FRAGMENT_OPERATION:
                    enforceTaskFragmentOrganized(func, hop.getContainer(), organizer);
                    break;
                case HIERARCHY_OP_TYPE_REPARENT_ACTIVITY_TO_TASK_FRAGMENT:
                    enforceTaskFragmentOrganized(func, hop.getNewParent(), organizer);
                    break;
                case HIERARCHY_OP_TYPE_SET_COMPANION_TASK_FRAGMENT:
                    enforceTaskFragmentOrganized(func, hop.getContainer(), organizer);
                    if (hop.getCompanionContainer() != null) {
                        enforceTaskFragmentOrganized(func, hop.getCompanionContainer(), organizer);
                    }
                    break;
                case HIERARCHY_OP_TYPE_SET_ADJACENT_TASK_FRAGMENTS:
                    enforceTaskFragmentOrganized(func, hop.getContainer(), organizer);
                    if (hop.getAdjacentRoot() != null) {
                        enforceTaskFragmentOrganized(func, hop.getAdjacentRoot(), organizer);
                    }
                    break;
                case HIERARCHY_OP_TYPE_REPARENT_CHILDREN:
                    enforceTaskFragmentOrganized(func,
                            WindowContainer.fromBinder(hop.getContainer()), organizer);
                    if (hop.getNewParent() != null) {
                        enforceTaskFragmentOrganized(func,
                                WindowContainer.fromBinder(hop.getNewParent()),
                                organizer);
                    }
                    break;
                case HIERARCHY_OP_TYPE_FINISH_ACTIVITY:
                    // Allow finish activity if it has the activity token.
                    break;
                default:
                    // Other types of hierarchy changes are not allowed.
                    String msg = "Permission Denial: " + func + " from pid="
                            + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                            + " trying to apply a hierarchy change that is not allowed for"
                            + " TaskFragmentOrganizer=" + organizer;
                    Slog.w(TAG, msg);
                    throw new SecurityException(msg);
            }
        }
    }

    /**
     * Makes sure that the given {@link WindowContainer} is a {@link TaskFragment} organized by the
     * given {@link ITaskFragmentOrganizer}.
     */
    private void enforceTaskFragmentOrganized(@NonNull String func, @Nullable WindowContainer wc,
            @NonNull ITaskFragmentOrganizer organizer) {
        if (wc == null) {
            Slog.e(TAG, "Attempt to operate on window that no longer exists");
            return;
        }

        final TaskFragment tf = wc.asTaskFragment();
        if (tf == null || !tf.hasTaskFragmentOrganizer(organizer)) {
            String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid() + " trying to modify window container not"
                    + " belonging to the TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
    }

    /**
     * Makes sure that the {@link TaskFragment} of the given fragment token is created and organized
     * by the given {@link ITaskFragmentOrganizer}.
     */
    private void enforceTaskFragmentOrganized(@NonNull String func,
            @NonNull IBinder fragmentToken, @NonNull ITaskFragmentOrganizer organizer) {
        Objects.requireNonNull(fragmentToken);
        final TaskFragment tf = mLaunchTaskFragments.get(fragmentToken);
        // When the TaskFragment is {@code null}, it means that the TaskFragment will be created
        // later in the same transaction, in which case it will always be organized by the given
        // organizer.
        if (tf != null && !tf.hasTaskFragmentOrganizer(organizer)) {
            String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid() + " trying to modify TaskFragment not"
                    + " belonging to the TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
    }

    /**
     * Makes sure that SurfaceControl transactions and the ability to set bounds outside of the
     * parent bounds are not allowed for embedding without full trust between the host and the
     * target.
     */
    private void enforceTaskFragmentConfigChangeAllowed(String func, @Nullable WindowContainer wc,
            WindowContainerTransaction.Change change, ITaskFragmentOrganizer organizer) {
        if (wc == null) {
            Slog.e(TAG, "Attempt to operate on task fragment that no longer exists");
            return;
        }
        if (change == null) {
            return;
        }
        final int changeMask = change.getChangeMask();
        if (changeMask != 0) {
            // None of the change should be requested from a TaskFragment organizer.
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " trying to apply changes of " + changeMask + " to TaskFragment"
                    + " TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        // Check if TaskFragment is embedded in fully trusted mode.
        if (wc.asTaskFragment().isAllowedToBeEmbeddedInTrustedMode()) {
            // Fully trusted, no need to check further
            return;
        }
        final WindowContainer wcParent = wc.getParent();
        if (wcParent == null) {
            Slog.e(TAG, "Attempt to apply config change on task fragment that has no parent");
            return;
        }
        final Configuration requestedConfig = change.getConfiguration();
        final Configuration parentConfig = wcParent.getConfiguration();
        if (parentConfig.screenWidthDp < requestedConfig.screenWidthDp
                || parentConfig.screenHeightDp < requestedConfig.screenHeightDp
                || parentConfig.smallestScreenWidthDp < requestedConfig.smallestScreenWidthDp) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " trying to apply screen width/height greater than parent's for non-trusted"
                    + " host, TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        if (change.getWindowSetMask() == 0) {
            // No bounds change.
            return;
        }
        final WindowConfiguration requestedWindowConfig = requestedConfig.windowConfiguration;
        final WindowConfiguration parentWindowConfig = parentConfig.windowConfiguration;
        if (!requestedWindowConfig.getBounds().isEmpty()
                && !parentWindowConfig.getBounds().contains(requestedWindowConfig.getBounds())) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " trying to apply bounds outside of parent for non-trusted host,"
                    + " TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        if (requestedWindowConfig.getAppBounds() != null
                && !requestedWindowConfig.getAppBounds().isEmpty()
                && parentWindowConfig.getAppBounds() != null
                && !parentWindowConfig.getAppBounds().contains(
                        requestedWindowConfig.getAppBounds())) {
            String msg = "Permission Denial: " + func + " from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " trying to apply app bounds outside of parent for non-trusted host,"
                    + " TaskFragmentOrganizer=" + organizer;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
    }

    private void createTaskFragment(@NonNull TaskFragmentCreationParams creationParams,
            @Nullable IBinder errorCallbackToken, @NonNull CallerInfo caller,
            @Nullable Transition transition) {
        final ActivityRecord ownerActivity =
                ActivityRecord.forTokenLocked(creationParams.getOwnerToken());
        final ITaskFragmentOrganizer organizer = ITaskFragmentOrganizer.Stub.asInterface(
                creationParams.getOrganizer().asBinder());

        if (mLaunchTaskFragments.containsKey(creationParams.getFragmentToken())) {
            final Throwable exception =
                    new IllegalArgumentException("TaskFragment token must be unique");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, null /* taskFragment */,
                    HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT, exception);
            return;
        }
        if (ownerActivity == null || ownerActivity.getTask() == null) {
            final Throwable exception =
                    new IllegalArgumentException("Not allowed to operate with invalid ownerToken");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, null /* taskFragment */,
                    HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT, exception);
            return;
        }
        if (!ownerActivity.isResizeable()) {
            final IllegalArgumentException exception = new IllegalArgumentException("Not allowed"
                    + " to operate with non-resizable owner Activity");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, null /* taskFragment */,
                    HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT, exception);
            return;
        }
        // The ownerActivity has to belong to the same app as the target Task.
        final Task ownerTask = ownerActivity.getTask();
        if (ownerTask.effectiveUid != ownerActivity.getUid()
                || ownerTask.effectiveUid != caller.mUid) {
            final Throwable exception =
                    new SecurityException("Not allowed to operate with the ownerToken while "
                            + "the root activity of the target task belong to the different app");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, null /* taskFragment */,
                    HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT, exception);
            return;
        }
        if (ownerTask.inPinnedWindowingMode()) {
            final Throwable exception = new IllegalArgumentException(
                    "Not allowed to create TaskFragment in PIP Task");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, null /* taskFragment */,
                    HIERARCHY_OP_TYPE_CREATE_TASK_FRAGMENT, exception);
            return;
        }
        final TaskFragment taskFragment = new TaskFragment(mService,
                creationParams.getFragmentToken(), true /* createdByOrganizer */);
        // Set task fragment organizer immediately, since it might have to be notified about further
        // actions.
        taskFragment.setTaskFragmentOrganizer(creationParams.getOrganizer(),
                ownerActivity.getUid(), ownerActivity.info.processName);
        final int position;
        if (creationParams.getPairedPrimaryFragmentToken() != null) {
            // When there is a paired primary TaskFragment, we want to place the new TaskFragment
            // right above the paired one to make sure there is no other window in between.
            final TaskFragment pairedPrimaryTaskFragment = getTaskFragment(
                    creationParams.getPairedPrimaryFragmentToken());
            final int pairedPosition = ownerTask.mChildren.indexOf(pairedPrimaryTaskFragment);
            position = pairedPosition != -1 ? pairedPosition + 1 : POSITION_TOP;
        } else if (creationParams.getPairedActivityToken() != null) {
            // When there is a paired Activity, we want to place the new TaskFragment right above
            // the paired Activity to make sure the Activity position is not changed after reparent.
            final ActivityRecord pairedActivity = ActivityRecord.forTokenLocked(
                    creationParams.getPairedActivityToken());
            final int pairedPosition = ownerTask.mChildren.indexOf(pairedActivity);
            position = pairedPosition != -1 ? pairedPosition + 1 : POSITION_TOP;
        } else {
            position = POSITION_TOP;
        }
        ownerTask.addChild(taskFragment, position);
        taskFragment.setWindowingMode(creationParams.getWindowingMode());
        taskFragment.setBounds(creationParams.getInitialBounds());
        // Record the initial relative embedded bounds.
        taskFragment.updateRelativeEmbeddedBounds();
        mLaunchTaskFragments.put(creationParams.getFragmentToken(), taskFragment);

        if (transition != null) transition.collectExistenceChange(taskFragment);
    }

    private void reparentTaskFragment(@NonNull TaskFragment oldParent,
            @Nullable WindowContainer<?> newParent, @Nullable ITaskFragmentOrganizer organizer,
            @Nullable IBinder errorCallbackToken, @Nullable Transition transition) {
        final TaskFragment newParentTF;
        if (newParent == null) {
            // Use the old parent's parent if the caller doesn't specify the new parent.
            newParentTF = oldParent.getTask();
        } else {
            newParentTF = newParent.asTaskFragment();
        }
        if (newParentTF == null) {
            final Throwable exception =
                    new IllegalArgumentException("Not allowed to operate with invalid container");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, newParentTF,
                    HIERARCHY_OP_TYPE_REPARENT_CHILDREN, exception);
            return;
        }
        if (newParentTF.getTaskFragmentOrganizer() != null) {
            // We are reparenting activities to a new embedded TaskFragment, this operation is only
            // allowed if the new parent is trusted by all reparent activities.
            final boolean isEmbeddingDisallowed = oldParent.forAllActivities(activity ->
                    newParentTF.isAllowedToEmbedActivity(activity) != EMBEDDING_ALLOWED);
            if (isEmbeddingDisallowed) {
                final Throwable exception = new SecurityException(
                        "The new parent is not allowed to embed the activities.");
                sendTaskFragmentOperationFailure(organizer, errorCallbackToken, newParentTF,
                        HIERARCHY_OP_TYPE_REPARENT_CHILDREN, exception);
                return;
            }
        }
        if (newParentTF.isEmbeddedTaskFragmentInPip() || oldParent.isEmbeddedTaskFragmentInPip()) {
            final Throwable exception = new SecurityException(
                    "Not allow to reparent in TaskFragment in PIP Task.");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, newParentTF,
                    HIERARCHY_OP_TYPE_REPARENT_CHILDREN, exception);
            return;
        }
        if (newParentTF.getTask() != oldParent.getTask()) {
            final Throwable exception = new SecurityException(
                    "The new parent is not in the same Task as the old parent.");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, newParentTF,
                    HIERARCHY_OP_TYPE_REPARENT_CHILDREN, exception);
            return;
        }
        if (transition != null) {
            // Collect the current parent. It's visibility may change as a result of this
            // reparenting.
            transition.collect(oldParent);
            transition.collect(newParentTF);
        }
        while (oldParent.hasChild()) {
            final WindowContainer child = oldParent.getChildAt(0);
            if (transition != null) {
                transition.collect(child);
            }
            child.reparent(newParentTF, POSITION_TOP);
        }
    }

    private int deleteTaskFragment(@NonNull TaskFragment taskFragment,
            @Nullable ITaskFragmentOrganizer organizer, @Nullable IBinder errorCallbackToken,
            @Nullable Transition transition) {
        final int index = mLaunchTaskFragments.indexOfValue(taskFragment);
        if (index < 0) {
            final Throwable exception =
                    new IllegalArgumentException("Not allowed to operate with invalid "
                            + "taskFragment");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, taskFragment,
                    HIERARCHY_OP_TYPE_DELETE_TASK_FRAGMENT, exception);
            return 0;
        }
        if (taskFragment.isEmbeddedTaskFragmentInPip()
                // When the Task enters PiP before the organizer removes the empty TaskFragment, we
                // should allow it to do the cleanup.
                && taskFragment.getTopNonFinishingActivity() != null) {
            final Throwable exception = new IllegalArgumentException(
                    "Not allowed to delete TaskFragment in PIP Task");
            sendTaskFragmentOperationFailure(organizer, errorCallbackToken, taskFragment,
                    HIERARCHY_OP_TYPE_DELETE_TASK_FRAGMENT, exception);
            return 0;
        }

        if (transition != null) transition.collectExistenceChange(taskFragment);

        mLaunchTaskFragments.removeAt(index);
        taskFragment.remove(true /* withTransition */, "deleteTaskFragment");
        return TRANSACT_EFFECTS_LIFECYCLE;
    }

    @Nullable
    TaskFragment getTaskFragment(IBinder tfToken) {
        return mLaunchTaskFragments.get(tfToken);
    }

    void cleanUpEmbeddedTaskFragment(TaskFragment taskFragment) {
        mLaunchTaskFragments.remove(taskFragment.getFragmentToken());
    }

    static class CallerInfo {
        final int mPid;
        final int mUid;

        CallerInfo() {
            mPid = Binder.getCallingPid();
            mUid = Binder.getCallingUid();
        }
    }

    void sendTaskFragmentOperationFailure(@NonNull ITaskFragmentOrganizer organizer,
            @Nullable IBinder errorCallbackToken, @Nullable TaskFragment taskFragment, int opType,
            @NonNull Throwable exception) {
        if (organizer == null) {
            throw new IllegalArgumentException("Not allowed to operate with invalid organizer");
        }
        mService.mTaskFragmentOrganizerController
                .onTaskFragmentError(organizer, errorCallbackToken, taskFragment, opType,
                        exception);
    }

    private Throwable convertStartFailureToThrowable(int result, Intent intent) {
        switch (result) {
            case ActivityManager.START_INTENT_NOT_RESOLVED:
            case ActivityManager.START_CLASS_NOT_FOUND:
                return new ActivityNotFoundException("No Activity found to handle " + intent);
            case ActivityManager.START_PERMISSION_DENIED:
                return new SecurityException("Permission denied and not allowed to start activity "
                        + intent);
            case ActivityManager.START_CANCELED:
                return new AndroidRuntimeException("Activity could not be started for " + intent
                        + " with error code : " + result);
            default:
                return new AndroidRuntimeException("Start activity failed with error code : "
                        + result + " when starting " + intent);
        }
    }
}
