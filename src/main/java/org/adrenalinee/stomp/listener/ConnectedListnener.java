package org.adrenalinee.stomp.listener;

import org.adrenalinee.stomp.StompHeaders;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public interface ConnectedListnener {
	
	void onConnected(StompHeaders stompHeaders);
}
