package ac.robinson.bettertogether.event;

public class ClientConnectionSuccessEvent {
	public EventType.Type mType;

	public ClientConnectionSuccessEvent(EventType.Type type) {
		mType = type;
	}
}
