package com.s8.pkgs.people.forms;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;
import com.s8.pkgs.people.WebSources;

public class Form extends S8WebObject {

	public Form(S8WebFront front, String typeName) {
		super(front, WebSources.ROOT_PATH + "/forms" + typeName);
	}

}
