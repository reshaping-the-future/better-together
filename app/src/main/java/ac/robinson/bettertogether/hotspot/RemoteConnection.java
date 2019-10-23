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

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.Closeable;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import ac.robinson.bettertogether.BetterTogetherUtils;
import ac.robinson.bettertogether.api.messaging.BroadcastMessage;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.MessageReceivedEvent;

abstract class RemoteConnection implements Runnable {

	// all remote connections (Wifi and Bluetooth) extend this class, sharing the same list of message parts
	private static HashMap<String, String> sMessageParts = new HashMap<>();
	private String TAG;

	void setLogTag(String logTag) {
		TAG = logTag;
	}

	boolean sendMessage(OutputStreamWriter streamWriter, String message) {
		try {
			if (streamWriter != null) {
				String formatString = "%0" + HotspotManagerService.MESSAGE_PART_COUNT_SIZE + "d";
				if (message.length() < HotspotManagerService.MESSAGE_PAYLOAD_SIZE) {
					streamWriter.write(BetterTogetherUtils.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE) +
							String.format(Locale.US, formatString, 1) + String.format(Locale.US, formatString, 0) + message +
							HotspotManagerService.MESSAGE_DELIMITER_STRING);
					streamWriter.flush();
					return true;
				} else {
					List<String> messageParts = BroadcastMessage.splitEqually(message,
							HotspotManagerService.MESSAGE_PAYLOAD_SIZE);
					int totalParts = messageParts.size();
					int partSizeLimit = (int) Math.pow(10, HotspotManagerService.MESSAGE_PART_COUNT_SIZE) - 1;
					if (totalParts < partSizeLimit) {
						String totalPartsString = String.format(Locale.US, formatString, totalParts);
						String messageId = BetterTogetherUtils.getRandomString(HotspotManagerService.MESSAGE_ID_SIZE);
						int partNumber = 0;
						for (String part : messageParts) {
							streamWriter.write(
									messageId + totalPartsString + String.format(Locale.US, formatString, partNumber) + part +
											HotspotManagerService.MESSAGE_DELIMITER_STRING);
							partNumber += 1;
						}
						streamWriter.flush();
						return true;
					} else {
						Log.e(TAG, "Error sending message: too large (" + totalParts + " parts; limit: " + partSizeLimit + ")");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "Error sending message: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.UNKNOWN));
		}
		return false;
	}

	void processBytes(String connectionId, byte[] buffer, int bytesRead, StringBuilder stringBuilder) {
		if (bytesRead != -1) {
			int bufferStart = 0;
			for (int i = 0; i < bytesRead; i += 1) {
				if (buffer[i] == HotspotManagerService.MESSAGE_DELIMITER_BYTE) {
					stringBuilder.append(new String(buffer, bufferStart, i - bufferStart, BroadcastMessage.CHARSET));
					receiveMessage(connectionId, stringBuilder.toString());
					bufferStart = i + 1;
					stringBuilder.setLength(0);
				}
			}
			if (bufferStart < bytesRead) {
				stringBuilder.append(new String(buffer, bufferStart, bytesRead - bufferStart, BroadcastMessage.CHARSET));
			}
		}
	}

	// messages are split into parts if they are larger than the buffer - here we recombine
	private void receiveMessage(String connectionId, String message) {
		int partCountSize = HotspotManagerService.MESSAGE_ID_SIZE + HotspotManagerService.MESSAGE_PART_COUNT_SIZE;
		if (message.length() > HotspotManagerService.MESSAGE_HEADER_SIZE) {
			try {
				String messageId = message.substring(0, HotspotManagerService.MESSAGE_ID_SIZE);
				int partCount = Integer.parseInt(message.substring(HotspotManagerService.MESSAGE_ID_SIZE, partCountSize), 10);
				int partNum = Integer.parseInt(message.substring(partCountSize, HotspotManagerService.MESSAGE_HEADER_SIZE), 10);
				String messageText = message.substring(HotspotManagerService.MESSAGE_HEADER_SIZE);

				if (partCount == 1) {
					EventBus.getDefault().post(new MessageReceivedEvent(connectionId, messageText));
				} else {
					// we trust that messages arrive in the correct order (e.g., no actual checking on partNum)
					String currentMessage = sMessageParts.get(messageId);
					currentMessage = (currentMessage == null ? "" : currentMessage) + messageText;
					if (partNum < partCount - 1) { // count is zero-based
						sMessageParts.put(messageId, currentMessage);
					} else {
						EventBus.getDefault().post(new MessageReceivedEvent(connectionId, currentMessage));
					}
				}
			} catch (NumberFormatException e) {
				Log.e(TAG, "Error receiving message: invalid part count - ignoring");
			}
		} else {
			Log.e(TAG, "Error receiving message: message body not found");
		}
	}

	void closeConnection(Closeable connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (Exception ignored) {
			}
		}
	}
}
