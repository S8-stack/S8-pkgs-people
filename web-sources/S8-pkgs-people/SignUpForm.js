
import { InboardBox } from './InboardBox.js';
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';
import { InboardField } from '/S8-pkgs-people/InboardField.js';
import { InboardMessageSlot } from '/S8-pkgs-people/InboardMessageSlot.js';

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/inboard.css');



export class SignUpForm extends NeObject {


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
	definePasswordField;

	/** @type{InboardField} */
	confirmPasswordField;




	constructor() {
		super();

		const _this = this;

		this.formNode = document.createElement("div");
		this.formNode.classList.add("inboard-form");

		/*
		let subTitleNode = document.createElement("h3");
		subTitleNode.innerText = "Sign-up";
		this.formNode.appendChild(subTitleNode);
		*/

		/* <label for="username">Username</label> */
		this.usernameField = new InboardField("username", "text", "Username", "Email");
		this.usernameField.inputNode.addEventListener("change", function(){
			_this.S8_vertex.runStringUTF8("on-username-change", _this.usernameField.getValue());
		}, false);
		this.formNode.appendChild(this.usernameField.getEnvelope());

		/* <define-password> */
		/* <label for="password">Define Password</label> */
		this.definePasswordField = new InboardField("define", "password", "Define Password", "Password");
		this.definePasswordField.inputNode.addEventListener("input", function(){
			_this.validatePassword();
			_this.validateRecopy();
		}, false);
		this.formNode.appendChild(this.definePasswordField.getEnvelope());
		/* </define-password> */


		/* <confirm-password> */
		this.confirmPasswordField = new InboardField("confirm", "password", "Confirm Password", "Recopy Password");
		this.confirmPasswordField.inputNode.addEventListener("input", function(){
			_this.validateRecopy();
		}, false);
		this.formNode.appendChild(this.confirmPasswordField.getEnvelope());
		/* </confirm-password> */

		/* <button>Log In</button> */
		let actionButtonNode = document.createElement("button");
		actionButtonNode.classList.add("inboard-button-action");
		actionButtonNode.innerText = "Sign Up";
		
		actionButtonNode.addEventListener("click", function () {
			const credentials = [
				_this.usernameField.getValue(), 
				_this.definePasswordField.getValue(),
				_this.confirmPasswordField.getValue()];
			/*S8WebFront.loseFocus();*/
			_this.S8_vertex.runStringUTF8Array("on-signup", credentials);
		});
		this.actionButtonNode = actionButtonNode;
		this.formNode.appendChild(actionButtonNode);

		/* <button>Go to LogIn</button> */
		let gotoButtonNode = document.createElement("button");
		gotoButtonNode.classList.add("inboard-button-goto");
		gotoButtonNode.innerText = "Go to LogIn";
		gotoButtonNode.addEventListener("click", function() { _this.S8_vertex.runVoid("goto-login"); });
		this.gotoButtonNode = gotoButtonNode;
		this.formNode.appendChild(gotoButtonNode);
	}


	validatePassword(){
		let message = this.checkPassword();
		if(message.length > 0){
			this.isPasswordDefined = false;
			this.definePasswordField.setWarningMessage(message);
		}
		else {
			this.isPasswordDefined = true;
			this.definePasswordField.setValidateMessage("Password is valid");
		}
	}

	checkPassword(){
		let password = this.definePasswordField.getValue();
		let n = PASSWORD_VALIDATION_RULES.length;
		for(let i = 0; i<n; i++){
			let rule = PASSWORD_VALIDATION_RULES[i];
			if(!rule.validate(password)){ 
				return rule.text;
			}
		}
		return "";
	}

	validateRecopy(){
		let dPassword = this.definePasswordField.getValue();
		let cPassword = this.confirmPasswordField.getValue();
		if(dPassword != cPassword){
			this.confirmPasswordField.setWarningMessage("Passwords do not match");
		}
		else {
			this.confirmPasswordField.setValidateMessage("Password confirmed!");
		}
	}
	


	getEnvelope() {
		return this.formNode;
	}

	S8_render() { /* continuous rendering approach... */ }


	S8_set_usernameFeedbackMessage(message){
		this.usernameField.setMessage(message);
	}

	S8_set_definePasswordFeedbackMessage(message){
		this.definePasswordField.setMessage(message);
	}

	S8_set_confirmPasswordFeedbackMessage(message){
		this.confirmPasswordField.setMessage(message);
	}

	S8_dispose() { /* nothing to do */ }
}



class Rule {

	constructor(text, check){
		this.text = text;
		this.regex = new RegExp(check);
	}

	validate(password) {
		return this.regex.test(password);
	}
}


export const PASSWORD_VALIDATION_RULES = [
	new Rule("At least one upper case English letter", "(?=.*?[A-Z])"),
	new Rule("At least one lower case English letter", "(?=.*?[a-z])"),
	new Rule("At least one digit", "(?=.*?[0-9])"),
	new Rule("At least one special character", "(?=.*?[#?!@$%^&*-])"),
	new Rule("Minimum eight in length", ".{8,}")
];

