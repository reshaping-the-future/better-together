package ac.robinson.bettertogether.event;

public class ServerErrorEvent {
	public EventType.Type mType;

	public ServerErrorEvent(EventType.Type type) {
		mType = type;
	}
}
