package org.adrenalinee.stomp;

import java.util.LinkedHashMap;
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
	
	static final String LF = "\n";
	static final String NULL = "\0";
	
	private Command command;
	
	private Map<String, String> headers;
	
	private String body;
	
	Frame(Command command, Map<String, String> headers, String body) {
		this.command = command;
		this.headers = new LinkedHashMap<String, String>();
		this.headers.putAll(headers);
		this.body = body;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer(command.name()).append(LF);
		
		for (Entry<String, String> entry: headers.entrySet()) {
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
			if ("".equals(line)) {
				break;
			}
			
			String[] header = line.split(":");
			headers.put(header[0], header[1]);
		}
		
		String body = st.nextToken();
		
		return new Frame(command, headers, body);
	}

	public Command getCommand() {
		return command;
	}

	public void setCommand(Command command) {
		this.command = command;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

}
