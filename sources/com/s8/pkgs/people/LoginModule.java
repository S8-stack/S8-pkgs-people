package com.s8.pkgs.people;

import com.s8.api.flow.S8AsyncFlow;
import com.s8.api.flow.S8User;
import com.s8.api.flow.table.objects.RowS8Object;
import com.s8.api.flow.table.requests.GetRowS8Request;
import com.s8.api.web.S8WebFront;
import com.s8.api.web.functions.arrays.StringUTF8ArrayNeFunction;
import com.s8.pkgs.people.InboardMessage.Mode;

public class LoginModule {
	
	public final Inboard inboard;
	
	private LogInForm form0;

	
	/**
	 * 
	 */
	public LoginModule(Inboard inboard) {
		super();
		this.inboard = inboard;
	}
	
	
	public void start(S8WebFront front) {
		if(form0 == null) {
			
			form0 = new LogInForm(front);
			
			form0.onTyringLogin(new StringUTF8ArrayNeFunction() {
				
				@Override
				public void run(S8AsyncFlow flow, String[] credentials) {
					
					String username = credentials[0];
					String password = credentials[1];
					
				
					flow.getRow(new GetRowS8Request(inboard.usersTableId, username){
						public @Override void onSucceed(Status status, RowS8Object row) {
							if(status == Status.OK) {
								S8User user = (S8User) row;
								if(row != null) {
									if(user.getPassword().equals(password)) {
										flow.setMe(user);
										inboard.onLogInSucceed(front, flow, user);
									}
									else {
										form0.setMessage(new InboardMessage(front, Mode.WARNING, "Invalid Username or Password"));
									}	
								}
								else {
									form0.setMessage(new InboardMessage(front, Mode.WARNING, "Invalid Username or Password"));
								}
							}
						}
						public @Override void onFailed(Exception exception) {
							exception.printStackTrace();
						}
					});
				
					/* flow send */
					flow.send();	
				}
			});
			
			
			
			form0.onGoToSignUp(f5 -> {
				inboard.getSignupModule().start(front);
				f5.send();
			});
		}
		
		inboard.getBox(front).setForm(form0);
		
	}
	

}
