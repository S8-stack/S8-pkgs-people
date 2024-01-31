
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';
import { InboardField } from '/S8-pkgs-people/InboardField.js';


import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/inboard.css');



export class ValidateForm extends NeObject {


	/**
	 * @type{HTMLDivElement}
	 */
	formNode;


	/** @type{InboardField} */
	codeField;

	/** @type{InboardField} */
	codeField;



	constructor() {
		super();

		this.formNode = document.createElement("div");
		this.formNode.classList.add("inboard-form");

		/* <title> */
		

		/*
		let subTitleNode = document.createElement("h3");
		subTitleNode.innerText = "Login";
		this.formNode.appendChild(subTitleNode);
		*/

		const _this = this;

		/* <label for="username">Username</label> */
		this.codeField = new InboardField("code", "text", "Validation code from email:", "Code");
		this.codeField.inputNode.addEventListener("click", function() { _this.codeField.clearMessage(); });
		this.formNode.appendChild(this.codeField.getEnvelope());
		

		/* <button>Log In</button> */
		let actionButtonNode = document.createElement("button");
		actionButtonNode.classList.add("inboard-button-action");
		actionButtonNode.innerText = "Validate";
		actionButtonNode.addEventListener("click", function() {
			const code = _this.codeField.getValue();
			/*S8WebFront.loseFocus();*/
           _this.S8_vertex.runStringUTF8("on-trying-validate", code);
        });
		this.actionButtonNode = actionButtonNode;
		this.formNode.appendChild(actionButtonNode);

		/* <button>Go to SignUp</button> */
		let gotoButtonNode = document.createElement("button");
		gotoButtonNode.classList.add("inboard-button-goto");
		gotoButtonNode.innerText = "Go to Log-in";
		gotoButtonNode.addEventListener("click", function() { _this.S8_vertex.runVoid("goto-login"); });
		this.gotoButtonNode = gotoButtonNode;
		this.formNode.appendChild(gotoButtonNode);

	}


	getEnvelope() {
		return this.formNode;
	}


	S8_set_message(message){
		this.codeField.setMessage(message);
	}

	S8_set_codeLength(length){
		this.codeField.setMaxLength(length);
	}

	S8_render() { /* continuous rendering approach... */ }


	S8_dispose(){ /* nothing to do */ }
}