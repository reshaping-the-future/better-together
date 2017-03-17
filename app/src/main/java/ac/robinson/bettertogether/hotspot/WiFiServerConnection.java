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

import android.os.Build;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;

class WifiServerConnection extends RemoteConnection {

	private static final String TAG = "WifiServerConnection";

	private boolean mRunning = false;

	private String mId;
	private Socket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	WifiServerConnection(String id, Socket socket) {
		mId = id;
		mSocket = socket;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Wifi server connected to client");
			EventBus.getDefault().post(new ServerConnectionSuccessEvent(EventType.Type.WIFI));

			mInputStream = mSocket.getInputStream();
			mOutputStreamWriter = new OutputStreamWriter(mSocket.getOutputStream(), BroadcastMessage.CHARSET);

			int bufferSize = HotspotManagerService.MESSAGE_BUFFER_SIZE;
			byte[] buffer = new byte[bufferSize];
			int bytesRead;
			StringBuilder stringBuilder = new StringBuilder();

			while (mRunning) {
				bytesRead = mInputStream.read(buffer);
				processBytes(mId, buffer, bytesRead, stringBuilder);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Wifi server connection error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerMessageErrorEvent(EventType.Type.WIFI));
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			closeConnection(mSocket);
		} else {
			if (mSocket != null) {
				try {
					mSocket.close();
				} catch (Exception ignored) {
				}
			}
		}
		mSocket = null;
	}
}
