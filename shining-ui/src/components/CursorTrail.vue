<template>
  <div class="cursor-trail">
    <canvas ref="canvas"></canvas>
  </div>
</template>

<script>
export default {
  name: 'CursorTrail',
  data() {
    return {
      canvas: null,
      ctx: null,
      particles: [],
      rafId: null,
      dpr: window.devicePixelRatio || 1,
      lastEmitTime: 0,
    };
  },
  mounted() {
    this.canvas = this.$refs.canvas;
    this.ctx = this.canvas.getContext('2d');
    this.resize();
    window.addEventListener('resize', this.resize);
    window.addEventListener('mousemove', this.handleMove);
    this.rafId = requestAnimationFrame(this.animate);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.resize);
    window.removeEventListener('mousemove', this.handleMove);
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
    }
  },
  methods: {
    resize() {
      const width = window.innerWidth;
      const height = window.innerHeight;
      const dpr = this.dpr;
      this.canvas.width = width * dpr;
      this.canvas.height = height * dpr;
      this.canvas.style.width = width + 'px';
      this.canvas.style.height = height + 'px';
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    },
    handleMove(event) {
      const now = performance.now();
      if (now - this.lastEmitTime < 16) return;
      this.lastEmitTime = now;
      const x = event.clientX;
      const y = event.clientY;
      for (let i = 0; i < 2; i++) {
        this.particles.push({
          x,
          y,
          vx: (Math.random() - 0.5) * 0.6,
          vy: (Math.random() - 0.8) * 0.6,
          radius: 2 + Math.random() * 4,
          life: 0,
          maxLife: 40 + Math.random() * 20,
        });
      }
      if (this.particles.length > 180) {
        this.particles.splice(0, this.particles.length - 180);
      }
    },
    animate() {
      const ctx = this.ctx;
      const width = this.canvas.width / this.dpr;
      const height = this.canvas.height / this.dpr;
      ctx.clearRect(0, 0, width, height);
      ctx.globalCompositeOperation = 'lighter';
      this.particles.forEach(p => {
        p.x += p.vx;
        p.y += p.vy;
        p.life++;
        const t = p.life / p.maxLife;
        const alpha = 1 - t;
        if (alpha <= 0) return;
        const radius = p.radius * (1 - t * 0.4);
        const gradient = ctx.createRadialGradient(
          p.x,
          p.y,
          0,
          p.x,
          p.y,
          radius
        );
        gradient.addColorStop(0, `rgba(255, 182, 193, ${alpha})`);
        gradient.addColorStop(0.5, `rgba(255, 105, 180, ${alpha * 0.9})`);
        gradient.addColorStop(1, `rgba(255, 105, 180, 0)`);
        ctx.beginPath();
        ctx.fillStyle = gradient;
        ctx.arc(p.x, p.y, radius, 0, Math.PI * 2);
        ctx.fill();
      });
      this.particles = this.particles.filter(p => p.life < p.maxLife);
      ctx.globalCompositeOperation = 'source-over';
      this.rafId = requestAnimationFrame(this.animate);
    },
  },
};
</script>

<style scoped>
.cursor-trail {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1200;
}
canvas {
  width: 100%;
  height: 100%;
  display: block;
}
</style>

