package com.s8.pkgs.people.demos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.s8.pkgs.people.S8People;

public class TestEmailRegex {

	public static void main(String[] args) {
		Pattern pattern = Pattern.compile(S8People.VALID_EMAIL_ADDRESS_REGEX);
		Matcher matcher = pattern.matcher("toto-department.service@groland.gouv.fr");
		System.out.println(matcher.matches());
	}

}
