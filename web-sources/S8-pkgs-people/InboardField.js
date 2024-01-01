
import { NeObject } from '/S8-core-bohr-neon/NeObject.js';
import { InboardMessage, VALIDATE_MODE, WARNING_MODE } from '/S8-pkgs-people/InboardMessage.js';

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/inboard.css');



export class InboardField extends NeObject {



	/**
	 * @type {boolean}
	 */
	hasMessage = false;



	/**
	 * @type{HTMLDivElement}
	 */
	wrapperNode;


	/**
	 * @type{HTMLInputElement}
	 */
	inputNode;



	constructor(id, type, label, placeholder,) {
		super();

		this.wrapperNode = document.createElement("div");
		this.wrapperNode.classList.add("inboard-field");


		/* <label for="username">Username</label> */
		this.labelNode = document.createElement("label");
		this.labelNode.setAttribute("for", id);
		this.labelNode.innerText = label;
		this.wrapperNode.appendChild(this.labelNode);


		/* <input type="text" placeholder="Email" id="username"> */
		this.inputNode = document.createElement("input");
		this.inputNode.setAttribute("type", type);
		this.inputNode.setAttribute("placeholder", placeholder);
		this.wrapperNode.appendChild(this.inputNode);


		this.messageNode = document.createElement("div");
		this.messageNode.style.display = "none";
		this.wrapperNode.appendChild(this.messageNode);

	}

    getEnvelope(){
        return this.wrapperNode;
    }


	getValue(){
		return this.inputNode.value;
	}


	setMaxLength(length){
		this.inputNode.setAttribute("maxlength", length);
	}


    setValidateMessage(text){
        let message = new InboardMessage();
        message.S8_set_mode(VALIDATE_MODE);
        message.S8_set_text(text);
        this.setMessage(message);
    }

    setWarningMessage(text){
        let message = new InboardMessage();
        message.S8_set_mode(WARNING_MODE);
        message.S8_set_text(text);
        this.setMessage(message);
    }

    setErrorMessage(text){
        let message = new InboardMessage();
        message.S8_set_mode(WARNING_MODE);
        message.S8_set_text(text);
        this.setMessage(message);
    }


	/** @param {InboardMessage} message */
	setMessage(message){
		if(message != null){
			this;this.clearMessageSlot();
			this.messageNode.style.display = "block";
			this.messageNode.appendChild(message.getEnvelope());
			this.hasMessage = true;
		}
		else{
			this.clearMessage();
		}
	}

	/** @param {InboardMessage} message */
	S8_set_message(message){ this.setMessage(message); }

	clearMessage() {
		if(this.hasMessage){
            this.clearMessageSlot();
			this.messageNode.style.display = "none";
			this.hasMessage = false;
		}
	}

	clearMessageSlot(){
 		/* remove potential child nodes */
 		while(this.messageNode.lastChild){  this.messageNode.removeChild(this.messageNode.lastChild); }
	}



	S8_render() { /* continuous rendering approach... */ }

	S8_dispose(){ /* nothing to do */ }
}