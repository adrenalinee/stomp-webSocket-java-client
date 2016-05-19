package org.adrenalinee.stomp;

import java.net.URI;
import java.nio.channels.NotYetConnectedException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import org.adrenalinee.stomp.frame.Frame;
import org.adrenalinee.stomp.frame.FrameBodyConverter;
import org.adrenalinee.stomp.frame.GsonFrameBodyConverter;
import org.adrenalinee.stomp.listener.ConnectedListnener;
import org.adrenalinee.stomp.listener.DisconnectListener;
import org.adrenalinee.stomp.listener.ErrorListener;
import org.adrenalinee.stomp.listener.ReceiptListener;
import org.adrenalinee.stomp.listener.SubscribeHandler;
import org.adrenalinee.stomp.listener.SubscribeHandlerWithoutPayload;
import org.adrenalinee.stomp.listener.WebScoketErrorListener;
import org.adrenalinee.stomp.listener.WebSocketCloseListener;
import org.adrenalinee.stomp.websocketClient.DefaultWebsocketClient;
import org.adrenalinee.stomp.websocketClient.WebsocketEventHandler;
import org.java_websocket.drafts.Draft_17;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 6.
 */
public class StompClient {
	private Logger logger = LoggerFactory.getLogger(getClass());
	
//	private WebSocketClient webSocketClient;
	
	private DefaultWebsocketClient websocketClient;
	
	/**
	 * default = 16KB
	 */
	private int maxWebSocketFrameSize = 16 * 1024;
	
	/**
	 * key: subscriptionId
	 */
	private Map<String, Subscription> subscriptions = new LinkedHashMap<String, Subscription>();
	
	private Map<String, Receipt> receipts = new LinkedHashMap<String, Receipt>();
	
	private int subscriptionCount;
	
	private int transactionCount;
	
	private int receiptCount;
	
	private Connection connection;
	
	private boolean connected;
	
	private Heartbeat heartbeat = new Heartbeat();
	
	private Timer pinger;
	
	private Timer ponger;
	
	private long serverActivity;
	
	private FrameBodyConverter frameConverter;
	
	private DisconnectListener disconnectListener;
	
	private ErrorListener errorListener;
	
	private ReceiptListener globalReceiptListener;
	
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
		if (httpHeaders == null) {
			httpHeaders = new HttpHeaders();
		}
		
