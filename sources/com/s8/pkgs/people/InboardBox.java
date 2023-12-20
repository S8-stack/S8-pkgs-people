package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;


/**
 * Inboard box
 */
public class InboardBox extends S8WebFrontObject {

	/**
	 * 
	 * @param session
	 */
	public InboardBox(S8WebFront front) {
		super(front, "/S8-pkgs-people/InboardBox");
	}
	
	
	
	public void setLogInForm(LogInForm form) {
		vertex.fields().setObjectField("loginForm", form);
	}
	
	public void setSignUpForm(SignUpForm form) {
		vertex.fields().setObjectField("signupForm", form);
	}
	
	

}
