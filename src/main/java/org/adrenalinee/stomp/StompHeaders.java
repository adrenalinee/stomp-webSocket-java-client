package org.adrenalinee.stomp;

import java.util.Map;
import java.util.TreeMap;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 13.
 */
public class StompHeaders {
	private Map<String, String> headers = new TreeMap<String, String>();
	
	public String getHeader(String key) {
		return headers.get(key);
	}
	
	public void addHeader(String key, String value) {
		headers.put(key, value);
	}
	
	public void setAuthorazation(String value) {
		headers.put("Authorazation", value);
	}
	
	public String getMessageId() {
		return headers.get("message-id");
	}
	
	public String getSubscription() {
		return headers.get("subscription");
	}
	public String getHeartBeat() {
		return headers.get("heart-beat");
	}
	
	public Map<String, String> getHeaders() {
		Map<String, String> clonedHeaders = new TreeMap<String, String>();
		clonedHeaders.putAll(headers);
		return clonedHeaders;
	}
}
