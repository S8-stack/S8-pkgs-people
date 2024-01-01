package com.s8.pkgs.people.demos;

import com.s8.pkgs.people.S8People;

public class TestValidatePassword {

	public static void main(String[] args) {
		
		String password = "Delphine12";
		System.out.println(S8People.VALID_PASSWORD.matcher(password).matches());
	}

}
