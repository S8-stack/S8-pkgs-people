package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;

public class InboardScreen extends S8WebObject {

	public InboardScreen(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/InboardScreen");
	}
	
	public void setBackground(S8WebObject back) {
		vertex.outbound().setObjectField("background", back);
	}
	
	public void setModalBox(S8WebObject box) {
		vertex.outbound().setObjectField("modalBox", box);
	}

}
