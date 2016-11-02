package ac.robinson.bettertogether.hotspot;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.Closeable;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;

import ac.robinson.bettertogether.event.BroadcastMessage;
import ac.robinson.bettertogether.event.ClientMessageErrorEvent;
import ac.robinson.bettertogether.event.EventType;
import ac.robinson.bettertogether.event.MessageReceivedEvent;

public abstract class RemoteConnection implements Runnable {

	// all remote connections (WiFi and Bluetooth) extend this class, sharing the same list of message parts
	private static HashMap<String, String> sMessageParts = new HashMap<>();
	private String TAG;

	protected void setLogTag(String logTag) {
		TAG = logTag;
	}

	protected boolean sendMessage(OutputStreamWriter streamWriter, String message) {
		try {
			if (streamWriter != null) {
				String formatString = "%0" + HotspotManagerService.MESSAGE_PART_COUNT_SIZE + "d";
				if (message.length() < HotspotManagerService.MESSAGE_PAYLOAD_SIZE) {
					streamWriter.write(HotspotManagerService.getRandomShortUUID() + String.format(formatString, 1) + String
							.format(formatString, 0) + message + HotspotManagerService.MESSAGE_DELIMITER_STRING);
					streamWriter.flush();
					return true;
				} else {
					List<String> messageParts = BroadcastMessage.splitEqually(message, HotspotManagerService
							.MESSAGE_PAYLOAD_SIZE);
					int totalParts = messageParts.size();
					int partSizeLimit = (int) Math.pow(10, HotspotManagerService.MESSAGE_PART_COUNT_SIZE) - 1;
					if (totalParts < partSizeLimit) {
						String totalPartsString = String.format(formatString, totalParts);
						String messageId = HotspotManagerService.getRandomShortUUID();
						int partNumber = 0;
						for (String part : messageParts) {
							streamWriter.write(messageId + totalPartsString + String.format(formatString, partNumber) + part +
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
			Log.e(TAG, "Error sending message: " + e.getLocalizedMessage());
			EventBus.getDefault().post(new ClientMessageErrorEvent(EventType.Type.UNKNOWN));
		}
		return false;
	}

	protected void processBytes(String connectionId, byte[] buffer, int bytesRead, StringBuilder stringBuilder) {
		if (bytesRead != -1) {
			int bufferStart = 0;
			for (int i = 0; i < bytesRead; i += 1) {
				if (buffer[i] == HotspotManagerService.MESSAGE_DELIMITER_BYTE) {
					stringBuilder.append(new String(buffer, bufferStart, i - bufferStart));
					receiveMessage(connectionId, stringBuilder.toString());
					bufferStart = i + 1;
					stringBuilder.setLength(0);
				}
			}
			if (bufferStart < bytesRead) {
				stringBuilder.append(new String(buffer, bufferStart, bytesRead - bufferStart));
			}
		}
	}

	// messages are split into parts if they are larger than the buffer - here we recombine
	protected void receiveMessage(String connectionId, String message) {
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

	protected void closeConnection(Closeable connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (Exception ignored) {
			}
		}
	}
}
