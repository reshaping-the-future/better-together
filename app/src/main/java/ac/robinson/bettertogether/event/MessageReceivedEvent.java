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

package ac.robinson.bettertogether.event;

import android.util.Log;

import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.hotspot.HotspotManagerService;

public class MessageReceivedEvent {

	public final String mDeliveredBy;
	public final String mMessageSource;
	public final BroadcastMessage mMessage;

	private static final BroadcastMessage sErrorMessage = new BroadcastMessage(BroadcastMessage.TYPE_ERROR, "");

	static {
		sErrorMessage.setSystemMessage();
	}

	static {
		sErrorMessage.setFrom(HotspotManagerService.SERVER_MESSAGE_ID);
	}

	public MessageReceivedEvent(String deliveredBy, String message) {
		mDeliveredBy = deliveredBy;
		mMessageSource = message;

		BroadcastMessage decodedMessage = sErrorMessage; // we don't want null messages - use a default error message on failure
		try {
			decodedMessage = BroadcastMessage.fromString(message);
		} catch (Exception e) {
			Log.d("MessageReceivedEvent", "Message error: " + e.getLocalizedMessage()); // TODO: deal with this
		}
		if (!HotspotManagerService.SERVER_MESSAGE_ID.equals(deliveredBy)) {
			decodedMessage.setFrom(deliveredBy); // for messages received at the server, we can set the from id here
		}
		mMessage = decodedMessage;
	}
}
