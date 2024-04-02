
import { S8 } from "/S8-api/S8Context.js";
import { S8Object } from "/S8-api/S8Object.js";

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/menu/P8Menu.css');


/**
 * 
 */
export class P8MenuIcon extends S8Object {


    /**
     * 
     */
    constructor(){
        super();
        this.wrapperNode = document.createElement("div");
        this.wrapperNode.classList.add("p8menu-icon");

        S8.page.SVG_insertByPathname(this.wrapperNode, "/S8-pkgs-people/icons/person-circle.svg", 32, 32);
    }


    /**
     * 
     * @returns 
     */
    getEnvelope(){
        return this.wrapperNode;
    }


    /**
     * icon
     * @param {*} code 
     */
    S8_set_imageURL(path){
        this.wrapperNode.style.backgroundImage = `url(${path})`;
    }

   /** continuous rendering approach */
    S8_render(){}

    /** continuous rendering approach */
    S8_dispose(){}
}