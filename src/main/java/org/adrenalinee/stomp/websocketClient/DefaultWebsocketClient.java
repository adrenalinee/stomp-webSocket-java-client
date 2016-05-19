package org.adrenalinee.stomp.websocketClient;

import java.net.URI;
import java.util.Map;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 
 * @author 신동성
 * @since 2016. 5. 9.
 */
public class DefaultWebsocketClient extends WebSocketClient {
	
	WebsocketEventHandler websocketEventHandler;
	
	
	public DefaultWebsocketClient(URI serverUri , Draft draft , Map<String,String> headers , int connecttimeout) {
		super(serverUri, draft, headers, connecttimeout);
	}
	
	@Override
	public void onMessage(String message) {
		websocketEventHandler.onMessage(message);
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		websocketEventHandler.onClose(code, reason, remote);
	}

	@Override
	public void onError(Exception ex) {
		websocketEventHandler.onError(ex);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		websocketEventHandler.onOpen();
	}

	public WebsocketEventHandler getWebsocketEventHandler() {
		return websocketEventHandler;
	}

	public void setWebsocketEventHandler(WebsocketEventHandler websocketEventHandler) {
		this.websocketEventHandler = websocketEventHandler;
	}

}
