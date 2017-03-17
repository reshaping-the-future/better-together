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

import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.net.ServerSocketFactory;

import ac.robinson.bettertogether.BetterTogetherUtils;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.ServerErrorEvent;
import ac.robinson.bettertogether.event.ServerMessageErrorEvent;

class WifiServer implements Runnable {

	private static final String TAG = "WifiServer";

	private boolean mRunning = false;

	private String mAddress;
	private int mPort;

	private ServerSocket mServerSocket;
	private HashMap<String, WifiServerConnection> mConnectedSockets = new HashMap<>();

	WifiServer(String address, int port) {
		mAddress = address;
		mPort = port;
	}

	@Override
	public void run() {
		mRunning = true;
		try {
			Log.d(TAG, "Starting Wifi server on port " + mPort);
			Thread.sleep(1000); // wait for hotspot to be initialised

			// TODO: use SSLServerSocket at some point in the future? (would require version handling...)
			mServerSocket = ServerSocketFactory.getDefault().createServerSocket(mPort, 0, InetAddress.getByName(mAddress));

			try {
				while (mRunning) {
					Socket acceptedSocket = mServerSocket.accept();

					String newConnectionId = BetterTogetherUtils.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE);
					WifiServerConnection connectedServer = new WifiServerConnection(newConnectionId, acceptedSocket);
					new Thread(connectedServer).start();
					mConnectedSockets.put(newConnectionId, connectedServer);
				}
			} catch (IOException e) {
				Log.e(TAG, "Wifi server client error: " + e.getLocalizedMessage());
				EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.WIFI));
			}

		} catch (IOException e) {
			Log.e(TAG, "Wifi server error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.WIFI));
		} catch (InterruptedException e) {
			Log.e(TAG, "Wifi server sleep error: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ServerErrorEvent(EventType.Type.WIFI));
		}
	}

	void sendMessageToAll(String message, @Nullable String ignoreClient) {
		for (Iterator<Map.Entry<String, WifiServerConnection>> connectedSocketsIterator = mConnectedSockets.entrySet().iterator
				(); connectedSocketsIterator.hasNext(); ) {
			Map.Entry<String, WifiServerConnection> connection = connectedSocketsIterator.next();
			if (!connection.getKey().equals(ignoreClient)) { // send to all except the single ignored client
				if (!connection.getValue().sendMessage(message)) {
					Log.d(TAG, "Error sending message to client - removing dead client socket");
					connectedSocketsIterator.remove(); // remove dead connection (client failed)
					EventBus.getDefault().post(new ServerMessageErrorEvent(EventType.Type.WIFI));
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
		for (Map.Entry<String, WifiServerConnection> connection : mConnectedSockets.entrySet()) {
			connection.getValue().closeConnection();
		}
	}
}
