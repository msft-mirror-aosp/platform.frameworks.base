/**
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

package android.tests.enforcepermission.service;

import android.annotation.EnforcePermission;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.tests.enforcepermission.INested;
import android.tests.enforcepermission.IProtected;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestService extends Service {

    private static final String TAG = "EnforcePermission.TestService";
    private volatile ServiceConnection mNestedServiceConnection;

    @Override
    public void onCreate() {
        mNestedServiceConnection = new ServiceConnection();
        Intent intent = new Intent(this, NestedTestService.class);
        boolean bound = bindService(intent, mNestedServiceConnection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Log.wtf(TAG, "bindService() on NestedTestService failed");
        }
    }

    @Override
    public void onDestroy() {
        unbindService(mNestedServiceConnection);
    }

    private static final class ServiceConnection implements android.content.ServiceConnection {
        private volatile CompletableFuture<INested> mFuture = new CompletableFuture<>();

        public INested get() {
            try {
                return mFuture.get(1, TimeUnit.SECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException("Unable to reach NestedTestService: " + e.getMessage());
            }
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            mFuture.complete(INested.Stub.asInterface(service));
        }

        public void onServiceDisconnected(ComponentName className) {
            mFuture = new CompletableFuture<>();
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IProtected.Stub mBinder = new IProtected.Stub() {
        @Override
        @EnforcePermission(android.Manifest.permission.INTERNET)
        public void ProtectedByInternet() {
            ProtectedByInternet_enforcePermission();
        }

        @Override
        @EnforcePermission(android.Manifest.permission.VIBRATE)
        public void ProtectedByVibrate() {
            ProtectedByVibrate_enforcePermission();
        }

        @Override
        @EnforcePermission(android.Manifest.permission.INTERNET)
        public void ProtectedByInternetAndVibrateImplicitly() {
            ProtectedByInternetAndVibrateImplicitly_enforcePermission();

            ProtectedByVibrate();
        }

        @Override
        @EnforcePermission(android.Manifest.permission.INTERNET)
        public void ProtectedByInternetAndAccessNetworkStateImplicitly() throws RemoteException {
            ProtectedByInternetAndAccessNetworkStateImplicitly_enforcePermission();

            mNestedServiceConnection.get().ProtectedByAccessNetworkState();

        }

        @Override
        @EnforcePermission(android.Manifest.permission.INTERNET)
        public void ProtectedByInternetAndReadSyncSettingsImplicitly() throws RemoteException {
            ProtectedByInternetAndReadSyncSettingsImplicitly_enforcePermission();

            mNestedServiceConnection.get().ProtectedByReadSyncSettings();
        }
    };
}
