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

package com.android.server.am;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.am.ActivityManagerService.Injector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.app.ApplicationStartInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;

import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.appop.AppOpsService;
import com.android.server.wm.ActivityTaskManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/**
 * Test class for {@link android.app.ApplicationStartInfo}.
 *
 * Build/Install/Run:
 * atest ApplicationStartInfoTest
 */
@Presubmit
public class ApplicationStartInfoTest {

    private static final String TAG = ApplicationStartInfoTest.class.getSimpleName();
    private static final ComponentName COMPONENT = new ComponentName("com.android.test", ".Foo");

    @Rule public ServiceThreadRule mServiceThreadRule = new ServiceThreadRule();
    @Mock private AppOpsService mAppOpsService;
    @Mock private PackageManagerInternal mPackageManagerInt;

    private Context mContext = getInstrumentation().getTargetContext();
    private TestInjector mInjector;
    private ActivityManagerService mAms;
    private ProcessList mProcessList;
    private AppStartInfoTracker mAppStartInfoTracker;
    private Handler mHandler;
    private HandlerThread mHandlerThread;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mProcessList = spy(new ProcessList());
        mAppStartInfoTracker = spy(new AppStartInfoTracker());
        mAppStartInfoTracker.mEnabled = true;
        setFieldValue(ProcessList.class, mProcessList, "mAppStartInfoTracker",
                mAppStartInfoTracker);
        mInjector = new TestInjector(mContext);
        mAms = new ActivityManagerService(mInjector, mServiceThreadRule.getThread());
        mAms.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mAms.mActivityTaskManager.initialize(null, null, mContext.getMainLooper());
        mAms.mAtmInternal = spy(mAms.mActivityTaskManager.getAtmInternal());
        mAms.mPackageManagerInt = mPackageManagerInt;
        mAppStartInfoTracker.mService = mAms;
        doReturn(new ComponentName("", "")).when(mPackageManagerInt).getSystemUiServiceComponent();
        doReturn("com.android.test").when(mPackageManagerInt).getNameForUid(anyInt());
        // Remove stale instance of PackageManagerInternal if there is any
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInt);
    }

    @After
    public void tearDown() {
        mHandlerThread.quit();
    }

    @Test
    public void testApplicationStartInfo() throws Exception {
        mAppStartInfoTracker.clearProcessStartInfo(true);
        mAppStartInfoTracker.mAppStartInfoLoaded.set(true);
        mAppStartInfoTracker.mAppStartInfoHistoryListSize =
                mAppStartInfoTracker.APP_START_INFO_HISTORY_LIST_SIZE;
        mAppStartInfoTracker.mProcStartStoreDir = new File(mContext.getFilesDir(),
                AppStartInfoTracker.APP_START_STORE_DIR);
        assertTrue(FileUtils.createDir(mAppStartInfoTracker.mProcStartStoreDir));
        mAppStartInfoTracker.mProcStartInfoFile = new File(mAppStartInfoTracker.mProcStartStoreDir,
                AppStartInfoTracker.APP_START_INFO_FILE);

        doNothing().when(mAppStartInfoTracker).schedulePersistProcessStartInfo(anyBoolean());

        final int app1Uid = 10123;
        final int app1Pid1 = 12345;
        final int app1Pid2 = 12346;
        final int app1DefiningUid = 23456;
        final int app1UidUser2 = 1010123;
        final int app1PidUser2 = 12347;
        final String app1ProcessName = "com.android.test.stub1:process";
        final String app1PackageName = "com.android.test.stub1";
        final long appStartTimestampIntentStarted = 1000000;
        final long appStartTimestampActivityLaunchFinished = 2000000;
        final long appStartTimestampReportFullyDrawn = 3000000;
        final long appStartTimestampService = 4000000;
        final long appStartTimestampBroadcast = 5000000;
        final long appStartTimestampRContentProvider = 6000000;

        ProcessRecord app = makeProcessRecord(
                app1Pid1,                    // pid
                app1Uid,                     // uid
                app1Uid,                     // packageUid
                null,                        // definingUid
                app1ProcessName,             // processName
                app1PackageName);            // packageName

        ArrayList<ApplicationStartInfo> list = new ArrayList<ApplicationStartInfo>();

        // Case 1: Activity start intent failed
        mAppStartInfoTracker.onIntentStarted(buildIntent(COMPONENT),
                appStartTimestampIntentStarted);
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 0);

        verifyInProgApplicationStartInfo(
                0,                                                    // index
                0,                                                    // pid
                0,                                                    // uid
                0,                                                    // packageUid
                null,                                                 // definingUid
                null,                                                 // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_UNSET,                // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        mAppStartInfoTracker.onIntentFailed(appStartTimestampIntentStarted);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(0);
        assertEquals(list.size(), 0);

        mAppStartInfoTracker.clearProcessStartInfo(true);

        // Case 2: Activity start launch cancelled
        mAppStartInfoTracker.onIntentStarted(buildIntent(COMPONENT),
                appStartTimestampIntentStarted);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 0);

        mAppStartInfoTracker.onActivityLaunched(appStartTimestampIntentStarted, COMPONENT,
                ApplicationStartInfo.START_TYPE_COLD, app);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 1);

        verifyInProgApplicationStartInfo(
                0,                                                    // index
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        mAppStartInfoTracker.onActivityLaunchCancelled(appStartTimestampIntentStarted);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(0);
        assertEquals(list.size(), 1);

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_ERROR,             // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        mAppStartInfoTracker.clearProcessStartInfo(true);

        // Case 3: Activity start success
        mAppStartInfoTracker.onIntentStarted(buildIntent(COMPONENT),
                appStartTimestampIntentStarted);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 0);

        mAppStartInfoTracker.onActivityLaunched(appStartTimestampIntentStarted, COMPONENT,
                ApplicationStartInfo.START_TYPE_COLD, app);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 1);

        verifyInProgApplicationStartInfo(
                0,                                                    // index
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        mAppStartInfoTracker.onActivityLaunchFinished(appStartTimestampIntentStarted, COMPONENT,
                appStartTimestampActivityLaunchFinished, ApplicationStartInfo.LAUNCH_MODE_STANDARD);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(1);
        assertEquals(list.size(), 1);

        verifyInProgApplicationStartInfo(
                0,                                                    // index
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN, // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        mAppStartInfoTracker.onReportFullyDrawn(appStartTimestampIntentStarted,
                appStartTimestampReportFullyDrawn);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1Pid1, 0, list);
        verifyInProgressRecordsSize(0);
        assertEquals(list.size(), 1);

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app1Pid1,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_START_ACTIVITY,     // reason
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN, // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        // Don't clear records for use in subsequent cases.

        // Case 4: Create an other app1 record with different pid started for a service
        sleep(1);
        app = makeProcessRecord(
                app1Pid2,                    // pid
                app1Uid,                     // uid
                app1Uid,                     // packageUid
                app1DefiningUid,             // definingUid
                app1ProcessName,             // processName
                app1PackageName);            // packageName
        ServiceRecord service = ServiceRecord.newEmptyInstanceForTest(mAms);

        mAppStartInfoTracker.handleProcessServiceStart(appStartTimestampService, app, service,
                false);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, 0, 0, list);
        assertEquals(list.size(), 2);

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app1Pid2,                                             // pid
                app1Uid,                                              // uid
                app1Uid,                                              // packageUid
                app1DefiningUid,                                      // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_SERVICE,            // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_WARM,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        // Case 5: Create an instance of app1 with a different user started for a broadcast
        sleep(1);
        app = makeProcessRecord(
                app1PidUser2,                    // pid
                app1UidUser2,                    // uid
                app1UidUser2,                    // packageUid
                null,                            // definingUid
                app1ProcessName,                 // processName
                app1PackageName);                // packageName

        mAppStartInfoTracker.handleProcessBroadcastStart(appStartTimestampBroadcast, app,
                null, true /* isColdStart */);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1UidUser2, app1PidUser2, 0, list);
        assertEquals(list.size(), 1);

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app1PidUser2,                                         // pid
                app1UidUser2,                                         // uid
                app1UidUser2,                                         // packageUid
                null,                                                 // definingUid
                app1ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_BROADCAST,          // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_COLD,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        // Case 6: User 2 gets removed
        mAppStartInfoTracker.onPackageRemoved(app1PackageName, app1UidUser2, false);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1UidUser2, app1PidUser2, 0, list);
        assertEquals(list.size(), 0);

        list.clear();
        mAppStartInfoTracker.getStartInfo(app1PackageName, app1Uid, app1PidUser2, 0, list);
        assertEquals(list.size(), 2);


        // Case 7: Create a process from another package started for a content provider
        final int app2UidUser2 = 1010234;
        final int app2PidUser2 = 12348;
        final String app2ProcessName = "com.android.test.stub2:process";
        final String app2PackageName = "com.android.test.stub2";

        sleep(1);

        app = makeProcessRecord(
                app2PidUser2,                    // pid
                app2UidUser2,                    // uid
                app2UidUser2,                    // packageUid
                null,                            // definingUid
                app2ProcessName,                 // processName
                app2PackageName);                // packageName

        mAppStartInfoTracker.handleProcessContentProviderStart(appStartTimestampRContentProvider,
                app, false);
        list.clear();
        mAppStartInfoTracker.getStartInfo(app2PackageName, app2UidUser2, app2PidUser2, 0, list);
        assertEquals(list.size(), 1);

        verifyApplicationStartInfo(
                list.get(0),                                          // info
                app2PidUser2,                                         // pid
                app2UidUser2,                                         // uid
                app2UidUser2,                                         // packageUid
                null,                                                 // definingUid
                app2ProcessName,                                      // processName
                ApplicationStartInfo.START_REASON_CONTENT_PROVIDER,   // reason
                ApplicationStartInfo.STARTUP_STATE_STARTED,           // startup state
                ApplicationStartInfo.START_TYPE_WARM,                 // state type
                ApplicationStartInfo.LAUNCH_MODE_STANDARD);           // launch mode

        // Case 8: Save and load again
        ArrayList<ApplicationStartInfo> original = new ArrayList<ApplicationStartInfo>();
        mAppStartInfoTracker.getStartInfo(null, app1Uid, 0, 0, original);
        assertTrue(original.size() > 0);

        mAppStartInfoTracker.persistProcessStartInfo();
        assertTrue(mAppStartInfoTracker.mProcStartInfoFile.exists());

        mAppStartInfoTracker.clearProcessStartInfo(false);
        list.clear();
        mAppStartInfoTracker.getStartInfo(null, app1Uid, 0, 0, list);
        assertEquals(0, list.size());

        mAppStartInfoTracker.loadExistingProcessStartInfo();
        list.clear();
        mAppStartInfoTracker.getStartInfo(null, app1Uid, 0, 0, list);
        assertEquals(original.size(), list.size());

        for (int i = list.size() - 1; i >= 0; i--) {
            assertTrue(list.get(i).equals(original.get(i)));
        }
    }

    private static <T> void setFieldValue(Class clazz, Object obj, String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private ProcessRecord makeProcessRecord(int pid, int uid, int packageUid, Integer definingUid,
            String processName, String packageName) {
        return makeProcessRecord(pid, uid, packageUid, definingUid, processName, packageName, mAms);
    }

    @SuppressWarnings("GuardedBy")
    static ProcessRecord makeProcessRecord(int pid, int uid, int packageUid, Integer definingUid,
            String processName, String packageName, ActivityManagerService ams) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        ProcessRecord app = new ProcessRecord(ams, ai, processName, uid);
        app.setPid(pid);
        app.info.uid = packageUid;
        if (definingUid != null) {
            app.setHostingRecord(HostingRecord.byAppZygote(COMPONENT, "", definingUid, ""));
        }
        return app;
    }

    private static Intent buildIntent(ComponentName componentName) throws Exception {
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.setPackage(componentName.getPackageName());
        return intent;
    }

    private void verifyInProgressRecordsSize(int expectedSize) {
        synchronized (mAppStartInfoTracker.mLock) {
            assertEquals(mAppStartInfoTracker.mInProgRecords.size(), expectedSize);
        }
    }

    private void verifyInProgApplicationStartInfo(int index,
            Integer pid, Integer uid, Integer packageUid,
            Integer definingUid, String processName,
            Integer reason, Integer startupState, Integer startType, Integer launchMode) {
        synchronized (mAppStartInfoTracker.mLock) {
            verifyApplicationStartInfo(mAppStartInfoTracker.mInProgRecords.valueAt(index),
                    pid, uid, packageUid, definingUid, processName, reason, startupState,
                    startType, launchMode);
        }
    }

    private void verifyApplicationStartInfo(ApplicationStartInfo info,
            Integer pid, Integer uid, Integer packageUid,
            Integer definingUid, String processName,
            Integer reason, Integer startupState, Integer startType, Integer launchMode) {
        assertNotNull(info);

        if (pid != null) {
            assertEquals(pid.intValue(), info.getPid());
        }
        if (uid != null) {
            assertEquals(uid.intValue(), info.getRealUid());
        }
        if (packageUid != null) {
            assertEquals(packageUid.intValue(), info.getPackageUid());
        }
        if (definingUid != null) {
            assertEquals(definingUid.intValue(), info.getDefiningUid());
        }
        if (processName != null) {
            assertTrue(TextUtils.equals(processName, info.getProcessName()));
        }
        if (reason != null) {
            assertEquals(reason.intValue(), info.getReason());
        }
        if (startupState != null) {
            assertEquals(startupState.intValue(), info.getStartupState());
        }
        if (startType != null) {
            assertEquals(startType.intValue(), info.getStartType());
        }
        if (launchMode != null) {
            assertEquals(launchMode.intValue(), info.getLaunchMode());
        }
    }

    private class TestInjector extends Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(File recentAccessesFile, File storageFile,
                Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mHandler;
        }

        @Override
        public ProcessList getProcessList(ActivityManagerService service) {
            return mProcessList;
        }
    }

    static class ServiceThreadRule implements TestRule {

        private ServiceThread mThread;

        ServiceThread getThread() {
            return mThread;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mThread = new ServiceThread("TestServiceThread",
                            Process.THREAD_PRIORITY_DEFAULT, true /* allowIo */);
                    mThread.start();
                    try {
                        base.evaluate();
                    } finally {
                        mThread.getThreadHandler().runWithScissors(mThread::quit, 0 /* timeout */);
                    }
                }
            };
        }
    }

    // TODO: [b/302724778] Remove manual JNI load
    static {
        System.loadLibrary("mockingservicestestjni");
    }
}
