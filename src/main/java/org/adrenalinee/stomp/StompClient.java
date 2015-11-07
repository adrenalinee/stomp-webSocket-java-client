package org.adrenalinee.stomp;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.adrenalinee.stomp.listener.ConnectListnener;
import org.adrenalinee.stomp.listener.DisconnectListener;
import org.adrenalinee.stomp.listener.ErrorListener;
import org.adrenalinee.stomp.listener.SubscribeListener;
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
	
	private void setupHeartbeat(Map<String, String> headers) {
		String heartbeatValue = headers.get("heart-beat");
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
	
//	public void parseConnect() {
//		
//	}
	
	public void connect(final ConnectListnener connectionLintener, final ErrorListener errorListener) {
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
				logger.info("<<< {}", message);
				
				Frame frame = Frame.unmarshall(message);
				if (Command.CONNECTED.equals(frame.getCommand())) {
					
					connected = true;
					setupHeartbeat(frame.getHeaders());
					if (connectionLintener != null) {
						try {
							connectionLintener.onConnect();
						} catch (Exception e) {
							logger.error("onConnect error url: " + url, e);
						}
					}
				} else if (Command.MESSAGE.equals(frame.getCommand())) {
					serverActivity = System.currentTimeMillis();
					if (Frame.LF.equals(message)) {
						logger.info("<<< PONG");
						return;
					}
					
					String subscription = frame.getHeaders().get("subscription");
					frame.setStompClient(StompClient.this);
					SubscribeListener subscriptionListener = subscriptions.get(subscription).getListener();
					if (connectionLintener != null) {
						try {
							subscriptionListener.onMessage(frame);
						} catch (Exception e) {
							logger.error("onMessage error subscrition id: " + subscription, e);
						}
					} else {
						logger.warn("Unhandled received MESSAGE: " + frame);
					}
				} else if (Command.RECEIPT.equals(frame.getCommand())) {
					//TODO
				} else if (Command.ERROR.equals(frame.getCommand())) {
					if (errorListener != null) {
						try {
							errorListener.onError(frame);
						} catch(Exception e) {
							logger.error("onErrorCallback error frame: " + frame, e);
						}
					}
				} else {
					logger.warn("Unhandled frame: " + frame);
				}
				
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				logger.warn("Whoops! Lost connection to "  + connection.getUrl());
				cleanUp();
				if (disconnectListener != null) {
					try {
						disconnectListener.onDisconnect();
					} catch (Exception e) {
						logger.error("onDisconnect error. url: " + connection.getUrl());
					}
				}
			}

			@Override
			public void onError(Exception ex) {
				logger.error("websocket error", ex);
				
			}
			
		};
		
		webSocketClient.connect();
	}
	
	public void disconnect(DisconnectListener listener) {
		this.disconnectListener = listener;
		transmit(Command.DISCONNECT, null, null);
		webSocketClient.close();
	}
	
	public void cleanUp() {
		if (pinger != null) {
			pinger.cancel();
		}
		if (ponger != null) {
			ponger.cancel();
		}
		disconnectListener = null;
		connected = false;
	}
	
	public void send(String destination, Map<String, String> headers, String body) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.put("destination", destination);
		if (headers != null) {
			realHeader.putAll(headers);
		}
		
		transmit(Command.SEND, realHeader, body);
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
	
	public void ack(String messageID, String subscription, Map<String, String> headers) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.putAll(headers);
		realHeader.put("message-id", messageID);
		realHeader.put("subscription", subscription);
		
		transmit(Command.ACK, realHeader, null);
	}
	
	public void nack(String messageID, String subscription, Map<String, String> headers) {
		Map<String, String> realHeader = new TreeMap<String, String>();
		realHeader.putAll(headers);
		realHeader.put("message-id", messageID);
		realHeader.put("subscription", subscription);
		
		transmit(Command.NACK, realHeader, null);
	}

	public boolean isConnected() {
		return connected;
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