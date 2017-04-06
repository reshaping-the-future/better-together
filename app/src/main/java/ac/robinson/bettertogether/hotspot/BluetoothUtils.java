/*
 * Copyright (C) 2017 The Better Together Toolkit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ac.robinson.bettertogether.hotspot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

class BluetoothUtils {
	private static final int BLUETOOTH_VISIBILITY_TIME = 600; // in seconds (10 minutes)

	static boolean isBluetoothAvailable(Context context) {
		return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
	}

	static BluetoothAdapter getBluetoothAdapter(Context context) {
		BluetoothAdapter bluetoothAdapter;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		} else {
			bluetoothAdapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
		}
		return bluetoothAdapter;
	}

	static void setDiscoverable(Context context, BluetoothAdapter mBluetoothAdapter) {
		// note - ideally we would disable Bluetooth visibility when disconnecting, but this is not currently possible without
		// resorting to hacky methods (e.g., set visibility to 1 second), which would also require another user prompt
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BLUETOOTH_VISIBILITY_TIME);
			discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // need to do this to start from service
			try {
				context.startActivity(discoverableIntent);
			} catch (Exception ignored) {
				// not much we can do - probably no Bluetooth adapter present
			}
		}
	}
}
