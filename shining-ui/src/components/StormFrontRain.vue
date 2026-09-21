<template>
  <div class="storm-front-rain" aria-hidden="true">
    <canvas ref="canvas"></canvas>
  </div>
</template>

<script>
const STORM_CONFIG = {
  background: ['#050812', '#0d1730', '#162744'],
  lights: [
    { x: 0.2, y: 0.14, r: 0.26, color: 'rgba(147, 197, 253, 0.14)' },
    { x: 0.82, y: 0.2, r: 0.3, color: 'rgba(219, 234, 254, 0.08)' },
  ],
  mist: { color: '#bfdbfe', opacity: 0.12, speed: 0.32, start: 0.18, step: 0.12 },
  groundGlow: { color: '#93c5fd', opacity: 0.18, size: 0.22 },
  ground: 0.8,
  layers: [
    {
      count: 220,
      color: '#dbeafe',
      speed: [920, 1240],
      length: [20, 36],
      drift: [-80, -40],
      opacity: [0.12, 0.28],
      width: [1.0, 1.55],
    },
    {
      count: 130,
      color: '#60a5fa',
      speed: [1280, 1620],
      length: [34, 72],
      drift: [-110, -64],
      opacity: [0.18, 0.34],
      width: [1.35, 2.0],
    },
  ],
  splashes: { pieces: 3, gravity: 2600, life: [0.1, 0.22], color: '#dbeafe' },
  ripples: { life: [0.35, 0.7], max: [16, 46], opacity: 0.28, color: '#bfdbfe' },
  lightning: { rate: 0.09, fade: 0.9, overlay: 0.16, color: '#dbeafe' },
};

