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

package com.android.server.companion;

import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_CONTEXT_SYNC;

import static com.android.server.companion.PermissionsUtils.sanitizeWithCallerChecks;

import android.companion.AssociationInfo;
import android.companion.ContextSyncMessage;
import android.companion.Flags;
import android.companion.Telecom;
import android.companion.datatransfer.PermissionSyncRequest;
import android.net.MacAddress;
import android.os.Binder;
import android.os.ShellCommand;
import android.util.Base64;
import android.util.proto.ProtoOutputStream;

import com.android.server.companion.datatransfer.SystemDataTransferProcessor;
import com.android.server.companion.datatransfer.contextsync.BitmapUtils;
import com.android.server.companion.datatransfer.contextsync.CrossDeviceSyncController;
import com.android.server.companion.presence.CompanionDevicePresenceMonitor;
import com.android.server.companion.transport.CompanionTransportManager;

import java.io.PrintWriter;
import java.util.List;

class CompanionDeviceShellCommand extends ShellCommand {
    private static final String TAG = "CDM_CompanionDeviceShellCommand";

    private final CompanionDeviceManagerService mService;
    private final AssociationStoreImpl mAssociationStore;
    private final CompanionDevicePresenceMonitor mDevicePresenceMonitor;
    private final CompanionTransportManager mTransportManager;

    private final SystemDataTransferProcessor mSystemDataTransferProcessor;
    private final AssociationRequestsProcessor mAssociationRequestsProcessor;
    private final BackupRestoreProcessor mBackupRestoreProcessor;

    CompanionDeviceShellCommand(CompanionDeviceManagerService service,
            AssociationStoreImpl associationStore,
            CompanionDevicePresenceMonitor devicePresenceMonitor,
            CompanionTransportManager transportManager,
            SystemDataTransferProcessor systemDataTransferProcessor,
            AssociationRequestsProcessor associationRequestsProcessor,
            BackupRestoreProcessor backupRestoreProcessor) {
        mService = service;
        mAssociationStore = associationStore;
        mDevicePresenceMonitor = devicePresenceMonitor;
        mTransportManager = transportManager;
        mSystemDataTransferProcessor = systemDataTransferProcessor;
        mAssociationRequestsProcessor = associationRequestsProcessor;
        mBackupRestoreProcessor = backupRestoreProcessor;
    }

