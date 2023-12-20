
import { InboardBox } from './InboardBox.js';
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';

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



	constructor() {
		super();

		this.formNode = document.createElement("div");
		this.formNode.classList.add("inboard-form");

		/* <title> */
		this.titleNode = document.createElement("h1");
		this.titleNode.innerText = "<Title>";
		this.formNode.appendChild(this.titleNode);

		let subTitleNode = document.createElement("h3");
		subTitleNode.innerText = "Sign-up";
		this.formNode.appendChild(subTitleNode);


		/* <label for="username">Username</label> */
		let usernameLabelNode = document.createElement("label");
		usernameLabelNode.setAttribute("for", "username");
		usernameLabelNode.innerText = "Username";
		this.formNode.appendChild(usernameLabelNode);


		/* <input type="text" placeholder="Email" id="username"> */
		let usernameInputNode = document.createElement("input");
		usernameInputNode.setAttribute("type", "text");
		usernameInputNode.setAttribute("placeholder", "Email");
		//usernameInputNode.setAttribute("id", "username");
		this.formNode.appendChild(usernameInputNode);

		this.usernameInputNode = usernameInputNode;

		/* <define-password> */
		/* <label for="password">Define Password</label> */
		let definePasswordLabelNode = document.createElement("label");
		definePasswordLabelNode.setAttribute("for", "password");
		definePasswordLabelNode.innerText = "Define Password";
		this.formNode.appendChild(definePasswordLabelNode);


		/* <input type="password" placeholder="Password" id="password"> */
		let definePasswordInputNode = document.createElement("input");
		definePasswordInputNode.setAttribute("type", "password");
		definePasswordInputNode.setAttribute("placeholder", "Password");
		//passwordInputNode.setAttribute("id", "password");
		this.formNode.appendChild(definePasswordInputNode);
		this.definaPasswordInputNode = definePasswordInputNode;


		/* </define-password> */


		/* <confirm-password> */

		/* <label for="password">Password</label> */
		let confirmPasswordLabelNode = document.createElement("label");
		confirmPasswordLabelNode.setAttribute("for", "password");
		confirmPasswordLabelNode.innerText = "Confirm Password";
		this.formNode.appendChild(confirmPasswordLabelNode);

		/* <input type="password" placeholder="Password" id="password"> */
		let confirmPasswordInputNode = document.createElement("input");
		confirmPasswordInputNode.setAttribute("type", "password");
		confirmPasswordInputNode.setAttribute("placeholder", "Confirm Password");
		//passwordInputNode.setAttribute("id", "password");
		this.formNode.appendChild(confirmPasswordInputNode);
		this.confirmPasswordInputNode = confirmPasswordInputNode;

		/* </confirm-password> */

		const _this = this;

		/* <button>Log In</button> */
		let actionButtonNode = document.createElement("button");
		actionButtonNode.classList.add("inboard-button-action");
		actionButtonNode.innerText = "Sign Up";
		
		actionButtonNode.addEventListener("click", function () {
			const credentials = [_this.usernameInputNode.value, _this.passwordInputNode.value];
			/*S8WebFront.loseFocus();*/
			_this.S8_vertex.runStringUTF8Array("on-trying-login", credentials);
		});
		this.actionButtonNode = actionButtonNode;
		this.formNode.appendChild(actionButtonNode);

		/* <button>Go to LogIn</button> */
		let gotoButtonNode = document.createElement("button");
		gotoButtonNode.classList.add("inboard-button-goto");
		gotoButtonNode.innerText = "Go to LogIn";
		gotoButtonNode.addEventListener("click", function() { _this.box.swap(); });
		this.gotoButtonNode = gotoButtonNode;
		this.formNode.appendChild(gotoButtonNode);
	}


	getEnvelope() {
		return this.formNode;
	}

	S8_render() { /* continuous rendering approach... */ }


	S8_set_title(value) {
		this.titleNode.innerText = value;
	}

	S8_dispose() { /* nothing to do */ }
}