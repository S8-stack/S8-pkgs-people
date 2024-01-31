
import { InboardBox } from '/S8-pkgs-people/InboardBox.js';
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';
import { InboardField } from '/S8-pkgs-people/InboardField.js';
import { InboardMessageSlot } from '/S8-pkgs-people/InboardMessageSlot.js';

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/inboard.css');



export class LogInForm extends NeObject {



	/**
	 * @type{InboardBox}
	 */
	box;


	/**
	 * @type{HTMLDivElement}
	 */
	formNode;


	/** @type{InboardField} */
	usernameField;

	/** @type{InboardField} */
	passwordField;


	/**
	 * @type{InboardMessageSlot}
	 */
	feedback;



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
		this.usernameField = new InboardField("username", "text", "Username", "Username");
		this.usernameField.inputNode.addEventListener("click", function() { _this.usernameField.clearMessage(); });
		this.formNode.appendChild(this.usernameField.getEnvelope());
		

		/* <label for="password">Password</label> */
		this.passwordField = new InboardField("password", "password", "Password", "Password");
		this.passwordField.inputNode.addEventListener("click", function() { _this.passwordField.clearMessage(); });
		this.formNode.appendChild(this.passwordField.getEnvelope());

		/* <button>Log In</button> */
		let actionButtonNode = document.createElement("button");
		actionButtonNode.classList.add("inboard-button-action");
		actionButtonNode.innerText = "Log in";
		actionButtonNode.addEventListener("click", function() {
			const credentials = [_this.usernameField.getValue(), _this.passwordField.getValue()];
			/*S8WebFront.loseFocus();*/
           _this.S8_vertex.runStringUTF8Array("on-trying-login", credentials);
        });
		this.actionButtonNode = actionButtonNode;
		this.formNode.appendChild(actionButtonNode);

		/* <button>Go to SignUp</button> */
		let gotoButtonNode = document.createElement("button");
		gotoButtonNode.classList.add("inboard-button-goto");
		gotoButtonNode.innerText = "Go to Sign Up";
		gotoButtonNode.addEventListener("click", function() { _this.S8_vertex.runVoid("goto-signup"); });
		this.gotoButtonNode = gotoButtonNode;
		this.formNode.appendChild(gotoButtonNode);

	}


	getEnvelope() {
		return this.formNode;
	}


	S8_set_message(message ){
		this.passwordField.setMessage(message);
	}


	S8_render() { /* continuous rendering approach... */ }


	S8_dispose(){ /* nothing to do */ }
}