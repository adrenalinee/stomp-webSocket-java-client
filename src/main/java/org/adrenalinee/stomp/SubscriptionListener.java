package org.adrenalinee.stomp;

import java.util.Map;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public interface SubscriptionListener {
	
	void onMessage(Map<String, String> headers, String body);
}
