import { NeObject } from "/S8-core-bohr-neon/NeObject.js";

import { S8WebFront } from "/S8-pkgs-ui-carbide/S8WebFront.js";


/**
 * 
 */
S8WebFront.CSS_import('/S8-pkgs-people/DynamicMeshBackground.css');

/**
 * 
 */
export class DynamicMeshBackground extends NeObject {


	/**
	 * @type{string} RGBA format particle color
	 */
	particleColor = [200, 200, 200];

	/**
	 * @type{string} RGBA line color
	 */
	lineColor = [200, 200, 255];
	
	/**
	 * @type{number} nb of particels
	 */
	particleAmount = 64;
	
	defaultSpeed = 1;
	
	variantSpeed = 1;

	defaultRadius = 2;
	
	variantRadius = 2;

	linkRadius = 256;

	/**
	 * @type{number} screen width
	 */
	w;


	/**
	 * @type{number} screen height
	 */
	h;

	drawArea;


	/**
	 * @type{Array<Particle>}
	 */
	particles;


	delay = 200;


	/**
	 * @type{string}
	 */
	requestAnimationID;

	constructor() {
		super();
		
		this.canvasBody = document.createElement("canvas");
		this.canvasBody.classList.add("dynamic-mesh-background");
		this.start();
	}
	
	
	getEnvelope(){
		return this.canvasBody;
	}



	resizeReset() {
		this.w = this.canvasBody.width = window.innerWidth;
		this.h = this.canvasBody.height = window.innerHeight;
	}


	deBounce() {
		let tid = 0;
		clearTimeout(tid);
		let _this = this;
		tid = setTimeout(function () {
			_this.resizeReset();
		}, this.delay);
	}

	checkDistance(x1, y1, x2, y2) {
	
		return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
	}


	linkPoints(point1, hubs) {
		let rgb = this.lineColor;

		for (let i = 0; i < hubs.length; i++) {
			let distance = this.checkDistance(point1.x, point1.y, hubs[i].x, hubs[i].y);
			let opacity = 1 - distance / this.linkRadius;
			
			if (opacity > 0) {
				this.drawArea.lineWidth = 0.25;
				this.drawArea.strokeStyle = `rgba(${rgb[0]}, ${rgb[0]}, ${rgb[0]}, ${opacity})`;
				this.drawArea.beginPath();
				this.drawArea.moveTo(point1.x, point1.y);
				this.drawArea.lineTo(hubs[i].x, hubs[i].y);
				this.drawArea.closePath();
				this.drawArea.stroke();
			}
		}
	}

	redraw() {
		this.drawArea.clearRect(0, 0, this.w, this.h);
		let nParticles = this.particles.length;
		for (let i = 0; i < nParticles; i++) {
			let particle = this.particles[i];
			particle.update();
			particle.draw();
		}
		for (let i = 0; i < nParticles; i++) {
			this.linkPoints(this.particles[i], this.particles);
		}


		let _this = this;
		this.requestAnimationID = window.requestAnimationFrame(function(){ _this.redraw() });
	}

	start() {

		let _this = this;

		this.drawArea = this.canvasBody.getContext("2d");


		window.addEventListener("resize", function () {
			_this.deBounce();
		});

		this.resizeReset();

		this.particles = [];
		for (let i = 0; i < this.particleAmount; i++) {
			this.particles.push(new Particle(this));
		}
		this.requestAnimationID = window.requestAnimationFrame(function () {
			_this.redraw();
		});
	}
	
	stop(){
		window.cancelAnimationFrame(this.requestAnimationID);
	}


	S8_render() {
		/* nothing to do here */
	}


	S8_set_particleColor(color){
		this.particleColor = color;
	}

	S8_set_particleAmount(n){
		this.particleAmount = n;
	}

	S8_set_lineColor(color){
		this.lineColor = color;
	}

	S8_dispose() {
		this.stop();
	}
}



class Particle {



	/**
	 * @type{DynamicMeshScreen} screen
	 */
	screen;


	/**
	 * 
	 * @param {DynamicMeshScreen} screen 
	 */
	constructor(screen) {
		this.screen = screen;

		this.x = Math.random() * screen.w;
		this.y = Math.random() * screen.h;

		this.speed = screen.defaultSpeed + Math.random() * screen.variantSpeed;
		this.directionAngle = Math.floor(Math.random() * 360);

		const rgb = screen.particleColor;
		this.color = `rgb(${rgb[0]}, ${rgb[0]}, ${rgb[0]})`;
		this.radius = screen.defaultRadius + Math.random() * screen.variantRadius;
		this.vector = {
			x: Math.cos(this.directionAngle) * this.speed,
			y: Math.sin(this.directionAngle) * this.speed
		};
	}

	update() {
		this.border();
		this.x += this.vector.x;
		this.y += this.vector.y;
	}

	border() {
		if (this.x >= this.screen.w || this.x <= 0) {
			this.vector.x *= -1;
		}
		if (this.y >= this.screen.h || this.y <= 0) {
			this.vector.y *= -1;
		}
		if (this.x > this.screen.w) this.x = this.screen.w;
		if (this.y > this.screen.h) this.y = this.screen.h;
		if (this.x < 0) this.x = 0;
		if (this.y < 0) this.y = 0;
	}

	draw() {
		let drawArea = this.screen.drawArea;

		drawArea.beginPath();
		drawArea.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
		drawArea.closePath();
		drawArea.fillStyle = this.color;
		drawArea.fill();
	};
};

