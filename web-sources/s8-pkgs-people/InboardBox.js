
import { NeObject } from '/s8-core-io-bohr-neon/NeObject.js';

import { S8WebFront } from "/s8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/s8-pkgs-people/inboard.css');



export class InboardBox extends NeObject {



	/**
	 * @type{HTMLDivElement}
	 */
	formNode;

	/**
	 * @type{DynamicMeshScreen}
	 */
	dynamicMeshScreen;


	constructor() {
		super();

		this.formNode = document.createElement("div");
		this.formNode.classList.add("inboard-box");

		/* <title> */
		this.titleNode = document.createElement("h1");
		this.titleNode.innerText = "<Title>";
		this.formNode.appendChild(this.titleNode);

		let subTitleNode = document.createElement("h3");
		subTitleNode.innerText = "Login";
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

		/* <label for="password">Password</label> */
		let passwordLabelNode = document.createElement("label");
		passwordLabelNode.setAttribute("for", "password");
		passwordLabelNode.innerText = "Password";
		this.formNode.appendChild(passwordLabelNode);


		/* <input type="password" placeholder="Password" id="password"> */
		let passwordInputNode = document.createElement("input");
		passwordInputNode.setAttribute("type", "password");
		passwordInputNode.setAttribute("placeholder", "Password");
		//passwordInputNode.setAttribute("id", "password");
		this.formNode.appendChild(passwordInputNode);
		this.passwordInputNode = passwordInputNode;

		/* <button>Log In</button> */
		let buttonNode = document.createElement("button");
		buttonNode.innerText = "Log IN";
		let _this = this;
		buttonNode.addEventListener("click", function() {
			const credentials = [_this.usernameInputNode.value, _this.passwordInputNode.value];
			/*S8WebFront.loseFocus();*/
           _this.S8_vertex.runStringUTF8Array("on-trying-login", credentials);
        });
		this.buttonNode = buttonNode;
		this.formNode.appendChild(buttonNode);
	}


	getEnvelope() {
		return this.formNode;
	}

	S8_render() { /* continuous rendering approach... */ }


	S8_set_title(value) {
		this.titleNode.innerText = value;
	}

	S8_dispose(){ /* nothing to do */ }
}