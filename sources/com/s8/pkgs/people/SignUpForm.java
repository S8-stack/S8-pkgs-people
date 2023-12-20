package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;


/**
 * Inboard box
 */
public class SignUpForm extends S8WebFrontObject {

	/**
	 * 
	 * @param session
	 */
	public SignUpForm(S8WebFront front) {
		super(front, "/S8-pkgs-people/SignUpForm");
	}
	
	
	/**
	 * 
	 * @param session
	 * @param title
	 */
	public SignUpForm(S8WebFront front, String title) {
		super(front, "/s8-pkgs-people/SignUpForm");
		setTitle(title);
	}
	
	
	public void setTitle(String title) {
		vertex.fields().setStringUTF8Field("title", title);
	}
	
	
	/**
	 * 
	 * @param func
	 */
	public void onTyringLogin(StringUTF8ArrayNeFunction func) {
		vertex.methods().setStringUTF8ArrayMethod("on-trying-login", func);
	}

}
