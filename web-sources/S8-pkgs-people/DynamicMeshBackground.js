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
	 * @type{CanvasRenderingContext2D}
	 */
	context;

	/**
	 * @type{string} RGBA format particle color
	 */
	particleColor = [128, 160, 200];

	/**
	 * @type{Number[]} RGBA line color
	 */
	lineColor = [180, 180, 212];

	/**
	 * @type{number} nb of particels
	 */
	particleAmount = 64;

	defaultSpeed = 0.4;

	variantSpeed = 0.4;

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


	xShifts;
	yShifts;

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
		this.context = this.canvasBody.getContext("2d");
		this.canvasBody.classList.add("dynamic-mesh-background");
		this.start();
	}


	getEnvelope() {
		return this.canvasBody;
	}



	resizeReset() {
		this.w = this.canvasBody.width = window.innerWidth;
		this.h = this.canvasBody.height = window.innerHeight;

		this.xShifts = new Array(9);
		this.yShifts = new Array(9);
		const w = this.w;
		const h = this.h;
		
		this.xShifts[0] = 0; this.yShifts[0] =-h;
		this.xShifts[1] = 0; this.yShifts[1] = 0;
		this.xShifts[2] = 0; this.yShifts[2] = h;
		this.xShifts[3] = w; this.yShifts[3] =-h;
		this.xShifts[4] = w; this.yShifts[4] = 0;
		this.xShifts[5] = w; this.yShifts[5] = h;
		this.xShifts[6] =-w; this.yShifts[6] =-h;
		this.xShifts[7] =-w; this.yShifts[7] = 0;
		this.xShifts[8] =-w; this.yShifts[8] = h;
	}


	deBounce() {
		let tid = 0;
		clearTimeout(tid);
		let _this = this;
		tid = setTimeout(function () {
			_this.resizeReset();
		}, this.delay);
	}



	redraw() {
		this.drawArea.clearRect(0, 0, this.w, this.h);
		let nParticles = this.particles.length;

		this.context.shadowBlur = 4;
		this.context.shadowColor = "white";
		for (let i = 0; i < nParticles; i++) {
			let particle = this.particles[i];
			particle.update();
			particle.drawBall();
		}

		this.context.shadowBlur = 0;
		for (let i = 0; i < nParticles; i++) {
			this.particles[i].drawLinks();
		}


		let _this = this;
		this.requestAnimationID = window.requestAnimationFrame(function () { _this.redraw() });
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
			this.particles.push(new Particle(this, i));
		}
		this.requestAnimationID = window.requestAnimationFrame(function () {
			_this.redraw();
		});
	}

	stop() {
		window.cancelAnimationFrame(this.requestAnimationID);
	}


	S8_render() {
		/* nothing to do here */
	}


	S8_set_particleColor(color) {
		this.particleColor = color;
	}

	S8_set_particleAmount(n) {
		this.particleAmount = n;
	}

	S8_set_lineColor(color) {
		this.lineColor = color;
	}

	S8_dispose() {
		this.stop();
	}
}



class Particle {

	/**
	 * @type{DynamicMeshBackground} screen
	 */
	screen;

	/** @type{number} : index */
	index;


	/**
	 * 
	 * @param {DynamicMeshBackground} screen 
	 * @param {number} index 
	 */
	constructor(screen, index) {
		this.screen = screen;
		this.index = index;

		this.x = Math.random() * screen.w;
		this.y = Math.random() * screen.h;

		this.speed = screen.defaultSpeed + Math.random() * screen.variantSpeed;
		this.directionAngle = Math.floor(Math.random() * 360);

		const rgb = screen.particleColor;
		this.color = `rgb(${rgb[0]}, ${rgb[0]}, ${rgb[0]})`;
		this.radius = screen.defaultRadius + Math.random() * screen.variantRadius;
		this.vx = Math.cos(this.directionAngle) * this.speed;
		this.vy = Math.sin(this.directionAngle) * this.speed;
	}


	update() {
		this.border();
		this.x += this.vx;
		this.y += this.vy;
	}

	border() {
		/*
		if (this.x >= this.screen.w || this.x <= 0) {
			this.vx *= -1;
		}
		if (this.y >= this.screen.h || this.y <= 0) {
			this.vy *= -1;
		}
		*/
		if (this.x > this.screen.w) { this.x = 0; }
		if (this.y > this.screen.h) { this.y = 0; }
		if (this.x < 0) { this.x = this.screen.w; }
		if (this.y < 0) { this.y = this.screen.h; }
	}

	distance(x, y) {
		let dx = x - this.x;
		let dy = y - this.y;
		return Math.sqrt(dx * dx + dy * dy);
	}


	drawBall() {
		let drawArea = this.screen.drawArea;

		drawArea.beginPath();
		drawArea.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
		drawArea.closePath();
		drawArea.fillStyle = this.color;
		drawArea.fill();
	};


	/**
	 * 
	 * @param {DynamicMeshBackground} screen 
	 * @param {*} hubs 
	 * @param {*} lineColor 
	 * @param {*} linkRadius 
	 */
	drawLinks() {

		const drawArea = this.screen.drawArea;
		const particles = this.screen.particles;
		const rgb = this.screen.lineColor;
		const linkRadius = this.screen.linkRadius;
		const xShifts = this.screen.xShifts, yShifts = this.screen.yShifts;

		let xActual, yActual, xShifted, yShifted;

		for (let i = 0; i < particles.length; i++) {
			let particle = particles[i];
			xActual = this.x; yActual = this.y;

			if (this.index < particle.index) {
				for (let j = 0; j < 8; j++) {
					xShifted = xActual + xShifts[j]; yShifted = yActual + yShifts[j];
					let distance = particle.distance(xShifted, yShifted);
					let opacity = 1 - distance / linkRadius;
	
					if (opacity > 0) {
						drawArea.lineWidth = 0.25;
						drawArea.strokeStyle = `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${opacity})`;
						drawArea.beginPath();
						drawArea.moveTo(xShifted, yShifted);
						drawArea.lineTo(particle.x, particle.y);
						drawArea.closePath();
						drawArea.stroke();
					}
				}
			}
		}
	}
};

