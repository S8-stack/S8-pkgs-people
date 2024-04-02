package com.s8.pkgs.people.menu;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;
import com.s8.pkgs.people.WebSources;


/**
 * 
 */
public class P8MenuIcon extends S8WebObject {

	
	public static P8MenuIcon create(S8WebFront front) {
		P8MenuIcon menu = new P8MenuIcon(front);
		return menu;
	}
	
	
	public P8MenuIcon(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/menu/P8MenuIcon");
	}
	
	
	public void setImage(String url) {
		vertex.outbound().setStringUTF8Field("imageURL", url);
	}
	
}
