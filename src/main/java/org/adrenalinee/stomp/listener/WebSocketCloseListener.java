package org.adrenalinee.stomp.listener;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 22.
 */
public interface WebSocketCloseListener {
	
	/**
	 * https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
	 * 
	 * @param code
	 * @param reason
	 * @param remote
	 * @throws Exception
	 */
	void onClose(int code, String reason, boolean remote) throws Exception;
}
