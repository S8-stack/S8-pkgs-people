package com.s8.pkgs.people.forms;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;
import com.s8.api.web.functions.none.VoidNeFunction;
import com.s8.pkgs.people.InboardMessage;
import com.s8.pkgs.people.WebSources;


/**
 * Inboard box
 */
public class LogInForm extends Form {

	
	/**
	 * 
	 * @param session
	 */
	public LogInForm(S8WebFront front) {
		super(front, "/LogInForm");
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
	
	
	/**
	 * 
	 * @param title
	 */
	public void setTitle(String title) {
		vertex.outbound().setStringUTF8Field("title", title);
	}
	
	
	/**
	 * 
	 * @param message
	 */
	public void setMessage(InboardMessage message) {
		vertex.outbound().setObjectField("message", message);
	}
	
	
	/**
	 * 
	 * @param func
	 */
	public void onTyringLogin(StringUTF8ArrayNeFunction func) {
		vertex.inbound().setStringUTF8ArrayMethod("on-trying-login", func);
	}
	
	
	/**
	 * 
	 * @param func
	 */
	public void onGoToSignUp(VoidNeFunction func) {
		vertex.inbound().setVoidMethod("goto-signup", func);
	}

}
