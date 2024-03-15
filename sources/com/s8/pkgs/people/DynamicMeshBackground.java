package com.s8.pkgs.people;

import com.s8.api.web.S8WebFront;
import com.s8.api.web.S8WebObject;


/**
 * 
 */
public class DynamicMeshBackground extends S8WebObject {

	
	/**
	 * 
	 * @param front
	 * @param typeName
	 */
	public DynamicMeshBackground(S8WebFront front) {
		super(front, WebSources.ROOT_PATH + "/DynamicMeshBackground");
	}
	
	
	public void setLineColor(int[] rgba) {
		vertex.outbound().setUInt8ArrayField("lineColor", rgba);
	}
	
	public void setParticleColor(int[] rgba) {
		vertex.outbound().setUInt8ArrayField("particleColor", rgba);
	}

}
