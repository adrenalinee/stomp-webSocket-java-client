package org.adrenalinee.stomp.listener;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 22.
 */
public interface WebSocketCloseListener {
	
	void onClose(int code, String reason, boolean remote) throws Exception;
}