		StompClient stompClient = new StompClient();
		stompClient.connection = new Connection(url, httpHeaders.getHeaders());
		return stompClient;
	}
	
	public List<Subscription> getSubscriptions() {
		LinkedList<Subscription> result = new LinkedList<Subscription>();
		Iterator<Subscription> iter = subscriptions.values().iterator();
		while (iter.hasNext()) {
			result.add(iter.next());
		}
		
		return result;
	}
	
	private void transmit(Command command, Map<String, String> headers, String body) {
		if (!connected) {
			if (!Command.CONNECT.equals(command)) {
				logger.warn("not connected yet... command: {}", command);
				return;
			}
		}
		
		String out = Frame.marshall(command, headers, body);
		logger.debug(">>> {}", out);
		while (true) {
			if (out.length() > maxWebSocketFrameSize) {
				websocketClient.send(out.substring(0, maxWebSocketFrameSize));
				out = out.substring(maxWebSocketFrameSize);
			} else {
				websocketClient.send(out);
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
			logger.debug("send PING every {}ms", ttl);
			
			
			//pinger 설정
			pinger = new Timer();
			pinger.scheduleAtFixedRate(new TimerTask() {
//				Logger logger = LoggerFactory.getLogger(getClass());
				
				@Override
				public void run() {
					try {
						websocketClient.send(Frame.LF);
						logger.debug(">>> PING");
					} catch (NotYetConnectedException e) {
						logger.error("ping send fail. not yet connected.");
					}
				}
			}, ttl, ttl);
		}
		
		
		if (!(heartbeat.getIncoming() == 0 || serverOutgoing == 0)) {
			final int ttl = Math.max(heartbeat.getIncoming(), serverOutgoing);
			logger.debug("check PONG every {}ms", ttl);
			
			//ponger 설정
			ponger = new Timer();
			ponger.scheduleAtFixedRate(new TimerTask() {
//				Logger logger = LoggerFactory.getLogger(getClass());
				
				@Override
				public void run() {
					long delta = System.currentTimeMillis() - serverActivity;
					if (delta > ttl * 2) {
						//서버에서 응답이 없음. 서버와 접속이 끊긴것으로 간주
						
						logger.warn("did not receive server activity for the last {}ms", delta);
						
						if (websocketClient.getConnection().isConnecting()) {
							websocketClient.close();
						}
						cleanUp();
						//TODO 정확한 처리 동작 확인 필요
					}
				}
			}, ttl, ttl);
		}
	}
	
	
	public StompClient connect(final ConnectedListnener connectedLintener) {
		if (frameConverter == null) {
			frameConverter = new GsonFrameBodyConverter();
		}
		
		final String url = connection.getUrl();
		final Map<String, String> headers = connection.getHeaders();
		final int connecttimeout =  connection.getConnecttimeout();
		
		websocketClient = new DefaultWebsocketClient(URI.create(url), new Draft_17(), headers, connecttimeout);
		websocketClient.setWebsocketEventHandler(createEventHandler(connectedLintener));
		
		
//		if (url != null) {
//			if (url.startsWith("wss")) {
//				//TODO SSL 처리를 제대로 할 수 있는 방법 필요함. 현재는 우회 하는 방법으로 생각됨..
//				websocketClient.setWebSocketFactory(new DefaultSSLWebSocketClientFactory(createSSLContext()));
//			}
//		}
		
		logger.debug("Opening Web Socket... url: {}", url);
		websocketClient.connect();
		
		return this;
	}
	
	
	private WebsocketEventHandler createEventHandler(final ConnectedListnener connectedLintener) {
		return new WebsocketEventHandler() {
			@Override
			public void onOpen() {
				logger.debug("Web Socket Openned...");
				
				Map<String, String> messageHeader = new TreeMap<String, String>();
				messageHeader.put("accept-version", "1.1,1.0");
				messageHeader.put("heart-beat", heartbeat.getOutgoing() + "," + heartbeat.getIncoming());
				transmit(Command.CONNECT, messageHeader, null);
			}
			
			@Override
			public void onMessage(String message) {
				serverActivity = System.currentTimeMillis();
				if (Frame.LF.equals(message)) {
					logger.debug("<<< PONG");
					return;
				}
				
				logger.debug("<<< {}", message);
				
				Frame frame = Frame.unmarshall(message);
				if (Command.CONNECTED.equals(frame.getCommand())) {
					
					connected = true;
					setupHeartbeat(frame.getHeaders());
					if (connectedLintener != null) {
						try {
							connectedLintener.onConnected(frame.getHeaders());
						} catch (Exception e) {
							e.printStackTrace();
//							logger.error("onConnected error url: " + url, e);
						}
					} else {
						logger.warn("Unhandled received CONNECTED: {}", frame);
					}
				} else if (Command.MESSAGE.equals(frame.getCommand())) {
//					serverActivity = System.currentTimeMillis();
//					if (Frame.LF.equals(message)) {
//						logger.debug("<<< PONG");
//						return;
//					}
					
					String subscription = frame.getHeaders().getSubscription();
					frame.setStompClient(StompClient.this);
					SubscribeHandler subscriptionListener = subscriptions.get(subscription).getListener();
					SubscribeHandlerWithoutPayload subscribeHandlerWithoutPayload = subscriptions.get(subscription).getListenerWithoutPayload();
					if (subscriptionListener != null) {
						Class<?> targetClass = subscriptions.get(subscription).getTargetClass();
						Object body = frameConverter.fromFrame(frame.getBody(), targetClass);
						
						try {
							subscriptionListener.onReceived(body, frame.getHeaders());
						} catch (Exception e) {
							logger.error("onMessage error subscrition id: " + subscription, e);
						}
					} else if (subscribeHandlerWithoutPayload != null) {
						try {
							subscribeHandlerWithoutPayload.onReceived(frame.getHeaders());
						} catch (Exception e) {
							logger.error("onMessage error subscrition id: " + subscription, e);
						}
					} else {
						logger.warn("Unhandled received MESSAGE: {}", frame);
					}
					
					
				} else if (Command.RECEIPT.equals(frame.getCommand())) {
					String receiptId = frame.getHeaders().getReceiptId();
					Receipt receipt = receipts.remove(receiptId);
					if (receipt != null) {
						ReceiptListener receiptListener = receipt.getReceiptListener();
						if (receiptListener != null) {
							try {
								receiptListener.onReceived(frame);
							} catch (Exception e) {
								logger.error("receiptListener.onReceipt error. receipt id: " + receiptId, e);
							}
						} else {
							logger.warn("Unhandled received RECEIPT: " + frame);
						}
					} else {
						//global receipt listener
						if (globalReceiptListener != null) {
							try {
								globalReceiptListener.onReceived(frame);
							} catch (Exception e) {
								logger.error("globalReceiptListener.onReceipt error. receipt id: " + receiptId, e);
							}
						} else {
							logger.warn("Unhandled received RECEIPT: {}", frame);
						}
					}
				} else if (Command.ERROR.equals(frame.getCommand())) {
					if (errorListener != null) {
						try {
							errorListener.onError(frame);
						} catch(Exception e) {
							logger.error("onErrorCallback error. frame: " + frame, e);
						}
					} else {
						logger.warn("Unhandled received ERROR: {}", frame);
					}
				} else {
					logger.warn("Unhandled frame: {}", frame);
				}
			}
			
			@Override
			public void onClose(int code, String reason, boolean remote) {
				if (code == 1000) {
					logger.debug("webSocket closed...");
				} else {
					logger.warn("Whoops! Lost connection to {}", connection.getUrl());
				}
				
				if (webSocketCloseListener != null) {
					try {
						webSocketCloseListener.onClose(code, reason, remote);
					} catch (Exception e) {
						logger.error("webSocketCloseListener.onClose() error. code: " + code +
								", reason: " + reason +
								", remote: " + remote,
								e);
					}
				} else {
					logger.warn("Unhandled onClose event. code: {}, reason: {}, remote: {}", code, reason, remote);
				}
				
				
				cleanUp();
				if (disconnectListener != null) {
					try {
						disconnectListener.onDisconnect();
					} catch (Exception e) {
						logger.error("disconnectListener.onDisconnect() error.", e);
					}
				}
			}
			
			@Override
			public void onError(Exception ex) {
				logger.error("websocket error", ex);
				if (webScoketErrorListener != null) {
					try {
						webScoketErrorListener.onError(ex);
					} catch (Exception e) {
						logger.error("webScoketErrorListener.onError() error.", e);
					}
				} else {
					logger.warn("Unhandled onError event.", ex);
				}
			}
		};
	}
	
	
	
	public void disconnect(final DisconnectListener disconnectListener) {
		transmit(Command.DISCONNECT, null, null);
		
		websocketClient.close();
		cleanUp();
		if (disconnectListener != null) {
			try {
				disconnectListener.onDisconnect();
			} catch (Exception e) {
				logger.error("disconnectListener.onDisconnect() error.", e);
			}
		}
	}
	
	
//	private SSLContext createSSLContext() {
//		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
//			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
//				return null;
//			}
//
//			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
//			}
//
//			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
//			}
//		} };
//
//		try {
//			SSLContext sc = SSLContext.getInstance("SSL");
//			sc.init(null, trustAllCerts, new java.security.SecureRandom());
//			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//			HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
//				public boolean verify(String hostname, SSLSession session) {
//					return true;
//				}
//			});
//			return sc;
//		} catch (Exception e) {
//			logger.error("에러 발생", e);
//		}
//		return null;
//	}
	
	
//	public void disconnect(final DisconnectListener disconnectListener) {
//		this.disconnectListener =  disconnectListener;
//		
//		Receipt receipt = new Receipt();
//		receipt.setReceiptId("receipt-" + receiptCount++);
//		receipt.setReceiptListener(new ReceiptListener() {
//			
//			@Override
//			public void onReceived(Frame frame) {
//				webSocketClient.close();
//				
////				cleanUp();
////				if (disconnectListener != null) {
////					try {
////						disconnectListener.onDisconnect();
////					} catch (Exception e) {
////						logger.error("disconnectListener.onDisconnect() error.", e);
////					}
////				}
//			}
//		});
//		receipts.put(receipt.getReceiptId(), receipt);
//		
//		disconnect(receipt);
//	}
//	
//	private void disconnect(Receipt receipt) {
//		Map<String, String> headers = new TreeMap<String, String>();
//		headers.put("receipt", receipt.getReceiptId());
//		
//		transmit(Command.DISCONNECT, headers, null);
//	}
	
	private void cleanUp() {
		if (pinger != null) {
			pinger.cancel();
		}
		if (ponger != null) {
			ponger.cancel();
		}
		
		disconnectListener = null;
		errorListener = null;
		webScoketErrorListener = null;
		webSocketCloseListener = null;
		
		subscriptions.clear();
		receipts.clear();
		
		serverActivity = 0;
		subscriptionCount = 0;
		receiptCount = 0;
		transactionCount = 0;
		
		connected = false;

	}
	
	public void send(String destination, String body) {
		send(destination, null, body);
	}
	
	public void send(String destination, Object body) {
		send(destination, null, frameConverter.toString(body));
	}
	
	public void send(String destination, StompHeaders stompHeaders, String body) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("destination", destination);
		if (stompHeaders != null) {
			headers.putAll(stompHeaders.getHeaders());
		}
		
		transmit(Command.SEND, headers, body);
	}
	
	public Subscription subscribe(String destination, Class<?> targetClass, SubscribeHandler listener) {
		Subscription subscription = new Subscription();
		subscription.setDestination(destination);
		subscription.setId("sub-" + subscriptionCount++);
		subscription.setTargetClass(targetClass);
		subscription.setListener(listener);
		subscriptions.put(subscription.getId(), subscription);
		
		return subscribe(subscription);
	}
	
	public Subscription subscribe(String destination, SubscribeHandlerWithoutPayload listener) {
		Subscription subscription = new Subscription();
		subscription.setDestination(destination);
		subscription.setId("sub-" + subscriptionCount++);
		subscription.setListenerWithoutPayload(listener);
		subscriptions.put(subscription.getId(), subscription);
		
		return subscribe(subscription);
	}
	
	
	private Subscription subscribe(Subscription subscription) {
		Map<String, String> headers = new TreeMap<String, String>();
		headers.put("destination", subscription.getDestination());
		headers.put("id", subscription.getId());
		
		transmit(Command.SUBSCRIBE, headers, null);
		return subscription;
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
	
	public void unsubscribeByDestination(String destination) {
		Iterator<Subscription> subIter = subscriptions.values().iterator();
		String id = null;
		while (subIter.hasNext()) {
			Subscription subscription = subIter.next();
			if (subscription.getDestination().equals(destination)) {
				id = subscription.getId();
				unsubscribe(subscription);
				break;
			}
		}
		if (id != null) {
			subscriptions.remove(id);
		}
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

	public WebScoketErrorListener getWebScoketErrorListener() {
		return webScoketErrorListener;
	}
	
	public void setWebSocketCloseListener(WebSocketCloseListener webSocketCloseListener) {
		this.webSocketCloseListener = webSocketCloseListener;
	}

	public WebSocketCloseListener getWebSocketCloseListener() {
		return webSocketCloseListener;
	}

	public void setGlobalReceiptListener(ReceiptListener receiptListener) {
		this.globalReceiptListener = receiptListener;
	}

	public ReceiptListener getGlobalReceiptListener() {
		return globalReceiptListener;
	}

	public ErrorListener getErrorListener() {
		return errorListener;
	}

	public void setErrorListener(ErrorListener errorListener) {
		this.errorListener = errorListener;
	}

	public FrameBodyConverter getFrameConverter() {
		return frameConverter;
	}

	public StompClient setFrameConverter(FrameBodyConverter frameConverter) {
		this.frameConverter = frameConverter;
		return this;
	}
	
}
