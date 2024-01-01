package com.s8.pkgs.people;


import com.s8.api.web.S8WebFront;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;
import com.s8.api.web.functions.none.VoidNeFunction;
import com.s8.api.web.functions.primitives.StringUTF8NeFunction;


/**
 * Inboard box
 */
public class SignupForm extends Form {
	
	
	

	
	/**
	 * 
	 * @param session
	 */
	public SignupForm(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/SignUpForm");
	}
	

	
	


	public void setTitle(String title) {
		vertex.outbound().setStringUTF8Field("title", title);
	}


	/**
	 * 
	 * @param func
	 */
	public void onSignUp(StringUTF8ArrayNeFunction func) {
		vertex.inbound().setStringUTF8ArrayMethod("on-signup", func);
	}

	/**
	 * 
	 * @param func
	 */
	public void onUsernameChange(StringUTF8NeFunction func) {
		vertex.inbound().setStringUTF8Method("on-username-change", func);
	}

	public void setUsernameFeedbackMessage(InboardMessage message){
		this.vertex.outbound().setObjectField("usernameFeedbackMessage", message);
	}

	/**
	 * 
	 * @param func
	 */
	public void onDefinePasswordChange(StringUTF8NeFunction func) {
		vertex.inbound().setStringUTF8Method("on-define-password-change", func);
	}
	
	public void setDefinePasswordFeedbackMessage(InboardMessage message){
		this.vertex.outbound().setObjectField("definePasswordFeedbackMessage", message);
	}

	/**
	 * 
	 * @param func
	 */
	public void onConfirmPasswordChange(StringUTF8NeFunction func) {
		vertex.inbound().setStringUTF8Method("on-confirm-password-change", func);
	}

	public void setConfirmPasswordFeedbackMessage(InboardMessage message){
		this.vertex.outbound().setObjectField("confirmPasswordFeedbackMessage", message);
	}
	
	/**
	 * 
	 * @param func
	 */
	public void onGoToLogIn(VoidNeFunction func) {
		vertex.inbound().setVoidMethod("goto-login", func);
	}


}
