package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.functions.none.VoidNeFunction;
import com.s8.api.web.functions.primitives.StringUTF8NeFunction;


/**
 * Inboard box
 */
public class ValidateForm extends Form {

	/**
	 * 
	 * @param session
	 */
	public ValidateForm(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/ValidateForm");
	}
	
	
	/**
	 * 
	 * @param session
	 * @param title
	 */
	public ValidateForm(S8WebFront front, String title) {
		super(front, WebSources.ROOT_PATH + "/ValidateForm");
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
	public void onTyringValidate(StringUTF8NeFunction func) {
		vertex.inbound().setStringUTF8Method("on-trying-validate", func);
	}
	
	/**
	 * 
	 * @param func
	 */
	public void onGoToLogIn(VoidNeFunction func) {
		vertex.inbound().setVoidMethod("goto-login", func);
	}

}
