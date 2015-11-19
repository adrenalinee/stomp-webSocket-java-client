package org.adrenalinee.stomp;

import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * 
 * @author shindongseong
 * @since 2015. 11. 6.
 */
public class Frame {
	public static final String LF = "\n";
	public static final String NULL = "\0";
	
	private Command command;
	
	private StompHeaders headers;
	
	private String body;
	
	private StompClient stompClient;
	
	
	Frame(Command command, Map<String, String> headers, String body) {
		this.command = command;
		this.headers = new StompHeaders();
//		this.headers.putAll(headers);
		
		if (headers != null) {
			for (String key: headers.keySet()) {
				this.headers.addHeader(key, headers.get(key));
			}
		}
		
		this.body = body;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer(command.name()).append(LF);
		
		for (Entry<String, String> entry: headers.getHeaders().entrySet()) {
			sb.append(entry.getKey())
				.append(":")
				.append(entry.getValue())
				.append(LF);
		}
		
		return
			sb.append(LF)
				.append(body == null ? "" : body)
				.append(NULL).toString();
	}
	
	public static String marshall(Command command, Map<String, String> headers, String body) {
		return new Frame(command, headers, body).toString();
	}
	
	public static Frame unmarshall(String message) {
		StringTokenizer st = new StringTokenizer(message, LF);
		Command command = Command.valueOf(st.nextToken());
		
		Map<String, String> headers = new TreeMap<String, String>();
		while (st.hasMoreTokens()) {
			String line = st.nextToken();
			if ("".equals(line.trim())) {
				break;
			}
			
			String[] header = line.split(":");
			headers.put(header[0], header[1]);
		}
		
		String body = null;
		if (st.hasMoreTokens()) {
			body = st.nextToken();
		}
		
		return new Frame(command, headers, body);
	}
	
	
	public void ack() {
		if (stompClient == null) {
			throw new RuntimeException();
		}
		String messageID = headers.getMessageId();
		String subscription = headers.getSubscription();
		stompClient.ack(messageID, subscription, headers);
	}
	
	public void nack() {
		if (stompClient == null) {
			throw new RuntimeException();
		}
		String messageID = headers.getMessageId();
		String subscription = headers.getSubscription();
		stompClient.nack(messageID, subscription, headers);
	}
	
	
	public Command getCommand() {
		return command;
	}

	public void setCommand(Command command) {
		this.command = command;
	}

	public StompHeaders getHeaders() {
		return headers;
	}

	public void setHeaders(StompHeaders headers) {
		this.headers = headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	void setStompClient(StompClient stompClient) {
		this.stompClient = stompClient;
	}

}
