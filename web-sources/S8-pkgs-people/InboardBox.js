
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

		this.container1Node = document.createElement("div");
		this.container1Node.classList.add("inboard-container-visible");
		this.boxNode.appendChild(this.container1Node);

		this.container2Node = document.createElement("div");
		this.container2Node.classList.add("inboard-container-hidden");
		this.boxNode.appendChild(this.container2Node);
	}


	swap(){
		if(this.isContainer1Visible){
			this.container1Node.classList.replace("inboard-container-visible", "inboard-container-hidden");
			this.container2Node.classList.replace("inboard-container-hidden", "inboard-container-visible");
			this.isContainer1Visible = false;
		}
		else{
			this.container1Node.classList.replace("inboard-container-hidden", "inboard-container-visible");
			this.container2Node.classList.replace("inboard-container-visible", "inboard-container-hidden");
			this.isContainer1Visible = true;
		}
	}


	getEnvelope() {
		return this.boxNode;
	}

	S8_render() { /* continuous rendering approach... */ }


	S8_set_loginForm(form) {
		form.box = this;
		this.container1Node.appendChild(form.getEnvelope());
	}

	S8_set_signupForm(form) {
		form.box = this;
		this.container2Node.appendChild(form.getEnvelope());
	}

	S8_dispose(){ /* nothing to do */ }
}