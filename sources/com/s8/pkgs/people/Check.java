package com.s8.pkgs.people;

import java.util.regex.Pattern;

public class Check {
	
	
	public final String rule;
	
	public final Pattern pattern;

	
	/**
	 * 
	 * @param rule
	 * @param pattern
	 */
	public Check(String rule, String regex) {
		super();
		this.rule = rule;
		this.pattern = Pattern.compile(regex);
	}
	
	
	
	
	
	//(?=.*[A-Z])(?=.*[0-9])

}
