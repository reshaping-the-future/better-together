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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.EventType;

class BluetoothClientConnection extends RemoteConnection {

	private static final String TAG = "BTClientConnection";

	private boolean mRunning = false;

	private BluetoothDevice mRemoteDevice;
	private BluetoothConnector mBluetoothConnector;

	private BluetoothSocket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	BluetoothClientConnection(BluetoothDevice remoteDevice) {
		setLogTag(TAG);
		mRemoteDevice = remoteDevice;
	}

	@Override
	public void run() {
		mRunning = true;

		// TODO: improve this initial connection in a similar way to the Wifi version (e.g., merge BluetoothConnector here)
		while (mInputStream == null) {
			mBluetoothConnector = new BluetoothConnector(mRemoteDevice, true, HotspotManagerService.BLUETOOTH_SERVER_UUID);
			try {
				mSocket = mBluetoothConnector.connect().getUnderlyingSocket();
				mInputStream = mSocket.getInputStream();
			} catch (Exception e1) {
				Log.d(TAG, "Bluetooth socket connection exception - aborting");
				EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.BLUETOOTH));
				return;
			}
		}
		if (mSocket == null) {
			Log.d(TAG, "Bluetooth socket setup failure - aborting");
			EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.BLUETOOTH));
			return;
		}

		Log.d(TAG, "Bluetooth client connected to " + mRemoteDevice.getName() + " - thread: " + this.toString());
		EventBus.getDefault().post(new ClientConnectionSuccessEvent(EventType.Type.BLUETOOTH));

		try {
			mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream(), BroadcastMessage.CHARSET);

			byte[] buffer = new byte[HotspotManagerService.MESSAGE_BUFFER_SIZE];
			int bytesRead;
			StringBuilder stringBuilder = new StringBuilder();

			while (mRunning) {
				bytesRead = mInputStream.read(buffer);
				processBytes(HotspotManagerService.SERVER_MESSAGE_ID, buffer, bytesRead, stringBuilder);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Bluetooth client error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.BLUETOOTH));
		}
	}

	boolean sendMessage(String message) {
		return sendMessage(mOutputStreamWriter, message);
	}

	void closeConnection() {
		mRunning = false;
		closeConnection(mInputStream);
		mInputStream = null;
		closeConnection(mOutputStreamWriter);
		mOutputStreamWriter = null;
		closeConnection(mSocket);
		mSocket = null;
		mBluetoothConnector.close();
		mBluetoothConnector = null;
	}
}
