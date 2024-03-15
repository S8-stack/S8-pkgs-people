package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;
import com.s8.pkgs.people.forms.Form;


/**
 * Inboard box
 */
public class InboardBox extends S8WebObject {

	/**
	 * 
	 * @param session
	 */
	public InboardBox(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/InboardBox");
	}
	
	
	
	
	public void setForm(Form form) {
		vertex.outbound().setObjectField("form", form);
	}
	
	
	public void setLogo(String url) {
		vertex.outbound().setStringUTF8Field("logo", url);
	}
	
	
	public void setTitle(String value) {
		vertex.outbound().setStringUTF8Field("title", value);
	}
	
	

}
