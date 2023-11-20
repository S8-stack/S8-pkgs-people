package com.s8.pkgs.people;

import com.s8.api.objects.web.S8WebFront;
import com.s8.api.objects.web.S8WebFrontObject;


/**
 * 
 */
public class DynamicMeshBackground extends S8WebFrontObject {

	
	/**
	 * 
	 * @param front
	 * @param typeName
	 */
	public DynamicMeshBackground(S8WebFront front) {
		super(front, "/s8-pkgs-people/DynamicMeshBackground");
	}
	
	
	public void setLineColor(int[] rgba) {
		vertex.fields().setUInt8ArrayField("lineColor", rgba);
	}
	
	public void setParticleColor(int[] rgba) {
		vertex.fields().setUInt8ArrayField("particleColor", rgba);
	}

}
