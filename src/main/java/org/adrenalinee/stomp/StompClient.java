package org.adrenalinee.stomp;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.adrenalinee.stomp.listener.ConnectedListnener;
import org.adrenalinee.stomp.listener.DisconnectListener;
import org.adrenalinee.stomp.listener.ErrorListener;
import org.adrenalinee.stomp.listener.ReceiptListener;
import org.adrenalinee.stomp.listener.SubscribeListener;
import org.adrenalinee.stomp.listener.WebScoketErrorListener;
import org.adrenalinee.stomp.listener.WebSocketCloseListener;
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
	private int maxWebSocketFrameSize = 16 * 1024;
	
	private Map<String, Subscription> subscriptions = new LinkedHashMap<String, Subscription>();
	
	private int subscriptionCount;
	
	private int transactionCount;
	
	private Connection connection;
	
	private DisconnectListener disconnectListener;
	
	private boolean connected;
	
	private Heartbeat heartbeat = new Heartbeat();
	
	private Timer pinger;
	
	private Timer ponger;
	
	private long serverActivity;
	
	private ErrorListener errorListener;
	
	private ReceiptListener receiptListener;
	
	private WebScoketErrorListener webScoketErrorListener;
	
	private WebSocketCloseListener webSocketCloseListener;
	
	
	/**
	 * StompClient instance create by clientOverWebsocket()
	 */
	private StompClient() {
		
	}
	
	
	public static StompClient clientOverWebsocket(String url) {
		return clientOverWebsocket(url, null);
	}
	
	public static StompClient clientOverWebsocket(String url, HttpHeaders httpHeaders) {
		StompClient stompClient = new StompClient();
		stompClient.connection = new Connection(url, httpHeaders.getHeaders());
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
	
	private void setupHeartbeat(StompHeaders headers) {
		String heartbeatValue = headers.getHeartBeat();
		if (heartbeatValue == null || "".equals(heartbeatValue)) {
			return;
		}
		
		String[] heartbeats = heartbeatValue.split(",");
		int serverOutgoing = Integer.parseInt(heartbeats[0]);
		int serverIncoming = Integer.parseInt(heartbeats[1]);
		
		if (!(heartbeat.getOutgoing() == 0 || serverIncoming == 0)) {
			int ttl = Math.max(heartbeat.getOutgoing(), serverIncoming);
			logger.debug("send PING every " + ttl + "ms");
			
			//pinger 설정
			pinger = new Timer();
			pinger.schedule(new TimerTask() {
				Logger logger = LoggerFactory.getLogger(getClass());
				
				@Override
				public void run() {
					webSocketClient.send(Frame.LF);
					logger.info(">>> PING");
				}
			}, ttl);
		}
		
		
		if (!(heartbeat.getIncoming() == 0 || serverOutgoing == 0)) {
			final int ttl = Math.max(heartbeat.getIncoming(), serverOutgoing);
			logger.debug("check PONG every " + ttl + "ms");
			
			//ponger 설정
			ponger = new Timer();
			ponger.schedule(new TimerTask() {
				Logger logger = LoggerFactory.getLogger(getClass());
				
				@Override
				public void run() {
					long delta = System.currentTimeMillis() - serverActivity;
					if (delta > ttl * 2) {
						logger.warn("did not receive server activity for the last " + delta + "ms");
						
						webSocketClient.close();
					}
				}
			}, ttl);
		}
	}
	
	
	public void connect(final ConnectedListnener connectedLintener) {
		final String url = connection.getUrl();
		final Map<String, String> headers = connection.getHeaders();
		final int connecttimeout =  connection.getConnecttimeout();
		
		webSocketClient = new WebSocketClient(URI.create(url), new Draft_17(), headers, connecttimeout) {
			
			@Override
			public void onOpen(ServerHandshake handshakedata) {
				logger.info("Web Socket Openned...");
				
				Map<String, String> messageHeader = new TreeMap<String, String>();
				messageHeader.put("accept-version", "1.1,1.0");
				messageHeader.put("heart-beat", heartbeat.getOutgoing() + "," + heartbeat.getIncoming());
				transmit(Command.CONNECT, messageHeader, null);
			}
			
			@Override
			public void onMessage(String message) {
				logger.info("<<< {}", message);
				
				Frame frame = Frame.unmarshall(message);
				if (Command.CONNECTED.equals(frame.getCommand())) {
					
					connected = true;
					setupHeartbeat(frame.getHeaders());
					if (connectedLintener != null) {
						try {
							connectedLintener.onConnected(frame);
						} catch (Exception e) {
							logger.error("onConnected error url: " + url, e);
						}
					} else {
						logger.warn("Unhandled received CONNECTED: " + frame);
					}
				} else if (Command.MESSAGE.equals(frame.getCommand())) {
					serverActivity = System.currentTimeMillis();
					if (Frame.LF.equals(message)) {
						logger.info("<<< PONG");
						return;
					}
					
					String subscription = frame.getHeaders().getSubscription();
					frame.setStompClient(StompClient.this);
					SubscribeListener subscriptionListener = subscriptions.get(subscription).getListener();
					if (connectedLintener != null) {
						try {
							subscriptionListener.onMessage(frame);
						} catch (Exception e) {
							logger.error("onMessage error subscrition id: " + subscription, e);
						}
					} else {
						logger.warn("Unhandled received MESSAGE: " + frame);
					}
				} else if (Command.RECEIPT.equals(frame.getCommand())) {
					if (receiptListener != null) {
						try {
							receiptListener.onReceipt(frame);
						}catch (Exception e) {
							logger.error("onReceipt error receitp id: " + frame.getHeaders().getReceiptId(), e);
						}
					} else {
						logger.warn("Unhandled received RECEIPT: " + frame);
					}
				} else if (Command.ERROR.equals(frame.getCommand())) {
					if (errorListener != null) {
						try {
							errorListener.onError(frame);
						} catch(Exception e) {
							logger.error("onErrorCallback error frame: " + frame, e);
						}
					} else {
						logger.warn("Unhandled received ERROR: " + frame);
					}
				} else {
					logger.warn("Unhandled frame: " + frame);
				}
			}
			
			@Override
			public void onClose(int code, String reason, boolean remote) {
				logger.warn("Whoops! Lost connection to "  + connection.getUrl());
				
				cleanUp();
				if (webSocketCloseListener != null) {
					try {
						webSocketCloseListener.onClose(code, reason, remote);
					} catch (Exception e) {
						logger.error("webSocketCloseListener.onClose() code: " + code +
								", reason: " + reason +
								", remote: " + remote,
								e);
					}
				} else {
					logger.warn("Unhandled onClose event. code: {}, reason: {}, remote: {}", code, reason, remote);
				}
			}
			
			@Override
			public void onError(Exception ex) {
				logger.error("websocket error", ex);
				if (webScoketErrorListener != null) {
					try {
						webScoketErrorListener.onError(ex);
					} catch (Exception e) {
						logger.error("webScoketErrorListener.onError() ex: " + ex,
								e);
					}
				} else {
					logger.warn("Unhandled onError event. ex: {}", ex);
				}
			}
		};
		
		logger.info("Opening Web Socket... url: {}" + url);
		webSocketClient.connect();
	}
	
	public void disconnect(DisconnectListener listener) {
		this.disconnectListener = listener;
		transmit(Command.DISCONNECT, null, null);
		webSocketClient.close();
	}
	
	private void cleanUp() {
		if (pinger != null) {
			pinger.cancel();
		}
		if (ponger != null) {
			ponger.cancel();
		}
		disconnectListener = null;
		connected = false;
	}
	
	public void send(String destination, String body) {
		send(destination, null, body);
	}
	
	public void send(String destination, StompHeaders stompHeaders, String body) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("destination", destination);
		if (stompHeaders != null) {
			headers.putAll(stompHeaders.getHeaders());
		}
		
		transmit(Command.SEND, headers, body);
	}
	
	public void subscribe(String destination, SubscribeListener listener) {
		Subscription subscription = new Subscription();
		subscription.setDestination(destination);
		subscription.setId("sub-" + subscriptionCount++);
		subscription.setListener(listener);
		subscriptions.put(subscription.getId(), subscription);
		
		subscribe(subscription);
	}
	
	private void subscribe(Subscription subscription) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("destination", subscription.getDestination());
		headers.put("id", subscription.getId());
		
		transmit(Command.SUBSCRIBE, headers, null);
	}
	
	
	public void unsubscribe(String id) {
		Subscription subscription = subscriptions.get(id);
		if (subscription == null) {
			logger.warn("not exist subscription id. id: {}", id);
			//TODO 구독정보에 해당 키가 없음을 알림
			return;
		}
		
		unsubscribe(subscription);
		subscriptions.remove(id);
	}
	
	private void unsubscribe(Subscription subscription) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("id", subscription.getId());
		
		transmit(Command.UNSUBSCRIBE, headers, null);
	}
	
	public Transaction bigen() {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("transaction", "tx-" + transactionCount++);
		
		transmit(Command.BEGIN, headers, null);
		
		return new Transaction(this);
	}
	
	public void commit(String transaction) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("transaction", transaction);
		
		transmit(Command.COMMIT, headers, null);
	}
	
	public void abort(String transaction) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("transaction", transaction);
		
		transmit(Command.ABORT, headers, null);
	}
	
	public void ack(String messageID, String subscription, StompHeaders stompHeaders) {
		Map<String, String> headers = stompHeaders.getHeaders();
		headers.put("message-id", messageID);
		headers.put("subscription", subscription);
		
		transmit(Command.ACK, headers, null);
	}
	
	public void nack(String messageID, String subscription, StompHeaders stompHeaders) {
		Map<String, String> headers = stompHeaders.getHeaders();
		headers.put("message-id", messageID);
		headers.put("subscription", subscription);
		
		transmit(Command.NACK, headers, null);
	}

	public boolean isConnected() {
		return connected;
	}

	public void setWebScoketErrorListener(WebScoketErrorListener webScoketErrorListener) {
		this.webScoketErrorListener = webScoketErrorListener;
	}

	public void setWebSocketCloseListener(WebSocketCloseListener webSocketCloseListener) {
		this.webSocketCloseListener = webSocketCloseListener;
	}

	public void setReceiptListener(ReceiptListener receiptListener) {
		this.receiptListener = receiptListener;
	}
}

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 7.
 */
class Heartbeat {
	int outgoing = 10000;
	
	int incoming = 10000;

	public int getOutgoing() {
		return outgoing;
	}

	public void setOutgoing(int outgoing) {
		this.outgoing = outgoing;
	}

	public int getIncoming() {
		return incoming;
	}

	public void setIncoming(int incoming) {
		this.incoming = incoming;
	}
	
}