import { S8 } from "/S8-api/S8Context.js";
import { S8Object } from "/S8-api/S8Object.js";



export const VALIDATE_MODE = 2;
export const WARNING_MODE = 3;
export const ERROR_MODE = 4;


/**
 * 
 */
export class InboardMessage extends S8Object {


    /**
     * 
     */
    constructor() {
        super();

        this.wrapperNode = document.createElement("div");
        this.wrapperNode.classList.add("inboard-message");

        /* <icon> */
        this.iconNode = document.createElement("div");
        this.iconNode.classList.add("inboard-message-icon");
        this.wrapperNode.appendChild(this.iconNode);
        /* </icon> */

        /* <text> */
        this.textNode = document.createElement("div");
        this.textNode.classList.add("inboard-message-text");
        this.wrapperNode.appendChild(this.textNode);
        /* </text> */

    }


    getEnvelope(){
        return this.wrapperNode;
    }


    /**
     * 2 -> validate, 3 -> warning, 4 -> error
     * @param {*} mode 
     */
    S8_set_mode(mode) {

        this.wrapperNode.classList.remove(
            "inboard-message-validate",
            "inboard-message-warning",
            "inboard-message-error");

        switch (mode) {

            case VALIDATE_MODE:
                this.wrapperNode.classList.add("inboard-message-validate");
                S8.page.SVG_insertByPathname(this.iconNode, "/S8-pkgs-people/icons/validate.svg", 16, 16);
                break;

            case WARNING_MODE:
                this.wrapperNode.classList.add("inboard-message-warning");
                S8.page.SVG_insertByPathname(this.iconNode, "/S8-pkgs-people/icons/warning.svg", 16, 16);
                break;

            case ERROR_MODE:
                this.wrapperNode.classList.add("inboard-message-error");
                S8.page.SVG_insertByPathname(this.iconNode, "/S8-pkgs-people/icons/error.svg", 16, 16);
                break;

            default: console.error("Unsupported mode: "+mode); break;
        }
    }

    S8_set_text(text) {
        this.textNode.innerHTML = text;
    }


    S8_render(){ /* nothing to do */ }
    S8_dispose(){ /* nothing to do */ }
}