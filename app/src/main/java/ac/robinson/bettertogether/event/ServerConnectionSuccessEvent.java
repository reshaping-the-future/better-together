package ac.robinson.bettertogether.event;

public class ServerConnectionSuccessEvent {
	public EventType.Type mType;

	public ServerConnectionSuccessEvent(EventType.Type type) {
		mType = type;
	}
}
