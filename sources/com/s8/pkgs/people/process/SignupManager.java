package com.s8.pkgs.people.process;

import java.io.IOException;

import com.s8.api.flow.S8AsyncFlow;
import com.s8.api.flow.mail.S8MailBuilder;
import com.s8.api.flow.mail.SendMailS8Request;
import com.s8.api.flow.table.objects.RowS8Object;
import com.s8.api.flow.table.requests.GetRowS8Request;
import com.s8.api.web.S8WebFront;
import com.s8.pkgs.people.Inboard;
import com.s8.pkgs.people.InboardMessage;
import com.s8.pkgs.people.S8People;
import com.s8.pkgs.people.InboardMessage.Mode;
import com.s8.pkgs.people.forms.SignupForm;
import com.s8.pkgs.people.forms.ValidateForm;

public class SignupManager {
	
	public final Inboard inboard;
	
	private SignupForm signUpForm;
	
	private ValidateForm validationForm;
	

	private String validationCode;
	
	

	/**
	 * 
	 * @param inboard
	 */
	public SignupManager(Inboard inboard) {
		super();
		this.inboard = inboard;
	}

	
	public void start(S8WebFront front) {
		
		if(signUpForm == null) {
			
			signUpForm = new SignupForm(front);

			signUpForm.onUsernameChange((f4, username) -> {
				
				boolean isValidEmailAddress = S8People.VALID_EMAIL_ADDRESS.matcher(username).matches();
				
				if(isValidEmailAddress) {
					f4.getRow(new GetRowS8Request(S8People.USERS_TABLE_ID, username){
						public @Override void onSucceed(Status status, RowS8Object row) {
							if(status == Status.OK) {
								if(row != null) {
									signUpForm.setUsernameFeedbackMessage(
											new InboardMessage(front, Mode.WARNING, "Username is already reserved"));
								}
								else {
									signUpForm.setUsernameFeedbackMessage(
											new InboardMessage(front, Mode.VALIDATE, "Username is available!"));
								}
							}
						}
						public @Override void onFailed(Exception exception) {
							exception.printStackTrace();
						}
					});
					
				}
				else {
					signUpForm.setUsernameFeedbackMessage(
							new InboardMessage(front, Mode.WARNING, "Username MUST be a valid email address"));
				}
				f4.send();
			});
			
			
			signUpForm.onSignUp((f4, credentials) -> {
				
				String username = credentials[0];
				boolean isValidEmailAddress = S8People.VALID_EMAIL_ADDRESS.matcher(username).matches();
				
				if(isValidEmailAddress) {
					f4.getRow(new GetRowS8Request(S8People.USERS_TABLE_ID, username){
						public @Override void onSucceed(Status status, RowS8Object row) {
							if(status == Status.OK) {
								if(row != null) {
									signUpForm.setUsernameFeedbackMessage(
											new InboardMessage(front, Mode.ERROR, "Username is not available"));
								}
								else {
									signUpForm.setUsernameFeedbackMessage(null); /* clear message */
									
									/* then */
									
									String passwordDefinition = credentials[1];
									if(passwordDefinition != null && 
											S8People.VALID_PASSWORD.matcher(passwordDefinition).matches()) {
										signUpForm.setDefinePasswordFeedbackMessage(null); /* clear message */
										
										String passwordConfirmation = credentials[2];
										if(passwordDefinition.equals(passwordConfirmation)) {
											signUpForm.setConfirmPasswordFeedbackMessage(null);
											onSignUpSucceed(front, f4, username, passwordDefinition);
										}
										else {
											signUpForm.setConfirmPasswordFeedbackMessage(
													new InboardMessage(front, Mode.WARNING, "Password recopy is not matching"));
										}
									}
									else {
										signUpForm.setDefinePasswordFeedbackMessage(
												new InboardMessage(front, Mode.WARNING, "Password is not valid"));
									}
								}
							}
						}
						
						public @Override void onFailed(Exception exception) {
							exception.printStackTrace();
						}
					});
					
				}
				else {
					signUpForm.setUsernameFeedbackMessage(
							new InboardMessage(front, Mode.WARNING, "Username MUST be a valid email address"));
				}
				f4.send();
				
			});
		
			
			signUpForm.onGoToLogIn(f5 -> { 
				inboard.getLoginModule().start(front); 
				f5.send(); 
			});
			
		}
		
		inboard.getBox(front).setForm(signUpForm);
	}
	
	
	
	private void onSignUpSucceed(S8WebFront front, S8AsyncFlow f4, 
			String username,
			String password) {
		
		validationCode = Long.toHexString(System.nanoTime() & 0xffffffffL);
		
		f4.sendEMail(new SendMailS8Request(true) {
			public @Override void compose(S8MailBuilder mail) throws IOException {
				mail.setRecipient(username);
				
				mail.setSubject("Sign-Up email confirmation");
				
				mail.html_setWrapperStyle(".mg-mail-wrapper", null);
				mail.html_appendBaseElement("div", 
						".mg-mail-banner", 
						"background-image: url(https://alphaventor.com/assets/logos/AlphaventorLogo-1024px-black-text.png);", 
						null);
				
				mail.html_appendBaseElement("h1", ".mg-h1", null, "Hello dear AlphaVentor user!");
				mail.html_appendBaseElement("h2", ".mg-h2", null, "Welcome to a world of designs");
				mail.html_appendBaseElement("p", ".mg-p", null, 
						"Please find below your validation code for the creation of your account:");
				
				mail.html_appendBaseElement("div", ".mg-code-wrapper", null, validationCode);
				
				mail.html_appendBaseElement("p", ".mg-p", null, 
						"If you're not the initiator of this, please report to pierre.convert@alphaventor.com");
				
			}
			
			@Override
			public void onSent(Status status, String message) {
				
			}
			
			@Override
			public void onFailed(Exception exception) {
				// TODO Auto-generated method stub
				
			}
		});
		
		
		if(validationForm == null) {
			validationForm = new ValidateForm(front);
			validationForm.onTyringValidate((f5, codeRecopy) -> {
				if(validationCode.equals(codeRecopy)) {
					inboard.onSignUpSucceed(front, f5, username, password);
				}
			});
		}
		
		
		
		inboard.getBox(front).setForm(validationForm);
	}
	
	

}
