package ac.robinson.bettertogether.event;

import android.util.Log;

import ac.robinson.bettertogether.hotspot.HotspotManagerService;

public class MessageReceivedEvent {

	public final String mDeliveredBy;
	public final String mMessageSource;
	public final BroadcastMessage mMessage;

	private static final BroadcastMessage sErrorMessage = new BroadcastMessage(BroadcastMessage.Type.INTERNAL, "");

	static {
		sErrorMessage.setFrom(HotspotManagerService.INTERNAL_SERVER_ID);
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
		if (!HotspotManagerService.INTERNAL_SERVER_ID.equals(deliveredBy)) {
			decodedMessage.setFrom(deliveredBy); // for messages received at the server, we can set the from id here
		}
		mMessage = decodedMessage;
	}
}
