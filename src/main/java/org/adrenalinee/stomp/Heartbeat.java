package org.adrenalinee.stomp;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 7.
 */
public class Heartbeat {
	int outgoing = 10000;
	
	int incoming = 10000;

	public int getOutgoing() {
		return outgoing;
	}

	public void setOutgoing(int outgoing) {
		this.outgoing = outgoing;
	}

	public int getIncoming() {
		return incoming;
	}

	public void setIncoming(int incoming) {
		this.incoming = incoming;
	}
	
}
