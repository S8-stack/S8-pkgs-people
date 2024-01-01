import { S8 } from "/S8-api/S8Context.js";
import { S8Object } from "/S8-api/S8Object.js";
import { InboardMessage } from "/S8-pkgs-people/InboardMessage.js";



export const VALIDATE_MODE = 2;
export const WARNING_MODE = 3;
export const ERROR_MODE = 4;


/**
 * 
 */
export class InboardMessageSlot {



	/**
	 * @type {boolean}
	 */
	hasMessage = false;



    /**
     * 
     */
    constructor() {
		let wrapperNode = document.createElement("div");
		wrapperNode.style.display = "none";
		this.wrapperNode = wrapperNode;
    }


    getEnvelope(){
        return this.wrapperNode;
    }



	/**
	 * 
	 * @param {InboardMessage} message 
	 */
	setMessage(message){
		if(message != null){

            /* remove potential child nodes */
            while(this.wrapperNode.lastChild){  this.wrapperNode.removeChild(this.wrapperNode.lastChild); }

			this.wrapperNode.style.display = "block";
			this.wrapperNode.appendChild(message.getEnvelope());
			this.hasMessage = true;
		}
		else{
			this.clearMessage();
		}
	}

	clearMessage() {
		if(this.hasMessage){
             /* remove potential child nodes */
            while(this.wrapperNode.lastChild){  this.wrapperNode.removeChild(this.wrapperNode.lastChild); }

			this.wrapperNode.style.display = "none";
			this.hasMessage = false;
		}
	}

}