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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.api.messaging.PluginIntent;

class HotspotManagerServiceCommunicator {

	private boolean mIsBound;

	private Messenger mService = null;
	private final Messenger mMessenger;
	private HotspotServiceCallback mCallback;

	private ArrayList<Message> mQueuedMessages = new ArrayList<>();

	interface HotspotServiceCallback {
		void onBroadcastMessageReceived(BroadcastMessage message);

		void onSystemMessageReceived(int type, String data);
	}

	HotspotManagerServiceCommunicator(HotspotServiceCallback callback) {
		mMessenger = new Messenger(new IncomingHandler(HotspotManagerServiceCommunicator.this));
		mCallback = callback;
	}

	private static class IncomingHandler extends Handler {
		private final WeakReference<HotspotManagerServiceCommunicator> mCommunicatorReference; // allow garbage collection

		IncomingHandler(HotspotManagerServiceCommunicator instance) {
			mCommunicatorReference = new WeakReference<>(instance);
		}

		@Override
		public void handleMessage(Message msg) {
			HotspotManagerServiceCommunicator mCommunicator = mCommunicatorReference.get();
			if (mCommunicator == null) {
				// TODO: anything to do here?
				return;
			}

			switch (msg.what) {
				case HotspotManagerService.MSG_BROADCAST:
					BroadcastMessage message = (BroadcastMessage) msg.getData()
							.getSerializable(PluginIntent.KEY_BROADCAST_MESSAGE);
					mCommunicator.mCallback.onBroadcastMessageReceived(message);
					break;

				default:
					mCommunicator.mCallback.onSystemMessageReceived(msg.what, msg.getData()
							.getString(PluginIntent.KEY_SERVICE_MESSAGE));
					break;
			}
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			try {
				Message message = Message.obtain(null, HotspotManagerService.MSG_REGISTER_CLIENT);
				message.replyTo = mMessenger;
				mService.send(message);
				for (Message queuedMessage : mQueuedMessages) {
					mService.send(queuedMessage);
				}
			} catch (RemoteException ignored) {
				// crashed before started - will reconnect
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null; // unexpectedly disconnected - e.g., crashed
		}
	};

	// broadcast messages are to remote clients
	boolean sendBroadcastMessage(BroadcastMessage data) {
		try {
			Message message = Message.obtain(null, HotspotManagerService.MSG_BROADCAST);
			message.replyTo = mMessenger;
			Bundle bundle = new Bundle(1);
			bundle.putSerializable(PluginIntent.KEY_BROADCAST_MESSAGE, data);
			message.setData(bundle);
			if (mService != null) {
				mService.send(message);
			} else {
				mQueuedMessages.add(message);
			}
			return true;
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		}
	}

	// system messages are to the HotspotManagerService
	boolean sendSystemMessage(int type, String data) {
		try {
			Message message = Message.obtain(null, type);
			message.replyTo = mMessenger;
			Bundle bundle = new Bundle(1);
			bundle.putString(PluginIntent.KEY_SERVICE_MESSAGE, data);
			message.setData(bundle);
			if (mService != null) {
				mService.send(message);
			} else {
				mQueuedMessages.add(message);
			}
			return true;
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		}
	}

	// connect to the service
	void bindService(Context context) {
		context.startService(new Intent(context, HotspotManagerService.class));
		context.bindService(new Intent(context, HotspotManagerService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	// disconnect from the service
	void unbindService(Context context, boolean keepServiceAlive) {
		if (mIsBound) {
			if (mService != null) {
				try {
					Message message = Message.obtain(null, HotspotManagerService.MSG_UNREGISTER_CLIENT);
					message.replyTo = mMessenger;
					message.arg1 = keepServiceAlive ? 1 : 0;
					mService.send(message);
				} catch (RemoteException ignored) {
					// crashed - nothing to do
				}
			}
			context.unbindService(mConnection);
			mIsBound = false;
		}
	}
}
