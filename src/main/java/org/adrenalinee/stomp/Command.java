package org.adrenalinee.stomp;

/**
 * STOMP client frames
 * @author shindongseong
 * @since 2015. 11. 6.
 */
public enum Command {
	CONNECT,
	CONNECTED,
	MESSAGE,
	RECEIPT,
	ERROR,
	DISCONNECT,
	SEND,
	SUBSCRIBE,
	UNSUBSCRIBE,
	ACK,
	NACK,
	COMMIT,
	ABORT,
	BEGIN
}