export default {
  name: 'StormFrontRain',
  data() {
    return {
      canvas: null,
      ctx: null,
      dpr: Math.min(window.devicePixelRatio || 1, 2),
      width: 0,
      height: 0,
      groundY: 0,
      drops: [],
      splashes: [],
      ripples: [],
      lightning: null,
      rafId: null,
      frameLoop: null,
      lastTime: performance.now(),
      time: 0,
    };
  },
  mounted() {
    this.canvas = this.$refs.canvas;
    this.ctx = this.canvas.getContext('2d');
    this.resize();
    window.addEventListener('resize', this.resize);
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    this.frameLoop = (now) => this.animate(now);
    this.rafId = requestAnimationFrame(this.frameLoop);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.resize);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
    }
  },
  methods: {
    rand(min, max) {
      return min + Math.random() * (max - min);
    },
    sample(range) {
      return Array.isArray(range) ? this.rand(range[0], range[1]) : range;
    },
    hexToRgb(hex) {
      const clean = hex.replace('#', '');
      const value = parseInt(clean, 16);
      return {
        r: (value >> 16) & 255,
        g: (value >> 8) & 255,
        b: value & 255,
      };
    },
    rgba(hex, alpha) {
      const { r, g, b } = this.hexToRgb(hex);
      return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    },
    createDrop(layer, seeded = false) {
      const drop = { layer };
      this.resetDrop(drop, seeded);
      return drop;
    },
    resetDrop(drop, seeded = false) {
      drop.x = this.rand(-this.width * 0.08, this.width * 1.08);
      drop.y = seeded ? this.rand(-this.height, this.height) : this.rand(-this.height * 0.4, -24);
      drop.speed = this.sample(drop.layer.speed);
      drop.length = this.sample(drop.layer.length);
      drop.width = this.sample(drop.layer.width);
      drop.opacity = this.sample(drop.layer.opacity);
      drop.drift = this.sample(drop.layer.drift);
      drop.slant = drop.drift * (drop.length / drop.speed);
    },
    resize() {
      this.dpr = Math.min(window.devicePixelRatio || 1, 2);
      this.width = window.innerWidth;
      this.height = window.innerHeight;
      this.groundY = this.height * STORM_CONFIG.ground;
      this.canvas.width = Math.round(this.width * this.dpr);
      this.canvas.height = Math.round(this.height * this.dpr);
      this.canvas.style.width = `${this.width}px`;
      this.canvas.style.height = `${this.height}px`;
      this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);

      this.drops = [];
      this.splashes = [];
      this.ripples = [];
      this.lightning = null;

      STORM_CONFIG.layers.forEach((layer) => {
        for (let index = 0; index < layer.count; index += 1) {
          this.drops.push(this.createDrop(layer, true));
        }
      });
    },
    handleVisibilityChange() {
      this.lastTime = performance.now();
    },
    spawnSplash(x, y, color) {
      for (let index = 0; index < STORM_CONFIG.splashes.pieces; index += 1) {
        this.splashes.push({
          x,
          y,
          vx: this.rand(-70, 70),
          vy: this.rand(-180, -60),
          width: this.rand(0.7, 1.4),
          length: this.rand(4, 10),
          alpha: this.rand(0.2, 0.45),
          life: this.sample(STORM_CONFIG.splashes.life),
          age: 0,
          gravity: STORM_CONFIG.splashes.gravity,
          color,
        });
      }
    },
    spawnRipple(x, y) {
      this.ripples.push({
        x,
        y,
        age: 0,
        life: this.sample(STORM_CONFIG.ripples.life),
        max: this.sample(STORM_CONFIG.ripples.max),
        alpha: STORM_CONFIG.ripples.opacity,
        color: STORM_CONFIG.ripples.color,
      });
    },
    buildLightning() {
      const endY = this.rand(this.height * 0.35, this.groundY * 0.95);
      let x = this.rand(this.width * 0.18, this.width * 0.82);
      let y = -20;
      const points = [{ x, y }];
      while (y < endY) {
        x += this.rand(-34, 34);
        y += this.rand(20, 48);
        points.push({ x, y });
      }
      return { alpha: 1, points };
    },
    update(dt) {
      this.time += dt;
      this.drops.forEach((drop) => {
        drop.x += drop.drift * dt;
        drop.y += drop.speed * dt;

        const outOfBounds = drop.y > this.height + drop.length || drop.x < -140 || drop.x > this.width + 140;
        const impact = drop.y >= this.groundY;
        if (impact) {
          this.spawnSplash(drop.x + drop.slant, this.groundY, STORM_CONFIG.splashes.color);
          this.spawnRipple(drop.x + drop.slant, this.groundY);
          this.resetDrop(drop, false);
        } else if (outOfBounds) {
          this.resetDrop(drop, false);
        }
      });

      this.splashes = this.splashes.filter((splash) => {
        splash.age += dt;
        splash.x += splash.vx * dt;
        splash.y += splash.vy * dt;
        splash.vy += splash.gravity * dt;
        return splash.age < splash.life;
      });

      this.ripples = this.ripples.filter((ripple) => {
        ripple.age += dt;
        return ripple.age < ripple.life;
      });

      if (!this.lightning && Math.random() < STORM_CONFIG.lightning.rate * dt) {
        this.lightning = this.buildLightning();
      }
      if (this.lightning) {
        this.lightning.alpha *= STORM_CONFIG.lightning.fade;
        if (this.lightning.alpha < 0.03) {
          this.lightning = null;
        }
      }
    },
    drawBackdrop() {
      const gradient = this.ctx.createLinearGradient(0, 0, 0, this.height);
      STORM_CONFIG.background.forEach((color, index) => {
        const stop = index / (STORM_CONFIG.background.length - 1);
        gradient.addColorStop(stop, color);
      });
      this.ctx.fillStyle = gradient;
      this.ctx.fillRect(0, 0, this.width, this.height);

      STORM_CONFIG.lights.forEach((light) => {
        const radius = Math.min(this.width, this.height) * light.r;
        const glow = this.ctx.createRadialGradient(
          this.width * light.x,
          this.height * light.y,
          0,
          this.width * light.x,
          this.height * light.y,
          radius
        );
        glow.addColorStop(0, light.color);
        glow.addColorStop(1, 'rgba(255,255,255,0)');
        this.ctx.fillStyle = glow;
        this.ctx.fillRect(0, 0, this.width, this.height);
      });

      for (let index = 0; index < 3; index += 1) {
        const mist = STORM_CONFIG.mist;
        const y = this.height * (mist.start + mist.step * index) + Math.sin(this.time * mist.speed + index * 1.8) * 12;
        const layerGlow = this.ctx.createLinearGradient(0, y - 90, 0, y + 90);
        layerGlow.addColorStop(0, 'rgba(255,255,255,0)');
        layerGlow.addColorStop(0.5, this.rgba(mist.color, mist.opacity));
        layerGlow.addColorStop(1, 'rgba(255,255,255,0)');
        this.ctx.fillStyle = layerGlow;
        this.ctx.fillRect(0, y - 90, this.width, 180);
      }

      const glow = STORM_CONFIG.groundGlow;
      const band = this.ctx.createLinearGradient(0, this.groundY - this.height * glow.size, 0, this.groundY + this.height * 0.04);
      band.addColorStop(0, 'rgba(255,255,255,0)');
      band.addColorStop(0.5, this.rgba(glow.color, glow.opacity));
      band.addColorStop(1, 'rgba(255,255,255,0)');
      this.ctx.fillStyle = band;
      this.ctx.fillRect(0, this.groundY - this.height * glow.size, this.width, this.height * glow.size + 40);

      const vignette = this.ctx.createRadialGradient(this.width * 0.5, this.height * 0.45, 0, this.width * 0.5, this.height * 0.45, this.width * 0.7);
      vignette.addColorStop(0, 'rgba(0,0,0,0)');
      vignette.addColorStop(1, 'rgba(0,0,0,0.24)');
      this.ctx.fillStyle = vignette;
      this.ctx.fillRect(0, 0, this.width, this.height);
    },
    drawRain() {
      this.ctx.lineCap = 'round';
      this.drops.forEach((drop) => {
        this.ctx.strokeStyle = this.rgba(drop.layer.color, drop.opacity);
        this.ctx.lineWidth = drop.width;
        this.ctx.beginPath();
        this.ctx.moveTo(drop.x, drop.y);
        this.ctx.lineTo(drop.x + drop.slant, drop.y + drop.length);
        this.ctx.stroke();
      });
    },
    drawSplashes() {
      this.splashes.forEach((splash) => {
        const progress = splash.age / splash.life;
        this.ctx.strokeStyle = this.rgba(splash.color, splash.alpha * (1 - progress));
        this.ctx.lineWidth = splash.width;
        this.ctx.beginPath();
        this.ctx.moveTo(splash.x, splash.y);
        this.ctx.lineTo(splash.x + splash.vx * 0.012, splash.y + splash.length);
        this.ctx.stroke();
      });
    },
    drawRipples() {
      this.ripples.forEach((ripple) => {
        const progress = ripple.age / ripple.life;
        const radius = ripple.max * progress;
        this.ctx.strokeStyle = this.rgba(ripple.color, ripple.alpha * (1 - progress));
        this.ctx.lineWidth = 1.1;
        this.ctx.beginPath();
        this.ctx.ellipse(ripple.x, ripple.y + 2, radius, radius * 0.28, 0, 0, Math.PI * 2);
        this.ctx.stroke();
      });
    },
    drawLightning() {
      if (!this.lightning) {
        return;
      }

      this.ctx.save();
      this.ctx.fillStyle = `rgba(255,255,255,${STORM_CONFIG.lightning.overlay * this.lightning.alpha})`;
      this.ctx.fillRect(0, 0, this.width, this.height);
      this.ctx.strokeStyle = this.rgba(STORM_CONFIG.lightning.color, 0.9 * this.lightning.alpha);
      this.ctx.lineWidth = 2.2;
      this.ctx.shadowBlur = 18;
      this.ctx.shadowColor = this.rgba(STORM_CONFIG.lightning.color, 0.7 * this.lightning.alpha);
      this.ctx.beginPath();
      this.lightning.points.forEach((point, index) => {
        if (index === 0) {
          this.ctx.moveTo(point.x, point.y);
        } else {
          this.ctx.lineTo(point.x, point.y);
        }
      });
      this.ctx.stroke();
      this.ctx.restore();
    },
    animate(now) {
      const dt = Math.min((now - this.lastTime) / 1000, 0.033);
      this.lastTime = now;
      this.update(dt);
      this.drawBackdrop();
      this.drawRain();
      this.drawSplashes();
      this.drawRipples();
      this.drawLightning();
      this.rafId = requestAnimationFrame(this.frameLoop);
    },
  },
};
</script>

<style scoped>
.storm-front-rain {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
