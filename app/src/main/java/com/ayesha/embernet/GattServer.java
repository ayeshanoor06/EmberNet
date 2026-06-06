
package com.ayesha.embernet;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.UUID;

public class GattServer {

    private static final String TAG = "GattServer";

    // Characteristic UUID for the SOS payload
    public static final UUID SOS_CHARACTERISTIC_UUID =
            UUID.fromString("0000EA02-0000-1000-8000-00805F9B34FB");

    private final Context              context;
    private       BluetoothGattServer  gattServer;
    private       byte[]               currentPayload;
    private       boolean              isRunning = false;

    public GattServer(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Start GATT server

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void start(byte[] initialPayload) {
        this.currentPayload = initialPayload;

        BluetoothManager btManager = (BluetoothManager)
                context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager == null) return;

        gattServer = btManager.openGattServer(
                context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Could not open GATT server");
            return;
        }

        // Build the GATT service
        BluetoothGattService service = new BluetoothGattService(
                BleAdvertiser.EMBERNET_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
        );

        // SOS characteristic — readable by any connecting scanner
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(
                        SOS_CHARACTERISTIC_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                );
        characteristic.setValue(currentPayload);
        service.addCharacteristic(characteristic);

        gattServer.addService(service);
        isRunning = true;
        Log.d(TAG, "GATT server started — payload size="
                + currentPayload.length);
    }

    // Update payload when GPS or battery changes
    public void updatePayload(byte[] newPayload) {
        this.currentPayload = newPayload;
        if (gattServer == null) return;

        BluetoothGattService service = gattServer.getService(
                BleAdvertiser.EMBERNET_SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(SOS_CHARACTERISTIC_UUID);
        if (characteristic != null) {
            characteristic.setValue(newPayload);
            Log.d(TAG, "GATT payload updated — "
                    + newPayload.length + " bytes");
        }
    }

    // ── Stop GATT server

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void stop() {
        if (gattServer != null) {
            gattServer.clearServices();
            gattServer.close();
            gattServer = null;
        }
        isRunning = false;
        Log.d(TAG, "GATT server stopped");
    }

    // ── GATT callbacks

    private final BluetoothGattServerCallback gattServerCallback =
            new BluetoothGattServerCallback() {

                @Override
                public void onConnectionStateChange(BluetoothDevice device,
                                                    int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Device connected: "
                                + device.getAddress());
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Device disconnected: "
                                + device.getAddress());
                    }
                }

                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                @Override
                public void onCharacteristicReadRequest(
                        BluetoothDevice device, int requestId,
                        int offset, BluetoothGattCharacteristic characteristic) {

                    if (SOS_CHARACTERISTIC_UUID.equals(
                            characteristic.getUuid())) {
                        Log.d(TAG, "Read request from "
                                + device.getAddress()
                                + " offset=" + offset);

                        // Handle offset reads for large payloads
                        byte[] value = currentPayload;
                        if (offset > 0 && offset < value.length) {
                            byte[] remaining = new byte[value.length - offset];
                            System.arraycopy(value, offset,
                                    remaining, 0, remaining.length);
                            value = remaining;
                        }

                        gattServer.sendResponse(device, requestId,
                                BluetoothGatt.GATT_SUCCESS, offset, value);
                    } else {
                        gattServer.sendResponse(device, requestId,
                                BluetoothGatt.GATT_FAILURE, 0, null);
                    }
                }
            };

    public boolean isRunning() { return isRunning; }
}