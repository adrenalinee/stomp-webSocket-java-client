package org.adrenalinee.stomp.websocketClient;

/**
 * 
 * @author 신동성
 * @since 2016. 5. 9.
 */
public interface WebsocketEventHandler {
	
	void onOpen();

	void onMessage(String message);

	void onClose(int code, String reason, boolean remote);

	void onError(Exception ex);
}
