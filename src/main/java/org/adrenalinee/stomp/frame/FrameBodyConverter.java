package org.adrenalinee.stomp.frame;

/**
 * 
 * @author 신동성
 * @since 2016. 5. 9.
 */
public interface FrameBodyConverter {
	
	Object fromFrame(String body, Class<?> targetClass);
	
	String toString(Object payload);
}
