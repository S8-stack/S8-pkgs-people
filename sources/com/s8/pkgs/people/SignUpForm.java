package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;
import com.s8.api.web.functions.primitives.StringUTF8NeFunction;


/**
 * Inboard box
 */
public class SignUpForm extends S8WebFrontObject {

	/**
	 * 
	 * @param session
	 */
	public SignUpForm(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/SignUpForm");
	}


	/**
	 * 
	 * @param session
	 * @param title
	 */
	public SignUpForm(S8WebFront front, String title) {
		super(front, WebSources.ROOT_PATH + "/SignUpForm");
		setTitle(title);
	}


	public void setTitle(String title) {
		vertex.fields().setStringUTF8Field("title", title);
	}


	/**
	 * 
	 * @param func
	 */
	public void onTyringSignUp(StringUTF8ArrayNeFunction func) {
		vertex.methods().setStringUTF8ArrayMethod("on-trying-signup", func);
	}

	/**
	 * 
	 * @param func
	 */
	public void onUsernameChange(StringUTF8NeFunction func) {
		vertex.methods().setStringUTF8Method("on-username-change", func);
	}

	public void setUsernameFeedbackMessage(InboardMessage message){
		this.vertex.fields().setObjectField("usernameFeedbackMessage", message);
	}

	/**
	 * 
	 * @param func
	 */
	public void onDefinePasswordChange(StringUTF8NeFunction func) {
		vertex.methods().setStringUTF8Method("on-define-password-change", func);
	}
	
	public void setDefinePasswordFeedbackMessage(InboardMessage message){
		this.vertex.fields().setObjectField("definePasswordFeedbackMessage", message);
	}

	/**
	 * 
	 * @param func
	 */
	public void onConfirmPasswordChange(StringUTF8NeFunction func) {
		vertex.methods().setStringUTF8Method("on-confirm-password-change", func);
	}

	public void setConfirmPasswordFeedbackMessage(InboardMessage message){
		this.vertex.fields().setObjectField("confirmPasswordFeedbackMessage", message);
	}


}
