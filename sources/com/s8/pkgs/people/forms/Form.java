package com.s8.pkgs.people.forms;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebFrontObject;
import com.s8.pkgs.people.WebSources;

public class Form extends S8WebFrontObject {

	public Form(S8WebFront front, String typeName) {
		super(front, WebSources.ROOT_PATH + "/forms" + typeName);
	}

}
