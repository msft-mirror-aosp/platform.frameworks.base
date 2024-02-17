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

package com.android.server.grammaticalinflection;

import static android.app.Flags.systemTermsOfAddressEnabled;
import static android.content.res.Configuration.GRAMMATICAL_GENDER_NOT_SPECIFIED;

import static com.android.server.grammaticalinflection.GrammaticalInflectionUtils.checkSystemGrammaticalGenderPermission;

import android.annotation.Nullable;
import android.app.ActivityTaskManager;
import android.app.GrammaticalInflectionManager;
import android.app.IGrammaticalInflectionManager;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.SystemProperties;
import android.os.Trace;
import android.permission.PermissionManager;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseIntArray;
import android.util.Xml;

import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The implementation of IGrammaticalInflectionManager.aidl.
 *
 * <p>This service is API entry point for storing app-specific grammatical inflection.
 */
public class GrammaticalInflectionService extends SystemService {
    private static final String TAG = "GrammaticalInflection";
    private static final String ATTR_NAME = "grammatical_gender";
    private static final String USER_SETTINGS_FILE_NAME = "user_settings.xml";
    private static final String TAG_GRAMMATICAL_INFLECTION = "grammatical_inflection";
    private static final String GRAMMATICAL_INFLECTION_ENABLED =
            "i18n.grammatical_Inflection.enabled";
    private static final String GRAMMATICAL_GENDER_PROPERTY = "persist.sys.grammatical_gender";

    private final GrammaticalInflectionBackupHelper mBackupHelper;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final Object mLock = new Object();
    private final SparseIntArray mGrammaticalGenderCache = new SparseIntArray();

    private PackageManagerInternal mPackageManagerInternal;
    private GrammaticalInflectionService.GrammaticalInflectionBinderService mBinderService;
    private PermissionManager mPermissionManager;
    private Context mContext;

    /**
     * Initializes the system service.
     * <p>
     * Subclasses must define a single argument constructor that accepts the context
     * and passes it to super.
     * </p>
     *
     * @param context The system server context.
     * @hide
     */
    public GrammaticalInflectionService(Context context) {
        super(context);
        mContext = context;
        mActivityTaskManagerInternal = LocalServices.getService(ActivityTaskManagerInternal.class);
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        mBackupHelper = new GrammaticalInflectionBackupHelper(this, context.getPackageManager());
        mBinderService = new GrammaticalInflectionBinderService();
        mPermissionManager = context.getSystemService(PermissionManager.class);
    }

    @Override
    public void onStart() {
        publishBinderService(Context.GRAMMATICAL_INFLECTION_SERVICE, mBinderService);
        LocalServices.addService(GrammaticalInflectionManagerInternal.class,
                new GrammaticalInflectionManagerInternalImpl());
    }

    private final class GrammaticalInflectionBinderService extends
            IGrammaticalInflectionManager.Stub {
        @Override
        public void setRequestedApplicationGrammaticalGender(
                String appPackageName, int userId, int gender) {
            GrammaticalInflectionService.this.setRequestedApplicationGrammaticalGender(
                    appPackageName, userId, gender);
        }

        @Override
        public void setSystemWideGrammaticalGender(int grammaticalGender, int userId) {
            checkCallerIsSystem();
            GrammaticalInflectionService.this.setSystemWideGrammaticalGender(grammaticalGender,
                    userId);
        }

        @Override
        public int getSystemGrammaticalGender(AttributionSource attributionSource, int userId) {
            return canGetSystemGrammaticalGender(attributionSource)
                    ? GrammaticalInflectionService.this.getSystemGrammaticalGender(
                    attributionSource, userId)
                    : GRAMMATICAL_GENDER_NOT_SPECIFIED;
        }

        @Override
        public void onShellCommand(FileDescriptor in, FileDescriptor out,
                FileDescriptor err, String[] args, ShellCallback callback,
                ResultReceiver resultReceiver) {
            (new GrammaticalInflectionShellCommand(mBinderService, mContext.getAttributionSource()))
                    .exec(this, in, out, err, args, callback, resultReceiver);
        }
    };

