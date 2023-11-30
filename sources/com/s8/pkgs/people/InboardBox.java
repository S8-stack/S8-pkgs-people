package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;


/**
 * 
 */
public class InboardBox extends S8WebFrontObject {

	/**
	 * 
	 * @param session
	 */
	public InboardBox(S8WebFront front) {
		super(front, "/s8-pkgs-people/InboardBox");
	}
	
	
	/**
	 * 
	 * @param session
	 * @param title
	 */
	public InboardBox(S8WebFront front, String title) {
		super(front, "/s8-pkgs-people/InboardBox");
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
