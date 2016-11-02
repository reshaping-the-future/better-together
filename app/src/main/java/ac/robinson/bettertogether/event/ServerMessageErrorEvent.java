package ac.robinson.bettertogether.event;

public class ServerMessageErrorEvent {
	public EventType.Type mType;

	public ServerMessageErrorEvent(EventType.Type type) {
		mType = type;
	}
}
