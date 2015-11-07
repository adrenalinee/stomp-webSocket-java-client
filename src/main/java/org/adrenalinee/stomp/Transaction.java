package org.adrenalinee.stomp;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 7.
 */
public class Transaction {
	
	String id;
	
	StompClient stompClient;
	
	Transaction(StompClient StompClient) {
		this.stompClient = StompClient;
	}
	
	public void commit() {
		stompClient.commit(id);
	}
	
	public void abort() {
		stompClient.abort(id);
	}
}
