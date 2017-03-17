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
import java.net.InetSocketAddress;
import java.net.Socket;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.event.ClientConnectionErrorEvent;
import ac.robinson.bettertogether.event.ClientConnectionSuccessEvent;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.EventType;

class WifiClientConnection extends RemoteConnection {

	private static final String TAG = "WifiClientConnection";

	private boolean mRunning = false;

	private String mHost;
	private int mPort;

	private Socket mSocket;
	private InputStream mInputStream;
	private OutputStreamWriter mOutputStreamWriter;

	WifiClientConnection(String host, int port) {
		setLogTag(TAG);
		mHost = host;
		mPort = port;
	}

	@Override
	public void run() {
		mRunning = true;

		Log.d(TAG, "Starting Wifi client - connecting to " + mHost + " on port " + mPort);
		int errorCount = 0;
		while (mInputStream == null) {
			try {
				if (mSocket != null) {
					mSocket.close();
				}
				mSocket = new Socket(); // TODO: use SSLSocket at some point in the future? (would require version handling...)
				mSocket.bind(null);
				mSocket.connect((new InetSocketAddress(mHost, mPort)), 500);
				mInputStream = mSocket.getInputStream();
			} catch (Exception e) {
				errorCount += 1;
				try {
					Thread.sleep(25);
				} catch (Exception ignored) {
				}
				if (errorCount > 200) {
					Log.d(TAG, "Multiple Wifi socket connection exceptions - aborting");
					EventBus.getDefault().post(new ClientConnectionErrorEvent(EventType.Type.WIFI));
					return;
				}
			}
		}

		Log.d(TAG, "Wifi client connected to " + mHost + " on port " + mPort + " - thread: " + this.toString());
		EventBus.getDefault().post(new ClientConnectionSuccessEvent(EventType.Type.WIFI));

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
			Log.e(TAG, "Wifi client error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.WIFI));
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
