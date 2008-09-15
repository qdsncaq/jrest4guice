package org.jrest4guice.rest;

import java.lang.reflect.Method;

/**
 * 
 * @author <a href="mailto:zhangyouqun@gmail.com">cnoss (QQ:86895156)</a>
 * 
 */
public class Service {
	private Object instance;
	private Method method;
	
	public Service(Object instance, Method method) {
		super();
		this.instance = instance;
		this.method = method;
	}
	public Object getInstance() {
		return instance;
	}
	public void setInstance(Object instance) {
		this.instance = instance;
	}
	public Method getMethod() {
		return method;
	}
	public void setMethod(Method method) {
		this.method = method;
	}
}