package com.s8.pkgs.people;

import com.s8.api.flow.S8AsyncFlow;
import com.s8.api.flow.S8User;
import com.s8.api.web.S8WebFront;
import com.s8.pkgs.people.process.LoginManager;
import com.s8.pkgs.people.process.SignupManager;

public abstract class Inboard {
	
	
	public final String usersTableId;
	
	public final String logoURL;
	
	public final String title;
	
	
	private InboardBox box;
	
	
	private SignupManager signupModule;
	
	private LoginManager loginModule;
	
	
	
	
	
	
	public Inboard(String usersTableId, String logoURL, String title) {
		super();
		this.usersTableId = usersTableId;
		this.logoURL = logoURL;
		this.title = title;
	}

	
	
	public InboardBox getBox(S8WebFront front) {
		if(box == null) {
			box = new InboardBox(front);
			box.setLogo(logoURL);
			box.setTitle(title);
		}
		return box;
	}
	
	

	
	public LoginManager getLoginModule() {
		if(loginModule == null) { loginModule = new LoginManager(this); }
		return loginModule;
	}

	public SignupManager getSignupModule() {
		if(signupModule == null) { signupModule = new SignupManager(this); }
		return signupModule;
	}
	
	
	


	


	
	public abstract void onLogInSucceed(S8WebFront front, S8AsyncFlow flow, S8User user);
	
	
	public abstract void onSignUpSucceed(S8WebFront front, S8AsyncFlow flow, String username, String password);
	
}
