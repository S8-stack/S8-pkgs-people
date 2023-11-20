package com.s8.pkgs.people;

import com.s8.api.objects.web.S8WebFront;
import com.s8.api.objects.web.S8WebFrontObject;

public class InboardScreen extends S8WebFrontObject {

	public InboardScreen(S8WebFront front) {
		super(front, "/s8-pkgs-people/InboardScreen");
	}
	
	public void setBackground(S8WebFrontObject back) {
		vertex.fields().setObjectField("background", back);
	}
	
	public void setModalBox(S8WebFrontObject box) {
		vertex.fields().setObjectField("modalBox", box);
	}

}
