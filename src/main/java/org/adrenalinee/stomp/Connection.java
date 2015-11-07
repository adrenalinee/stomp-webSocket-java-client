package org.adrenalinee.stomp;

import java.util.Map;

/**
 * 
 * @author 신동성
 * @since 2015. 11. 6.
 */
public class Connection {
	private String url;
	
	private Map<String,String> headers;
	
	private int connecttimeout = 30;
	
	public Connection(String url, Map<String,String> headers) {
		this.url = url;
		this.headers = headers;
	}
	
	public String getUrl() {
		return url;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public int getConnecttimeout() {
		return connecttimeout;
	}

	public void setConnecttimeout(int connecttimeout) {
		this.connecttimeout = connecttimeout;
	}
}
