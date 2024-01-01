package com.s8.pkgs.people;

import java.util.regex.Pattern;

/**
 * 
 */
public class S8People {

	public static final String USERS_TABLE_ID = "USERS";


	public static final Pattern VALID_EMAIL_ADDRESS = Pattern.compile("[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}");


	public static final Pattern VALID_PASSWORD = Pattern.compile("(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[#?!@$%^&*-]).{8,}");




	
}
