<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>

<template>
  <div class="auth-page">
    <div class="auth-card panel">
      <div class="brand">
        <span class="logo-mark">AQG</span>
        <div class="brand-meta">
          <div class="brand-name">API Quality Guard</div>
          <div class="brand-sub">{{ subtitle }}</div>
        </div>
      </div>

      <h1 class="auth-title">{{ title }}</h1>
      <slot />

      <div class="auth-footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 18%, rgba(59, 130, 246, 0.16), transparent 42%),
    radial-gradient(circle at 82% 82%, rgba(139, 92, 246, 0.14), transparent 42%),
    var(--aqg-bg);
}

.auth-card {
  width: 400px;
  padding: 32px;
  box-shadow: 0 24px 60px rgba(2, 6, 23, 0.55);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
}

.brand-sub {
  font-size: 12px;
  color: var(--aqg-text-muted);
  margin-top: 2px;
}

.auth-title {
  margin: 28px 0 20px;
  font-size: 22px;
  font-weight: 700;
}

.auth-footer {
  margin-top: 18px;
  font-size: 13px;
  color: var(--aqg-text-secondary);
  text-align: center;
}

.auth-footer :deep(a) {
  color: var(--aqg-blue);
  text-decoration: none;
}
</style>