    private final class GrammaticalInflectionManagerInternalImpl
            extends GrammaticalInflectionManagerInternal {

        @Override
        @Nullable
        public byte[] getBackupPayload(int userId) {
            checkCallerIsSystem();
            return mBackupHelper.getBackupPayload(userId);
        }

        @Override
        public void stageAndApplyRestoredPayload(byte[] payload, int userId) {
            mBackupHelper.stageAndApplyRestoredPayload(payload, userId);
        }

        @Override
        public int getSystemGrammaticalGender(int userId) {
            return checkSystemTermsOfAddressIsEnabled()
                    ? GrammaticalInflectionService.this.getSystemGrammaticalGender(
                    mContext.getAttributionSource(), userId)
                    : GRAMMATICAL_GENDER_NOT_SPECIFIED;
        }

        @Override
        public int retrieveSystemGrammaticalGender(Configuration configuration) {
            int systemGrammaticalGender = getSystemGrammaticalGender(mContext.getUserId());
            // Retrieve the grammatical gender from system property, set it into
            // configuration which will get updated later if the grammatical gender raw value of
            // current configuration is {@link Configuration#GRAMMATICAL_GENDER_UNDEFINED}.
            if (configuration.getGrammaticalGenderRaw()
                    == Configuration.GRAMMATICAL_GENDER_UNDEFINED
                    || systemGrammaticalGender <= Configuration.GRAMMATICAL_GENDER_NOT_SPECIFIED) {
                systemGrammaticalGender = SystemProperties.getInt(GRAMMATICAL_GENDER_PROPERTY,
                        Configuration.GRAMMATICAL_GENDER_UNDEFINED);
            }
            return systemGrammaticalGender;
        }

        @Override
        public boolean canGetSystemGrammaticalGender(int uid, String packageName) {
            AttributionSource attributionSource = new AttributionSource.Builder(
                    uid).setPackageName(packageName).build();
            return GrammaticalInflectionService.this.canGetSystemGrammaticalGender(
                    attributionSource);
        }
    }

    protected int getApplicationGrammaticalGender(String appPackageName, int userId) {
        final ActivityTaskManagerInternal.PackageConfig appConfig =
                mActivityTaskManagerInternal.getApplicationConfig(appPackageName, userId);

        if (appConfig == null || appConfig.mGrammaticalGender == null) {
            return GRAMMATICAL_GENDER_NOT_SPECIFIED;
        } else {
            return appConfig.mGrammaticalGender;
        }
    }

    protected void setRequestedApplicationGrammaticalGender(
            String appPackageName, int userId, int gender) {
        int preValue = getApplicationGrammaticalGender(appPackageName, userId);
        final ActivityTaskManagerInternal.PackageConfigurationUpdater updater =
                mActivityTaskManagerInternal.createPackageConfigurationUpdater(appPackageName,
                        userId);

        if (!SystemProperties.getBoolean(GRAMMATICAL_INFLECTION_ENABLED, true)) {
            if (preValue != GRAMMATICAL_GENDER_NOT_SPECIFIED) {
                Log.d(TAG, "Clearing the user's grammatical gender setting");
                updater.setGrammaticalGender(GRAMMATICAL_GENDER_NOT_SPECIFIED).commit();
            }
            return;
        }

        final int uid = mPackageManagerInternal.getPackageUid(appPackageName, 0, userId);
        FrameworkStatsLog.write(FrameworkStatsLog.GRAMMATICAL_INFLECTION_CHANGED,
                FrameworkStatsLog.APPLICATION_GRAMMATICAL_INFLECTION_CHANGED__SOURCE_ID__OTHERS,
                uid,
                gender != GRAMMATICAL_GENDER_NOT_SPECIFIED,
                preValue != GRAMMATICAL_GENDER_NOT_SPECIFIED);

        updater.setGrammaticalGender(gender).commit();
    }

