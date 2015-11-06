package org.adrenalinee.stomp;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public class Subscription {
	
	private String id;
	
	private String destination;
	
	SubscriptionListener listener;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public SubscriptionListener getListener() {
		return listener;
	}

	public void setListener(SubscriptionListener listener) {
		this.listener = listener;
	}
	
	
}
