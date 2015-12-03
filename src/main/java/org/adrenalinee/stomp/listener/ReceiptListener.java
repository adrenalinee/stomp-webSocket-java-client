package org.adrenalinee.stomp.listener;

import org.adrenalinee.stomp.Frame;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 22.
 */
public interface ReceiptListener {
	
	void onReceived(Frame frame);
}