    @Override
    public int onCommand(String cmd) {
        final PrintWriter out = getOutPrintWriter();
        final int associationId;

        try {
            if ("simulate-device-event".equals(cmd) && Flags.devicePresence()) {
                associationId = getNextIntArgRequired();
                int event = getNextIntArgRequired();
                mDevicePresenceMonitor.simulateDeviceEvent(associationId, event);
                return 0;
            }
            switch (cmd) {
                case "list": {
                    final int userId = getNextIntArgRequired();
                    final List<AssociationInfo> associationsForUser =
                            mAssociationStore.getAssociationsForUser(userId);
                    for (AssociationInfo association : associationsForUser) {
                        // TODO(b/212535524): use AssociationInfo.toShortString(), once it's not
                        //  longer referenced in tests.
                        out.println(association.getPackageName() + " "
                                + association.getDeviceMacAddress() + " " + association.getId());
                    }
                }
                break;

                case "associate": {
                    int userId = getNextIntArgRequired();
                    String packageName = getNextArgRequired();
                    String address = getNextArgRequired();
                    String deviceProfile = getNextArg();
                    final MacAddress macAddress = MacAddress.fromString(address);
                    mService.createNewAssociation(userId, packageName, macAddress,
                            null, deviceProfile, false);
                }
                break;

                case "disassociate": {
                    final int userId = getNextIntArgRequired();
                    final String packageName = getNextArgRequired();
                    final String address = getNextArgRequired();
                    final AssociationInfo association =
                            mService.getAssociationWithCallerChecks(userId, packageName, address);
                    if (association != null) {
                        mService.disassociateInternal(association.getId());
                    }
                }
                break;

                case "disassociate-all": {
                    final int userId = getNextIntArgRequired();
                    final String packageName = getNextArgRequired();
                    final List<AssociationInfo> userAssociations =
                            mAssociationStore.getAssociationsForPackage(userId, packageName);
                    for (AssociationInfo association : userAssociations) {
                        if (sanitizeWithCallerChecks(mService.getContext(), association) != null) {
                            mService.disassociateInternal(association.getId());
                        }
                    }
                }
                break;

                case "clear-association-memory-cache":
                    mService.persistState();
                    mService.loadAssociationsFromDisk();
                    break;

                case "simulate-device-appeared":
                    associationId = getNextIntArgRequired();
                    mDevicePresenceMonitor.simulateDeviceEvent(associationId, /* event */ 0);
                    break;

                case "simulate-device-disappeared":
                    associationId = getNextIntArgRequired();
                    mDevicePresenceMonitor.simulateDeviceEvent(associationId, /* event */ 1);
                    break;

                case "get-backup-payload": {
                    final int userId = getNextIntArgRequired();
                    byte[] payload = mBackupRestoreProcessor.getBackupPayload(userId);
                    out.println(Base64.encodeToString(payload, Base64.NO_WRAP));
                }
                break;

                case "apply-restored-payload": {
                    final int userId = getNextIntArgRequired();
                    byte[] payload = Base64.decode(getNextArgRequired(), Base64.NO_WRAP);
                    mBackupRestoreProcessor.applyRestoredPayload(payload, userId);
                }
                break;

                case "remove-inactive-associations": {
                    // This command should trigger the same "clean-up" job as performed by the
                    // InactiveAssociationsRemovalService JobService. However, since the
                    // InactiveAssociationsRemovalService run as system, we want to run this
                    // as system (not as shell/root) as well.
                    Binder.withCleanCallingIdentity(
                            mService::removeInactiveSelfManagedAssociations);
                }
                break;

                case "create-emulated-transport":
                    // This command creates a RawTransport in order to test Transport listeners
                    associationId = getNextIntArgRequired();
                    mTransportManager.createEmulatedTransport(associationId);
                    break;

                case "send-context-sync-empty-message": {
                    associationId = getNextIntArgRequired();
                    mTransportManager.createEmulatedTransport(associationId)
                            .processMessage(MESSAGE_REQUEST_CONTEXT_SYNC,
                                    /* sequence= */ 0,
                                    CrossDeviceSyncController.createEmptyMessage());
                    break;
                }

                case "send-context-sync-call-create-message": {
                    associationId = getNextIntArgRequired();
                    String callId = getNextArgRequired();
                    String address = getNextArgRequired();
                    String facilitator = getNextArgRequired();
                    mTransportManager.createEmulatedTransport(associationId)
                            .processMessage(MESSAGE_REQUEST_CONTEXT_SYNC,
                                    /* sequence= */ 0,
                                    CrossDeviceSyncController.createCallCreateMessage(callId,
                                            address, facilitator));
                    break;
                }

                case "send-context-sync-call-control-message": {
                    associationId = getNextIntArgRequired();
                    String callId = getNextArgRequired();
                    int control = getNextIntArgRequired();
                    mTransportManager.createEmulatedTransport(associationId)
                            .processMessage(MESSAGE_REQUEST_CONTEXT_SYNC,
                                    /* sequence= */ 0,
                                    CrossDeviceSyncController.createCallControlMessage(callId,
                                            control));
                    break;
                }

                case "send-context-sync-call-facilitators-message": {
                    associationId = getNextIntArgRequired();
                    int numberOfFacilitators = getNextIntArgRequired();
                    String facilitatorName = getNextArgRequired();
                    String facilitatorId = getNextArgRequired();
                    final ProtoOutputStream pos = new ProtoOutputStream();
                    pos.write(ContextSyncMessage.VERSION, 1);
                    final long telecomToken = pos.start(ContextSyncMessage.TELECOM);
                    for (int i = 0; i < numberOfFacilitators; i++) {
                        final long facilitatorsToken = pos.start(Telecom.FACILITATORS);
                        pos.write(Telecom.CallFacilitator.NAME,
                                numberOfFacilitators == 1 ? facilitatorName : facilitatorName + i);
                        pos.write(Telecom.CallFacilitator.IDENTIFIER,
                                numberOfFacilitators == 1 ? facilitatorId : facilitatorId + i);
                        pos.end(facilitatorsToken);
                    }
                    pos.end(telecomToken);
                    mTransportManager.createEmulatedTransport(associationId)
                            .processMessage(MESSAGE_REQUEST_CONTEXT_SYNC,
                                    /* sequence= */ 0, pos.getBytes());
                    break;
                }

                case "send-context-sync-call-message": {
                    associationId = getNextIntArgRequired();
                    String callId = getNextArgRequired();
                    String facilitatorId = getNextArgRequired();
                    int status = getNextIntArgRequired();
                    boolean acceptControl = getNextBooleanArgRequired();
                    boolean rejectControl = getNextBooleanArgRequired();
                    boolean silenceControl = getNextBooleanArgRequired();
                    boolean muteControl = getNextBooleanArgRequired();
                    boolean unmuteControl = getNextBooleanArgRequired();
                    boolean endControl = getNextBooleanArgRequired();
                    boolean holdControl = getNextBooleanArgRequired();
                    boolean unholdControl = getNextBooleanArgRequired();
                    final ProtoOutputStream pos = new ProtoOutputStream();
                    pos.write(ContextSyncMessage.VERSION, 1);
                    final long telecomToken = pos.start(ContextSyncMessage.TELECOM);
                    final long callsToken = pos.start(Telecom.CALLS);
                    pos.write(Telecom.Call.ID, callId);
                    final long originToken = pos.start(Telecom.Call.ORIGIN);
                    pos.write(Telecom.Call.Origin.CALLER_ID, "Caller Name");
                    pos.write(Telecom.Call.Origin.APP_ICON, BitmapUtils.renderDrawableToByteArray(
                            mService.getContext().getPackageManager().getApplicationIcon(
                                    facilitatorId)));
                    final long facilitatorToken = pos.start(
                            Telecom.Request.CreateAction.FACILITATOR);
                    pos.write(Telecom.CallFacilitator.NAME, "Test App Name");
                    pos.write(Telecom.CallFacilitator.IDENTIFIER, facilitatorId);
                    pos.end(facilitatorToken);
                    pos.end(originToken);
                    pos.write(Telecom.Call.STATUS, status);
                    if (acceptControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.ACCEPT);
                    }
                    if (rejectControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.REJECT);
                    }
                    if (silenceControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.SILENCE);
                    }
                    if (muteControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.MUTE);
                    }
                    if (unmuteControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.UNMUTE);
                    }
                    if (endControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.END);
                    }
                    if (holdControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.PUT_ON_HOLD);
                    }
                    if (unholdControl) {
                        pos.write(Telecom.Call.CONTROLS, Telecom.TAKE_OFF_HOLD);
                    }
                    pos.end(callsToken);
                    pos.end(telecomToken);
                    mTransportManager.createEmulatedTransport(associationId)
                            .processMessage(MESSAGE_REQUEST_CONTEXT_SYNC,
                                    /* sequence= */ 0, pos.getBytes());
                    break;
                }

                case "disable-context-sync": {
                    associationId = getNextIntArgRequired();
                    int flag = getNextIntArgRequired();
                    mAssociationRequestsProcessor.disableSystemDataSync(associationId, flag);
                    break;
                }

                case "enable-context-sync": {
                    associationId = getNextIntArgRequired();
                    int flag = getNextIntArgRequired();
                    mAssociationRequestsProcessor.enableSystemDataSync(associationId, flag);
                    break;
                }

                case "get-perm-sync-state": {
                    associationId = getNextIntArgRequired();
                    PermissionSyncRequest request =
                            mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.println((request == null ? "null" : request.isUserConsented()));
                    break;
                }

                case "remove-perm-sync-state": {
                    associationId = getNextIntArgRequired();
                    PermissionSyncRequest request =
                            mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.print((request == null ? "null" : request.isUserConsented()));
                    mSystemDataTransferProcessor.removePermissionSyncRequest(associationId);
                    request = mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    // should print " -> null"
                    out.println(" -> " + (request == null ? "null" : request.isUserConsented()));
                    break;
                }

                case "enable-perm-sync": {
                    associationId = getNextIntArgRequired();
                    PermissionSyncRequest request =
                            mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.print((request == null ? "null" : request.isUserConsented()));
                    mSystemDataTransferProcessor.enablePermissionsSync(associationId);
                    request = mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.println(" -> " + request.isUserConsented()); // should print " -> true"
                    break;
                }

                case "disable-perm-sync": {
                    associationId = getNextIntArgRequired();
                    PermissionSyncRequest request =
                            mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.print((request == null ? "null" : request.isUserConsented()));
                    mSystemDataTransferProcessor.disablePermissionsSync(associationId);
                    request = mSystemDataTransferProcessor.getPermissionSyncRequest(associationId);
                    out.println(" -> " + request.isUserConsented()); // should print " -> false"
                    break;
                }

                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (Throwable e) {
            final PrintWriter errOut = getErrPrintWriter();
            errOut.println();
            errOut.println("Exception occurred while executing '" + cmd + "':");
            e.printStackTrace(errOut);
            return 1;
        }
        return 0;
    }

    private int getNextIntArgRequired() {
        return Integer.parseInt(getNextArgRequired());
    }

    private boolean getNextBooleanArgRequired() {
        String arg = getNextArgRequired();
        if ("true".equalsIgnoreCase(arg) || "false".equalsIgnoreCase(arg)) {
            return Boolean.parseBoolean(arg);
        } else {
            throw new IllegalArgumentException("Expected a boolean argument but was: " + arg);
        }
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Companion Device Manager (companiondevice) commands:");
        pw.println("  help");
        pw.println("      Print this help text.");
        pw.println("  list USER_ID");
        pw.println("      List all Associations for a user.");
        pw.println("  associate USER_ID PACKAGE MAC_ADDRESS [DEVICE_PROFILE]");
        pw.println("      Create a new Association.");
        pw.println("  disassociate USER_ID PACKAGE MAC_ADDRESS");
        pw.println("      Remove an existing Association.");
        pw.println("  disassociate-all USER_ID");
        pw.println("      Remove all Associations for a user.");
        pw.println("  clear-association-memory-cache");
        pw.println("      Clear the in-memory association cache and reload all association ");
        pw.println("      information from persistent storage. USE FOR DEBUGGING PURPOSES ONLY.");
        pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");

        pw.println("  simulate-device-appeared ASSOCIATION_ID");
        pw.println("      Make CDM act as if the given companion device has appeared.");
        pw.println("      I.e. bind the associated companion application's");
        pw.println("      CompanionDeviceService(s) and trigger onDeviceAppeared() callback.");
        pw.println("      The CDM will consider the devices as present for 60 seconds and then");
        pw.println("      will act as if device disappeared, unless 'simulate-device-disappeared'");
        pw.println("      or 'simulate-device-appeared' is called again before 60 seconds run out"
                + ".");
        pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");

        pw.println("  simulate-device-disappeared ASSOCIATION_ID");
        pw.println("      Make CDM act as if the given companion device has disappeared.");
        pw.println("      I.e. unbind the associated companion application's");
        pw.println("      CompanionDeviceService(s) and trigger onDeviceDisappeared() callback.");
        pw.println("      NOTE: This will only have effect if 'simulate-device-appeared' was");
        pw.println("      invoked for the same device (same ASSOCIATION_ID) no longer than");
        pw.println("      60 seconds ago.");

        pw.println("  get-backup-payload USER_ID");
        pw.println("      Generate backup payload for the given user and print its content");
        pw.println("      encoded to a Base64 string.");
        pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");
        pw.println("  apply-restored-payload USER_ID PAYLOAD");
        pw.println("      Apply restored backup payload for the given user.");
        pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");

        if (Flags.devicePresence()) {
            pw.println("  simulate-device-event ASSOCIATION_ID EVENT");
            pw.println("  Simulate the companion device event changes:");
            pw.println("    Case(0): ");
            pw.println("      Make CDM act as if the given companion device has appeared.");
            pw.println("      I.e. bind the associated companion application's");
            pw.println("      CompanionDeviceService(s) and trigger onDeviceAppeared() callback.");
            pw.println("      The CDM will consider the devices as present for"
                    + "60 seconds and then");
            pw.println("      will act as if device disappeared, unless"
                    + "'simulate-device-disappeared'");
            pw.println("      or 'simulate-device-appeared' is called again before 60 seconds"
                    + "run out.");
            pw.println("    Case(1): ");
            pw.println("      Make CDM act as if the given companion device has disappeared.");
            pw.println("      I.e. unbind the associated companion application's");
            pw.println("      CompanionDeviceService(s) and trigger onDeviceDisappeared()"
                    + "callback.");
            pw.println("      NOTE: This will only have effect if 'simulate-device-appeared' was");
            pw.println("      invoked for the same device (same ASSOCIATION_ID) no longer than");
            pw.println("      60 seconds ago.");
            pw.println("    Case(2): ");
            pw.println("      Make CDM act as if the given companion device is BT connected ");
            pw.println("    Case(3): ");
            pw.println("      Make CDM act as if the given companion device is BT disconnected ");
            pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");
        }

        pw.println("  remove-inactive-associations");
        pw.println("      Remove self-managed associations that have not been active ");
        pw.println("      for a long time (90 days or as configured via ");
        pw.println("      \"debug.cdm.cdmservice.removal_time_window\" system property). ");
        pw.println("      USE FOR DEBUGGING AND/OR TESTING PURPOSES ONLY.");

        pw.println("  create-emulated-transport <ASSOCIATION_ID>");
        pw.println("      Create an EmulatedTransport for testing purposes only");

        pw.println("  enable-perm-sync <ASSOCIATION_ID>");
        pw.println("      Enable perm sync for the association.");
        pw.println("  disable-perm-sync <ASSOCIATION_ID>");
        pw.println("      Disable perm sync for the association.");
        pw.println("  get-perm-sync-state <ASSOCIATION_ID>");
        pw.println("      Get perm sync state for the association.");
        pw.println("  remove-perm-sync-state <ASSOCIATION_ID>");
        pw.println("      Remove perm sync state for the association.");
    }
}
