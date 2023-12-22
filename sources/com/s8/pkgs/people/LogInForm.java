package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;


/**
 * Inboard box
 */
public class LogInForm extends S8WebFrontObject {

	/**
	 * 
	 * @param session
	 */
	public LogInForm(S8WebFront front) {
		super(front, "/S8-pkgs-people/LogInForm");
	}
	
	
	/**
	 * 
	 * @param session
	 * @param title
	 */
	public LogInForm(S8WebFront front, String title) {
		super(front, WebSources.ROOT_PATH + "/LogInForm");
		setTitle(title);
	}
	
	
	public void setTitle(String title) {
		vertex.fields().setStringUTF8Field("title", title);
	}
	
	public void setMessage(InboardMessage message) {
		vertex.fields().setObjectField("message", message);
	}
	
	
	/**
	 * 
	 * @param func
	 */
	public void onTyringLogin(StringUTF8ArrayNeFunction func) {
		vertex.methods().setStringUTF8ArrayMethod("on-trying-login", func);
	}

}
