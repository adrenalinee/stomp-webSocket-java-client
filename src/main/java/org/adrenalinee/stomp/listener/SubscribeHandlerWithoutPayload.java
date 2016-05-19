package org.adrenalinee.stomp.listener;

import org.adrenalinee.stomp.StompHeaders;

public interface SubscribeHandlerWithoutPayload {
	void onReceived(StompHeaders stompHeaders);
}
