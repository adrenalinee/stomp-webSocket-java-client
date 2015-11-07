package org.adrenalinee.stomp.listener;

import org.adrenalinee.stomp.Frame;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public interface SubscribeListener {
	
	void onMessage(Frame frame);
}
