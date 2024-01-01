package com.s8.pkgs.people;

import java.io.IOException;

import com.s8.api.flow.S8AsyncFlow;
import com.s8.api.flow.mail.S8Mail;
import com.s8.api.flow.mail.SendMailS8Request;
import com.s8.api.flow.table.objects.RowS8Object;
import com.s8.api.flow.table.requests.GetRowS8Request;
import com.s8.api.web.S8WebFront;
import com.s8.pkgs.people.InboardMessage.Mode;

public class SignupModule {
	
	public final Inboard inboard;
	
	private SignupForm form0;
	
	private ValidateForm form1;
	

	private String validationCode;
	
	

	/**
	 * 
	 * @param inboard
	 */
	public SignupModule(Inboard inboard) {
		super();
		this.inboard = inboard;
	}

	
	public void start(S8WebFront front) {
		
		if(form0 == null) {
			
			form0 = new SignupForm(front);

			form0.onUsernameChange((f4, username) -> {
				
				boolean isValideEmailAddress = S8People.VALID_EMAIL_ADDRESS.matcher(username).matches();
				
				if(isValideEmailAddress) {
					f4.getRow(new GetRowS8Request(S8People.USERS_TABLE_ID, username){
						public @Override void onSucceed(Status status, RowS8Object row) {
							if(status == Status.OK) {
								if(row != null) {
									form0.setUsernameFeedbackMessage(
											new InboardMessage(front, Mode.WARNING, "Username is already reserved"));
								}
								else {
									form0.setUsernameFeedbackMessage(
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
					form0.setUsernameFeedbackMessage(
							new InboardMessage(front, Mode.WARNING, "Username MUST be a valid email address"));
				}
				f4.send();
			});
			
			
			form0.onSignUp((f4, credentials) -> {
				
				String username = credentials[0];
				boolean isValideEmailAddress = S8People.VALID_EMAIL_ADDRESS.matcher(username).matches();
				
				if(isValideEmailAddress) {
					f4.getRow(new GetRowS8Request(S8People.USERS_TABLE_ID, username){
						public @Override void onSucceed(Status status, RowS8Object row) {
							if(status == Status.OK) {
								if(row != null) {
									form0.setUsernameFeedbackMessage(
											new InboardMessage(front, Mode.ERROR, "Username is not available"));
								}
								else {
									form0.setUsernameFeedbackMessage(null); /* clear message */
									
									/* then */
									
									String passwordDefinition = credentials[1];
									if(passwordDefinition != null && 
											S8People.VALID_PASSWORD.matcher(passwordDefinition).matches()) {
										form0.setDefinePasswordFeedbackMessage(null); /* clear message */
										
										String passwordConfirmation = credentials[2];
										if(passwordDefinition.equals(passwordConfirmation)) {
											form0.setConfirmPasswordFeedbackMessage(null);
											onSignUpSucceed(front, f4, username, passwordDefinition);
										}
										else {
											form0.setConfirmPasswordFeedbackMessage(
													new InboardMessage(front, Mode.WARNING, "Password recopy is not matching"));
										}
									}
									else {
										form0.setDefinePasswordFeedbackMessage(
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
					form0.setUsernameFeedbackMessage(
							new InboardMessage(front, Mode.WARNING, "Username MUST be a valid email address"));
				}
				f4.send();
				
			});
		
			
			form0.onGoToLogIn(f5 -> { 
				inboard.getLoginModule().start(front); 
				f5.send(); 
			});
			
		}
		
		inboard.getBox(front).setForm(form0);
	}
	
	
	
	private void onSignUpSucceed(S8WebFront front, S8AsyncFlow f4, 
			String username,
			String password) {
		
		validationCode = Long.toHexString(System.nanoTime() & 0xffffffffL);
		
		f4.sendEMail(new SendMailS8Request(true) {
			public @Override void compose(S8Mail mail) throws IOException {
				mail.setRecipient(username);
				mail.setSubject("Sign-Up email confirmation");
				mail.appendText("Please find below your validation code:");
				mail.appendText(validationCode);
			}
			
			@Override
			public void onSent(Status status, String message) {
				
			}
			
			@Override
			public void onFailed(Exception exception) {
				// TODO Auto-generated method stub
				
			}
		});
		
		
		if(form1 == null) {
			form1 = new ValidateForm(front);
			form1.onTyringValidate((f5, codeRecopy) -> {
				if(validationCode.equals(codeRecopy)) {
					inboard.onSignUpSucceed(front, f5, username, password);
				}
			});
		}
		
		
		
		inboard.getBox(front).setForm(form1);
	}
	
	

}
