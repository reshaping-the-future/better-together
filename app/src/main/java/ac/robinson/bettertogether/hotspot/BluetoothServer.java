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
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ac.robinson.bettertogether.BetterTogetherUtils;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerErrorEvent;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;

class BluetoothServer implements Runnable {

	private static final String TAG = "BluetoothServer";

	private boolean mRunning = false;

	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothServerSocket mServerSocket;
	private HashMap<String, BluetoothServerConnection> mConnectedSockets = new HashMap<>();

	BluetoothServer(BluetoothAdapter adapter) {
		mBluetoothAdapter = adapter;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Starting Bluetooth server");
			mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(mBluetoothAdapter.getName(),
					HotspotManagerService.BLUETOOTH_SERVER_UUID);

			try {
				while (mRunning) {
					BluetoothSocket acceptedSocket = mServerSocket.accept();

					String newConnectionId = BetterTogetherUtils.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE);
					BluetoothServerConnection connectedServer = new BluetoothServerConnection(newConnectionId, acceptedSocket);
					new Thread(connectedServer).start();
					mConnectedSockets.put(newConnectionId, connectedServer);
				}
			} catch (IOException e) {
				Log.e(TAG, "Bluetooth server client error: " + e.getLocalizedMessage());
				EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.BLUETOOTH));
			}

		} catch (IOException e) {
			Log.e(TAG, "Bluetooth server error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.BLUETOOTH));
		}
	}

	void sendMessageToAll(String message, @Nullable String ignoreClient) {
		for (Iterator<Map.Entry<String, BluetoothServerConnection>> connectedSocketsIterator = mConnectedSockets.entrySet()
				.iterator(); connectedSocketsIterator.hasNext(); ) {
			Map.Entry<String, BluetoothServerConnection> connection = connectedSocketsIterator.next();
			if (!connection.getKey().equals(ignoreClient)) { // send to all except the single ignored client
				if (!connection.getValue().sendMessage(message)) {
					Log.d(TAG, "Error sending message to client - removing dead client socket");
					connectedSocketsIterator.remove(); // remove dead connection (client failed)
					EventBus.getDefault().post(new ServerMessageErrorEvent(EventType.Type.BLUETOOTH));
				}
			}
		}
	}

	void closeAllConnections() {
		mRunning = false;
		if (mServerSocket != null) {
			try {
				mServerSocket.close();
				mServerSocket = null;
			} catch (Exception ignored) {
			}
		}
		for (Map.Entry<String, BluetoothServerConnection> connection : mConnectedSockets.entrySet()) {
			connection.getValue().closeConnection();
		}
	}
}
