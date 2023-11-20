
import { NeObject } from '/s8-core-io-bohr-neon/NeObject.js';


export class InboardScreen extends NeObject {


	/**
	 * @type{DynamicMeshScreen}
	 */
	background;


	modalBox;


	constructor() {
		super();

		this.wrapperNode = document.createElement("div");

		this.backgroundWrapperNode = document.createElement("div");
		this.wrapperNode.appendChild(this.backgroundWrapperNode);

		this.modalBoxWrapperNode = document.createElement("div");
		this.wrapperNode.appendChild(this.modalBoxWrapperNode);
	}


	S8_render() { /* continuous rendering approach... */ }


	S8_set_background(node) {
		this.backgroundWrapperNode.appendChild(node.getEnvelope());
	}


	S8_set_modalBox(node) {
		this.modalBoxWrapperNode.appendChild(node.getEnvelope());
	}

	getEnvelope() {
		return this.wrapperNode;
	}

	S8_dispose() {
	}

	stop() {
	}

}