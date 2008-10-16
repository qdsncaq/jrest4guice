package org.jrest4guice.rest.reader;

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.jrest4guice.client.ModelMap;
import org.jrest4guice.rest.exception.Need2RedirectException;

/**
 * 
 * @author <a href="mailto:zhangyouqun@gmail.com">cnoss (QQ:86895156)</a>
 * 
 */
@SuppressWarnings( { "unchecked" })
public abstract class ParameterPairContentRader implements RequestContentReader {
	@Override
	public void readData(HttpServletRequest request, ModelMap params,
			String charset) {
		Enumeration names = request.getAttributeNames();
		String name;
		while (names.hasMoreElements()) {
			name = names.nextElement().toString();
			params.put(name, request.getAttribute(name));
		}

		// url中的参数
		names = request.getParameterNames();
		while (names.hasMoreElements()) {
			name = names.nextElement().toString();
			params.put(name, request.getParameter(name));
		}
	}
}
