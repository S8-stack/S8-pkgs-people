
import { InboardMessage, VALIDATE_MODE, WARNING_MODE, ERROR_MODE } from "/S8-pkgs-people/InboardMessage.js";



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