package ac.robinson.bettertogether.event;

public class ClientMessageErrorEvent {
	public EventType.Type mType;

	public ClientMessageErrorEvent(EventType.Type type) {
		mType = type;
	}
}
