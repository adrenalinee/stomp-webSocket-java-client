package org.adrenalinee.stomp;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 6.
 */
public class StompClient {
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private WebSocketClient webSocketClient;
	
	/**
	 * default = 16KB
	 */
	private int maxWebSocketFrameSize = 16*1024;
	
	private Map<String, Subscription> subscriptions = new LinkedHashMap<String, Subscription>();
	
	private int subscriptionCount;
	
	private Connection connection;
	
	
	private StompClient() {
		
	}
	
	public static StompClient clientOverWebsocket(String url, Map<String,String> headers) {
		StompClient stompClient = new StompClient();
		stompClient.connection = new Connection(url, headers);
		return stompClient;
	}
	
	private void transmit(Command command, Map<String, String> headers, String body) {
		String out = Frame.marshall(command, headers, body);
		logger.info(">>> {}", out);
		while (true) {
			if (out.length() > maxWebSocketFrameSize) {
				webSocketClient.send(out.substring(0, maxWebSocketFrameSize));
				out = out.substring(maxWebSocketFrameSize);
			} else {
				webSocketClient.send(out);
				break;
			}
		}
		
	}
	
	public void setupHeartbeat() {
		
	}
	
	public void parseConnect() {
		
	}
	
	public void connect(final ConnectionListnener connectionLintener) {
		logger.info("Opening Web Socket...");
		
		final String url = connection.getUrl();
		final Map<String, String> headers = connection.getHeaders();
		final int connecttimeout =  connection.getConnecttimeout();
		
		WebSocketClient webSocketClient = new WebSocketClient(URI.create(url), new Draft_17(), headers, connecttimeout) {

			@Override
			public void onOpen(ServerHandshake handshakedata) {
				logger.info("Web Socket Openned...");
				
				Map<String, String> messageHeader = new TreeMap<String, String>();
				messageHeader.put("accept-version", "1.1,1.0");
				messageHeader.put("heart-beat", "10000,10000");
				transmit(Command.CONNECT, messageHeader, null);
				
			}

			@Override
			public void onMessage(String message) {
				logger.info("<<< " + message);
				
				Frame frame = Frame.unmarshall(message);
				if (Command.CONNECTED.equals(frame.getCommand())) {
					if (connectionLintener != null) {
						try {
							connectionLintener.onConnect();
						} catch (Exception e) {
							logger.error("onConnect error url: " + url, e);
						}
					}
				} else if (Command.MESSAGE.equals(frame.getCommand())) {
					String subscription = frame.getHeaders().get("subscription");
					SubscriptionListener subscriptionListener =
							subscriptions.get(subscription).getListener();
					if (connectionLintener != null) {
						try {
							subscriptionListener.onMessage(frame.getHeaders(), frame.getBody());
						} catch (Exception e) {
							logger.error("onMessage error subscrition id: " + subscription, e);
						}
					}
				} else if (Command.RECEIPT.equals(frame.getCommand())) {
					//TODO
				} else if (Command.ERROR.equals(frame.getCommand())) {
					//TODO
				}
				
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				logger.warn("Whoops! Lost connection to "  + connection.getUrl());
				cleanUp();
			}

			@Override
			public void onError(Exception ex) {
				logger.error("websocket error", ex);
				
			}
			
		};
		
		webSocketClient.connect();
	}
	
	public void disconnect() {
		transmit(Command.DISCONNECT, null, null);
	}
	
	public void cleanUp() {
		
	}
	
	public void send(String destination, Map<String, String> headers, String body) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.put("destination", destination);
		if (headers != null) {
			realHeader.putAll(headers);
		}
		
		transmit(Command.SEND, realHeader, body);
	}
	
	public void subscribe(String destination, SubscriptionListener listener) {
		Subscription subscription = new Subscription();
		subscription.setDestination(destination);
		subscription.setId("sub-" + subscriptionCount++);
		subscription.setListener(listener);
		subscriptions.put(subscription.getId(), subscription);
		
		subscribe(subscription);
	}
	
	private void subscribe(Subscription subscription) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.put("destination", subscription.getDestination());
		realHeader.put("id", subscription.getId());
		
		transmit(Command.SUBSCRIBE, realHeader, null);
	}
	
	
	
	public void unsubscribe(String id) {
		Subscription subscription = subscriptions.get(id);
		if (subscription == null) {
			//TODO 구독정보에 해당 키가 없음을 알림
			return;
		}
		
		unsubscribe(subscription);
		subscriptions.remove(id);
	}
	
	private void unsubscribe(Subscription subscription) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.put("id", subscription.getId());
		
		transmit(Command.UNSUBSCRIBE, realHeader, null);
	}
	
	public void bigen() {
		
	}
	
	public void commit() {
		
	}
	
	public void abort() {
		
	}
	
	public void ack() {
		
	}
	
	public void nack() {
		
	}
}
