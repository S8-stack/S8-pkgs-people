
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/inboard.css');



export class InboardBox extends NeObject {


	isContainer1Visible = true;

	constructor() {
		super();

		this.boxNode = document.createElement("div");
		this.boxNode.classList.add("inboard-box");

		this.logoNode = document.createElement("div");
		this.logoNode.classList.add("inboard-logo");
		this.boxNode.appendChild(this.logoNode);

		this.titleNode = document.createElement("h1");
		this.titleNode.innerText = "<Title>";
		this.boxNode.appendChild(this.titleNode);

		this.containerNode = document.createElement("div");
		this.containerNode.classList.add("inboard-container-visible");
		this.boxNode.appendChild(this.containerNode);

		
	}







	getEnvelope() {
		return this.boxNode;
	}

	S8_render() { /* continuous rendering approach... */ }
	/**
		 * 
		 * @param {*} value 
		 */
	S8_set_logo(value) {
		this.logoNode.style.backgroundImage = `url(${value})`;
	}

	/**
	 * 
	 * @param {*} value 
	 */
	S8_set_title(value) {
		this.titleNode.innerText = value;
	}


	/**
	 * 
	 * @param {*} form 
	 */
	S8_set_form(form) {
		/* remove potential child nodes in containerNode */
		while (this.containerNode.lastChild) { this.containerNode.removeChild(this.containerNode.lastChild); }

		this.containerNode.appendChild(form.getEnvelope());
	}

	S8_dispose() { /* nothing to do */ }
}