    protected void setSystemWideGrammaticalGender(int grammaticalGender, int userId) {
        Trace.beginSection("GrammaticalInflectionService.setSystemWideGrammaticalGender");
        if (!GrammaticalInflectionManager.VALID_GRAMMATICAL_GENDER_VALUES.contains(
                grammaticalGender)) {
            throw new IllegalArgumentException("Unknown grammatical gender");
        }

        if (!checkSystemTermsOfAddressIsEnabled()) {
            if (grammaticalGender == GRAMMATICAL_GENDER_NOT_SPECIFIED) {
                return;
            }
            Log.d(TAG, "Clearing the system grammatical gender setting");
            grammaticalGender = GRAMMATICAL_GENDER_NOT_SPECIFIED;
        }

        synchronized (mLock) {
            final File file = getGrammaticalGenderFile(userId);
            final AtomicFile atomicFile = new AtomicFile(file);
            FileOutputStream stream = null;
            try {
                stream = atomicFile.startWrite();
                stream.write(toXmlByteArray(grammaticalGender, stream));
                atomicFile.finishWrite(stream);
                mGrammaticalGenderCache.put(userId, grammaticalGender);
            } catch (IOException e) {
                Log.e(TAG, "Failed to write file " + atomicFile, e);
                if (stream != null) {
                    atomicFile.failWrite(stream);
                }
                throw new RuntimeException(e);
            }
        }

        try {
            Configuration config = new Configuration();
            config.setGrammaticalGender(grammaticalGender);
            ActivityTaskManager.getService().updateConfiguration(config);
        } catch (RemoteException e) {
            Log.w(TAG, "Can not update configuration", e);
        }
        Trace.endSection();
    }

    public int getSystemGrammaticalGender(AttributionSource attributionSource, int userId) {
        String packageName = attributionSource.getPackageName();
        if (packageName == null) {
            Log.d(TAG, "Package name is null.");
            return GRAMMATICAL_GENDER_NOT_SPECIFIED;
        }

        synchronized (mLock) {
            int grammaticalGender = mGrammaticalGenderCache.get(userId);
            return grammaticalGender < 0 ? GRAMMATICAL_GENDER_NOT_SPECIFIED : grammaticalGender;
        }
    }

    private File getGrammaticalGenderFile(int userId) {
        final File dir = new File(Environment.getDataSystemCeDirectory(userId),
                TAG_GRAMMATICAL_INFLECTION);
        return new File(dir, USER_SETTINGS_FILE_NAME);
    }

    private byte[] toXmlByteArray(int grammaticalGender, FileOutputStream fileStream) {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TypedXmlSerializer out = Xml.resolveSerializer(fileStream);
            out.setOutput(outputStream, StandardCharsets.UTF_8.name());
            out.startDocument(/* encoding= */ null, /* standalone= */ true);
            out.startTag(null, TAG_GRAMMATICAL_INFLECTION);
            out.attributeInt(null, ATTR_NAME, grammaticalGender);
            out.endTag(null, TAG_GRAMMATICAL_INFLECTION);
            out.endDocument();

            return outputStream.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private int getGrammaticalGenderFromXml(TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {

        XmlUtils.nextElement(parser);
        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            if (TAG_GRAMMATICAL_INFLECTION.equals(tagName)) {
                return parser.getAttributeInt(null, ATTR_NAME);
            } else {
                XmlUtils.nextElement(parser);
            }
        }

        return GRAMMATICAL_GENDER_NOT_SPECIFIED;
    }

    private void checkCallerIsSystem() {
        int callingUid = Binder.getCallingUid();
        if (callingUid != Process.SYSTEM_UID && callingUid != Process.SHELL_UID
                && callingUid != Process.ROOT_UID) {
            throw new SecurityException("Caller is not system, shell and root.");
        }
    }

    private boolean checkSystemTermsOfAddressIsEnabled() {
        if (!systemTermsOfAddressEnabled()) {
            Log.d(TAG, "The flag must be enabled to allow calling the API.");
            return false;
        }
        return true;
    }

    private boolean canGetSystemGrammaticalGender(AttributionSource attributionSource) {
        return checkSystemTermsOfAddressIsEnabled() && checkSystemGrammaticalGenderPermission(
                mPermissionManager, attributionSource);
    }

    @Override
    public void onUserUnlocked(TargetUser user) {
        IoThread.getHandler().post(() -> {
            int userId = user.getUserIdentifier();
            final File file = getGrammaticalGenderFile(userId);
            synchronized (mLock) {
                if (!file.exists()) {
                    Log.d(TAG, "User " + userId + "doesn't have the grammatical gender file.");
                    return;
                }
                if (mGrammaticalGenderCache.indexOfKey(userId) < 0) {
                    try {
                        InputStream in = new FileInputStream(file);
                        final TypedXmlPullParser parser = Xml.resolvePullParser(in);
                        mGrammaticalGenderCache.put(userId, getGrammaticalGenderFromXml(parser));
                    } catch (IOException | XmlPullParserException e) {
                        Log.e(TAG, "Failed to parse XML configuration from " + file, e);
                    }
                }
            }
        });
    }
}
