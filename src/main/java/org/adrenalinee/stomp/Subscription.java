package org.adrenalinee.stomp;

import org.adrenalinee.stomp.listener.SubscribeListener;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public class Subscription {
	
	private String id;
	
	private String destination;
	
	SubscribeListener listener;

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

	public SubscribeListener getListener() {
		return listener;
	}

	public void setListener(SubscribeListener listener) {
		this.listener = listener;
	}
	
	
}
