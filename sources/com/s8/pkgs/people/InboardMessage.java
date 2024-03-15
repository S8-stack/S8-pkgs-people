package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;


/**
 * 
 */
public class InboardMessage extends S8WebObject {

	public enum Mode {
		VALIDATE(2), WARNING(3), ERROR(4);
		
		public int code;

		private Mode(int code) { this.code = code; }
	}
	
	/**
	 * 
	 * @param front
	 * @param mode
	 * @param text
	 */
	public InboardMessage(S8WebFront front, Mode mode, String text) {
		super(front, WebSources.ROOT_PATH + "/InboardMessage");
		vertex.outbound().setUInt8Field("mode", mode.code);
		vertex.outbound().setStringUTF8Field("text", text);
	}
	
}
