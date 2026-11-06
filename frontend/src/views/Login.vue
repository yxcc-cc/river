<template>
  <div class="login-page">
    <div class="page-card">
      <!-- ===== 左侧：登录 ===== -->
      <div class="left-panel">
        <!-- 浮动粒子 -->
        <div class="particle-group">
          <i class="particle p1"></i><i class="particle p2"></i><i class="particle p3"></i>
          <i class="particle p4"></i><i class="particle p5"></i><i class="particle p6"></i>
          <i class="particle p7"></i><i class="particle p8"></i>
        </div>
        <!-- 装饰线条 -->
        <div class="deco-lines">
          <span class="line l1"></span><span class="line l2"></span><span class="line l3"></span>
        </div>

        <div class="form-area">
          <!-- Logo居中 -->
          <div class="logo-area">
            <div class="logo-icon-wrap">
              <div class="logo-glow"></div>
              <div class="logo-icon">
                <svg viewBox="0 0 48 48" width="56" height="56" fill="none">
                  <path d="M24 4L6 12v12c0 11 8 18 18 22 10-4 18-11 18-22V12L24 4z" fill="#1a6adb" stroke="#1a6adb" stroke-width="2" stroke-linejoin="round"/>
                  <path d="M14 24c2-2 4-2 6 0s4 2 6 0 4-2 6 0" stroke="#fff" stroke-width="2" stroke-linecap="round" fill="none"/>
                  <path d="M14 29c2-2 4-2 6 0s4 2 6 0 4-2 6 0" stroke="#fff" stroke-width="1.5" stroke-linecap="round" fill="none" opacity="0.6"/>
                  <circle cx="24" cy="18" r="3" fill="#fff"/>
                </svg>
              </div>
            </div>
            <h2 class="logo-title">河边安全智能预警系统</h2>
            <p class="logo-sub">Riverbank Safety Early Warning System</p>
          </div>

          <!-- 表单 -->
          <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" size="large" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item prop="captcha">
              <div class="captcha-row">
                <el-input v-model="form.captcha" placeholder="请输入验证码" size="large" class="captcha-input">
                  <template #prefix><el-icon><Key /></el-icon></template>
                </el-input>
                <canvas ref="captchaCanvas" class="captcha-canvas" width="120" height="40" @click="refreshCaptcha"></canvas>
              </div>
            </el-form-item>
            <div class="form-options">
              <el-checkbox v-model="remember">记住密码</el-checkbox>
            </div>
            <el-form-item>
              <el-button type="primary" :loading="loading" size="large" class="login-btn" @click="handleLogin">
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- ===== 右侧：轮播展示 ===== -->
      <div class="right-panel" :class="slides[currentSlide].theme">
        <!-- 几何装饰 -->
        <div class="geo-group">
          <div class="dots-matrix"></div>
          <div class="geo arc arc-1"></div>
          <div class="geo arc arc-2"></div>
          <div class="geo arc arc-3"></div>
          <div class="geo tri tri-1"></div>
          <div class="geo tri tri-2"></div>
          <div class="geo dot dot-1"></div>
          <div class="geo dot dot-2"></div>
          <div class="geo dot dot-3"></div>
          <div class="geo dot dot-4"></div>
          <div class="geo ring ring-1"></div>
          <div class="geo ring ring-2"></div>
        </div>

        <!-- 笔记本 -->
        <div class="laptop-box">
          <div class="laptop" :class="'flip-' + currentSlide">
            <div class="screen">
              <div class="screen-inner">
                <transition :name="screenTransition" mode="out-in">
                  <div v-if="currentSlide === 0" class="mock-page" key="p0">
                    <div class="mock-topbar">
                      <span class="dot-r"></span><span class="dot-y"></span><span class="dot-g"></span>
                      <span class="mock-title">实时监控 · AI检测</span>
                    </div>
                    <div class="mock-body">
                      <div class="mock-side">
                        <div class="m-menu on"></div><div class="m-menu"></div><div class="m-menu"></div><div class="m-menu"></div>
                      </div>
                      <div class="mock-main">
                        <div class="m-cam-grid">
                          <div class="m-cam active"><div class="m-cam-label">DEV-001 甘棠湖</div><div class="m-cam-wave"></div></div>
                          <div class="m-cam"><div class="m-cam-label">DEV-006 USB</div><div class="m-cam-wave"></div></div>
                        </div>
                        <div class="m-detect-bar">
                          <span class="m-detect-dot"></span>
                          <span class="m-detect-txt">YOLOv8 检测中 · 置信度 0.5 · 3帧确认</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else-if="currentSlide === 1" class="mock-page" key="p1">
                    <div class="mock-topbar">
                      <span class="dot-r"></span><span class="dot-y"></span><span class="dot-g"></span>
                      <span class="mock-title">水位监测 · 四级预警</span>
                    </div>
                    <div class="mock-body">
                      <div class="mock-side">
                        <div class="m-menu"></div><div class="m-menu on"></div><div class="m-menu"></div><div class="m-menu"></div>
                      </div>
                      <div class="mock-main">
                        <div class="m-cards">
                          <div class="m-card"><span class="m-val">6</span><span class="m-lbl">监测站</span></div>
                          <div class="m-card"><span class="m-val">128cm</span><span class="m-lbl">当前水位</span></div>
                          <div class="m-card warn-card"><span class="m-val">注意</span><span class="m-lbl">预警等级</span></div>
                        </div>
                        <div class="m-chart">
                          <svg viewBox="0 0 200 40" preserveAspectRatio="none">
                            <path d="M0,30 Q25,20 50,22 T100,15 T150,18 T200,10" stroke="#4a9eff" stroke-width="2" fill="none" opacity="0.7"/>
                            <line x1="0" y1="12" x2="200" y2="12" stroke="#f56c6c" stroke-width="1" stroke-dasharray="4" opacity="0.5"/>
                          </svg>
                        </div>
                        <div class="m-alerts">
                          <div class="m-alert warn"><span class="m-icon">⚠</span><span class="m-atxt">水位上涨 - 甘棠湖 128cm</span></div>
                          <div class="m-alert danger"><span class="m-icon">🚨</span><span class="m-atxt">超警戒线 - 八里湖 156cm</span></div>
                          <div class="m-alert safe"><span class="m-icon">✓</span><span class="m-atxt">正常 - 南湖公园 82cm</span></div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-else class="mock-page" key="p2">
                    <div class="mock-topbar">
                      <span class="dot-r"></span><span class="dot-y"></span><span class="dot-g"></span>
                      <span class="mock-title">GIS地图 · 设备总览</span>
                    </div>
                    <div class="mock-body">
                      <div class="mock-side">
                        <div class="m-menu"></div><div class="m-menu"></div><div class="m-menu on"></div><div class="m-menu"></div>
                      </div>
                      <div class="mock-main">
                        <div class="m-map-area">
                          <div class="m-map-grid"></div>
                          <div class="m-pin p1"><span class="m-pin-tip">甘棠湖</span></div>
                          <div class="m-pin p2"><span class="m-pin-tip">八里湖</span></div>
                          <div class="m-pin p3"><span class="m-pin-tip">南湖</span></div>
                          <div class="m-pin p4"><span class="m-pin-tip">浔阳楼</span></div>
                          <div class="m-pin p5"><span class="m-pin-tip">烟水亭</span></div>
                        </div>
                        <div class="m-map-legend">
                          <span class="m-legend-item"><i class="m-legend-dot online"></i>在线 5</span>
                          <span class="m-legend-item"><i class="m-legend-dot offline"></i>离线 1</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </transition>
              </div>
            </div>
            <div class="base"></div>
            <div class="shadow"></div>
          </div>
        </div>

        <!-- 文案 -->
        <transition name="text-slide" mode="out-in">
          <div class="right-text" :key="currentSlide">
            <h3>{{ slides[currentSlide].title }}</h3>
            <p>{{ slides[currentSlide].desc }}</p>
          </div>
        </transition>

        <!-- 轮播指示器 -->
        <div class="dots">
          <i v-for="(s, i) in slides" :key="i" :class="['dot', { active: currentSlide === i }]" @click="goToSlide(i)"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'
import { useUserStore } from '../store'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const remember = ref(false)

const formRef = ref()
const captchaCanvas = ref()
const captchaCode = ref('')
const form = reactive({ username: '', password: '', captcha: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 生成验证码
const generateCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let code = ''
  for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)]
  captchaCode.value = code
  return code
}

const drawCaptcha = () => {
  const canvas = captchaCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height
  const code = generateCaptcha()

  // 背景 - 半透明
  ctx.clearRect(0, 0, w, h)
  ctx.fillStyle = 'rgba(240, 242, 245, 0.5)'
  ctx.fillRect(0, 0, w, h)

  // 干扰线
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = `rgba(${Math.random()*150+50},${Math.random()*150+50},${Math.random()*150+50},0.3)`
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(Math.random() * w, Math.random() * h)
    ctx.lineTo(Math.random() * w, Math.random() * h)
    ctx.stroke()
  }

  // 干扰点
  for (let i = 0; i < 30; i++) {
    ctx.fillStyle = `rgba(${Math.random()*200},${Math.random()*200},${Math.random()*200},0.4)`
    ctx.fillRect(Math.random() * w, Math.random() * h, 1.5, 1.5)
  }

  // 文字
  const colors = ['#1a6adb', '#e02020', '#333', '#e6a23c', '#67c23a']
  for (let i = 0; i < 4; i++) {
    ctx.save()
    ctx.font = `${Math.random()*4+20}px 'Courier New', monospace`
    ctx.fillStyle = colors[Math.floor(Math.random() * colors.length)]
    ctx.translate(28 + i * 24, 28 + Math.random()*6 - 3)
    ctx.rotate((Math.random() - 0.5) * 0.4)
    ctx.fillText(code[i], 0, 0)
    ctx.restore()
  }
}

const refreshCaptcha = () => drawCaptcha()

const slides = [
  { theme: 'theme-blue', title: '多路视频实时监控', desc: 'ESP32-CAM + USB摄像头多路接入、YOLOv8 AI入侵检测、自动截图预警' },
  { theme: 'theme-cyan', title: '水位数据智能预警', desc: '多站点水位实时采集、四级预警等级推送、历史趋势分析' },
  { theme: 'theme-navy', title: 'GIS地图总览大屏', desc: '高德地图设备标注、实时状态可视化、数据大屏展示' }
]

const currentSlide = ref(0)
const screenTransition = ref('screen-flip')
const sweepKey = ref(0)              // 每次切换 +1，重放扫光
let slideTimer = null

// 切换时先触发扫光，再换主题
const switchSlide = (next) => {
  sweepKey.value++
  currentSlide.value = next
}

const startAutoSlide = () => {
  slideTimer = setInterval(() => {
    const next = (currentSlide.value + 1) % slides.length
    screenTransition.value = 'screen-flip'
    switchSlide(next)
  }, 5000)
}
const goToSlide = (i) => {
  if (i === currentSlide.value) return
  screenTransition.value = i > currentSlide.value ? 'screen-flip' : 'screen-flip-back'
  switchSlide(i)
}
onMounted(() => { startAutoSlide(); setTimeout(drawCaptcha, 100) })
onUnmounted(() => clearInterval(slideTimer))

const handleLogin = async () => {
  await formRef.value.validate()
  // 验证码校验（忽略大小写）
  if (form.captcha.toLowerCase() !== captchaCode.value.toLowerCase()) {
    ElMessage.error('验证码错误')
    refreshCaptcha()
    form.captcha = ''
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    userStore.setLogin(res.data)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) { refreshCaptcha() } finally { loading.value = false }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@300;400;500;700&display=swap');

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  font-family: 'Noto Sans SC', sans-serif;
  padding: 24px;
}

.page-card {
  display: flex;
  width: 1060px;
  max-width: 100%;
  height: 600px;
  border-radius: 24px;
  overflow: hidden;
  background: transparent;
  box-shadow: 0 2px 4px rgba(0,0,0,0.02), 0 8px 24px rgba(0,0,0,0.06), 0 24px 48px rgba(0,0,0,0.04);
}

/* ===== 左侧 ===== */
.left-panel {
  width: 50%;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 56px;
  position: relative;
  overflow: hidden;
}

/* 浮动粒子 */
.particle-group { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.particle {
  position: absolute;
  border-radius: 50%;
  display: block;
  opacity: 0;
  animation: particleFloat linear infinite;
}
.p1 { width: 6px; height: 6px; background: rgba(56,97,251,0.2); top: 80%; left: 10%; animation-duration: 12s; animation-delay: 0s; }
.p2 { width: 4px; height: 4px; background: rgba(139,92,246,0.18); top: 90%; left: 30%; animation-duration: 15s; animation-delay: 2s; }
.p3 { width: 8px; height: 8px; background: rgba(56,97,251,0.12); top: 85%; left: 55%; animation-duration: 18s; animation-delay: 1s; }
.p4 { width: 3px; height: 3px; background: rgba(139,92,246,0.22); top: 95%; left: 75%; animation-duration: 10s; animation-delay: 4s; }
.p5 { width: 5px; height: 5px; background: rgba(56,97,251,0.15); top: 88%; left: 90%; animation-duration: 14s; animation-delay: 3s; }
.p6 { width: 7px; height: 7px; background: rgba(139,92,246,0.1); top: 92%; left: 45%; animation-duration: 16s; animation-delay: 5s; }
.p7 { width: 4px; height: 4px; background: rgba(56,97,251,0.18); top: 82%; left: 65%; animation-duration: 11s; animation-delay: 1.5s; }
.p8 { width: 5px; height: 5px; background: rgba(139,92,246,0.14); top: 87%; left: 20%; animation-duration: 13s; animation-delay: 3.5s; }

@keyframes particleFloat {
  0%   { transform: translateY(0) translateX(0) scale(0); opacity: 0; }
  10%  { opacity: 0.6; transform: translateY(-40px) translateX(10px) scale(1); }
  90%  { opacity: 0.3; }
  100% { transform: translateY(-600px) translateX(-30px) scale(0.5); opacity: 0; }
}

/* 装饰线条 */
.deco-lines { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.line { position: absolute; display: block; height: 1px; background: linear-gradient(90deg, transparent, rgba(56,97,251,0.08), transparent); }
.l1 { width: 200px; top: 25%; left: -50px; transform: rotate(-15deg); animation: lineGlide 8s ease-in-out infinite; }
.l2 { width: 160px; top: 60%; right: -30px; transform: rotate(10deg); animation: lineGlide 10s ease-in-out infinite 2s; }
.l3 { width: 120px; top: 80%; left: 20%; transform: rotate(-8deg); animation: lineGlide 12s ease-in-out infinite 4s; }
@keyframes lineGlide {
  0%,100% { opacity: 0.3; transform: translateX(0) rotate(-15deg); }
  50%     { opacity: 0.8; transform: translateX(40px) rotate(-15deg); }
}

.form-area { width: 100%; max-width: 360px; position: relative; z-index: 1; }

/* Logo居中 */
.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 36px;
}

.logo-icon-wrap {
  position: relative;
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.logo-glow {
  position: absolute;
  inset: -8px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(26,106,219,0.15) 0%, transparent 70%);
  animation: glowPulse 3s ease-in-out infinite;
}

@keyframes glowPulse {
  0%,100% { transform: scale(1); opacity: 0.5; }
  50%     { transform: scale(1.15); opacity: 1; }
}

.logo-icon {
  width: 68px;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  background: rgba(255,255,255,0.4);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  box-shadow: 0 4px 20px rgba(26,106,219,0.12);
  position: relative;
  z-index: 1;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
  letter-spacing: 2px;
}

.logo-sub {
  font-size: 11px;
  color: #8892a4;
  margin: 4px 0 0;
  letter-spacing: 1px;
}

/* 输入框 */
.el-form :deep(.el-form-item) { margin-bottom: 20px; }
.el-input :deep(.el-input__wrapper) { border-radius: 10px; box-shadow: 0 0 0 1px rgba(0,0,0,0.08); padding: 4px 14px; height: 48px; transition: all 0.25s; background: rgba(255,255,255,0.55); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.el-input :deep(.el-input__wrapper:hover) { box-shadow: 0 0 0 1px rgba(0,0,0,0.14); }
.el-input :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 2px rgba(26,106,219,0.1); background: rgba(255,255,255,0.7); }
.el-input :deep(.el-input__prefix) { color: #999; }

/* 验证码 */
.captcha-row { display: flex; gap: 12px; width: 100%; }
.captcha-input { flex: 1; }
.captcha-canvas {
  width: 120px;
  height: 40px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid rgba(0,0,0,0.08);
  flex-shrink: 0;
  background: rgba(255,255,255,0.4);
}

/* 选项行 */
.form-options { display: flex; align-items: center; margin: -4px 0 20px; font-size: 13px; }
.form-options :deep(.el-checkbox__label) { color: #999; font-size: 13px; }

/* 登录按钮 */
.login-btn {
  width: 100%; height: 50px; border-radius: 12px; font-size: 16px; font-weight: 600;
  letter-spacing: 6px; background: #1a6adb; border: none; margin-top: 4px;
  box-shadow: 0 4px 14px rgba(26,106,219,0.2); transition: all 0.25s; border-radius: 10px;
}
.login-btn:hover { background: #1558b8; box-shadow: 0 6px 20px rgba(26,106,219,0.3); transform: translateY(-1px); }

/* ===== 右侧 ===== */
.right-panel {
  width: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 40px 36px;
  transition: background 0.8s ease;
}

.theme-blue { background: linear-gradient(145deg, #0d2b52 0%, #1a4a8a 40%, #1a6adb 100%); }
.theme-cyan { background: linear-gradient(145deg, #0a3040 0%, #0e5a6e 40%, #17a2b8 100%); }
.theme-navy { background: linear-gradient(145deg, #1a1a30 0%, #2a3a5a 40%, #3a5a8a 100%); }

.theme-blue .tri-1 { border-bottom-color: rgba(255,160,120,0.2); }
.theme-blue .dot-1 { background: rgba(255,160,120,0.3); }
.theme-cyan .tri-1 { border-bottom-color: rgba(140,255,240,0.18); }
.theme-cyan .dot-1 { background: rgba(140,255,240,0.25); }
.theme-navy .tri-1 { border-bottom-color: rgba(255,180,100,0.2); }
.theme-navy .dot-1 { background: rgba(255,180,100,0.3); }

/* 几何 */
.geo-group { position: absolute; inset: 0; pointer-events: none; }
.geo { position: absolute; }
.arc { border-radius: 50%; border-style: solid; border-color: transparent; }
.arc-1 { width: 100px; height: 100px; border-width: 3px; border-top-color: rgba(255,255,255,0.08); border-right-color: rgba(255,255,255,0.04); top: 10%; left: 8%; animation: spin 20s linear infinite, drift1 14s ease-in-out infinite; }
.arc-2 { width: 60px; height: 60px; border-width: 2px; border-bottom-color: rgba(255,255,255,0.06); bottom: 20%; right: 12%; animation: spin 16s linear infinite reverse, drift2 12s ease-in-out infinite; }
.arc-3 { width: 40px; height: 40px; border-width: 2px; border-top-color: rgba(255,255,255,0.05); top: 55%; left: 5%; animation: spin 12s linear infinite, drift3 10s ease-in-out infinite; }

.tri { width: 0; height: 0; border-left: solid transparent; border-right: solid transparent; border-bottom: solid; }
.tri-1 { border-left-width: 22px; border-right-width: 22px; border-bottom-width: 38px; top: 18%; right: 20%; animation: drift4 16s ease-in-out infinite, spin 24s linear infinite; transition: border-bottom-color 0.8s; }
.tri-2 { border-left-width: 14px; border-right-width: 14px; border-bottom-width: 24px; border-bottom-color: rgba(255,255,255,0.08); bottom: 30%; left: 18%; animation: drift5 14s ease-in-out infinite; }

.dot-1 { width: 14px; height: 14px; border-radius: 50%; top: 28%; left: 22%; animation: drift2 10s ease-in-out infinite; transition: background 0.8s; }
.dot-2 { width: 8px; height: 8px; border-radius: 50%; background: rgba(255,255,255,0.12); top: 45%; right: 25%; animation: drift3 8s ease-in-out infinite; }
.dot-3 { width: 10px; height: 10px; border-radius: 50%; background: rgba(255,255,255,0.1); bottom: 15%; left: 30%; animation: drift4 9s ease-in-out infinite; }
.dot-4 { width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.08); top: 80%; right: 15%; animation: drift5 7s ease-in-out infinite; }

.ring-1 { width: 50px; height: 50px; border-radius: 50%; border: 2px solid rgba(255,255,255,0.05); bottom: 10%; right: 30%; animation: spin 18s linear infinite, drift1 12s ease-in-out infinite; }
.ring-2 { width: 30px; height: 30px; border-radius: 50%; border: 2px solid rgba(255,255,255,0.06); top: 40%; left: 12%; animation: spin 14s linear infinite reverse, drift3 10s ease-in-out infinite; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes drift1 { 0%,100%{transform:translate(0,0)} 25%{transform:translate(10px,-14px)} 50%{transform:translate(-6px,8px)} 75%{transform:translate(12px,6px)} }
@keyframes drift2 { 0%,100%{transform:translate(0,0)} 33%{transform:translate(-12px,10px)} 66%{transform:translate(8px,-6px)} }
@keyframes drift3 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(8px,-12px)} }
@keyframes drift4 { 0%,100%{transform:translate(0,0)} 25%{transform:translate(6px,10px)} 50%{transform:translate(-10px,-6px)} 75%{transform:translate(4px,12px)} }
@keyframes drift5 { 0%,100%{transform:translate(0,0)} 50%{transform:translate(-8px,8px)} }

/* ===== 笔记本 ===== */
.laptop-box { position: relative; z-index: 1; width: 340px; height: 280px; display: flex; align-items: center; justify-content: center; }

.laptop {
  display: flex;
  flex-direction: column;
  align-items: center;
  transform-style: preserve-3d;
  transform: perspective(800px) rotateX(3deg) rotateY(-2deg);
}

.screen { width: 320px; background: #2a2a3a; border-radius: 10px 10px 0 0; padding: 6px; border: 2px solid #3a3a4a; border-bottom: none; backface-visibility: hidden; }
.screen-inner { background: #0f1923; border-radius: 6px; overflow: hidden; perspective: 400px; }

.mock-page { min-height: 160px; }
.mock-topbar { display: flex; align-items: center; gap: 5px; padding: 6px 10px; background: #16213e; border-bottom: 1px solid #1a2744; }
.dot-r,.dot-y,.dot-g { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.dot-r { background: #ff5f57; } .dot-y { background: #ffbd2e; } .dot-g { background: #28c840; }
.mock-title { font-size: 8px; color: rgba(255,255,255,0.35); margin-left: 6px; }

.mock-body { display: flex; min-height: 140px; }
.mock-side { width: 40px; background: #0d1520; padding: 8px 5px; display: flex; flex-direction: column; gap: 5px; }
.m-menu { height: 5px; border-radius: 3px; background: rgba(255,255,255,0.06); }
.m-menu.on { background: rgba(26,106,219,0.5); }
.mock-main { flex: 1; padding: 8px; }

.m-cards { display: flex; gap: 5px; margin-bottom: 8px; }
.m-card { flex: 1; padding: 5px 4px; border-radius: 4px; text-align: center; background: rgba(255,255,255,0.03); border-left: 2px solid #4a9eff; }
.m-val { display: block; font-size: 11px; font-weight: 700; color: #fff; }
.m-lbl { font-size: 7px; color: rgba(255,255,255,0.25); }
.m-chart { height: 36px; margin-bottom: 6px; }
.m-chart svg { width: 100%; height: 100%; }
.m-rows { display: flex; flex-direction: column; gap: 4px; }
.m-row { height: 4px; border-radius: 2px; background: rgba(255,255,255,0.04); }
.m-row:nth-child(1) { width: 90%; } .m-row:nth-child(2) { width: 70%; } .m-row:nth-child(3) { width: 80%; }

.m-alerts { display: flex; flex-direction: column; gap: 6px; padding: 4px 0; }
.m-alert { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; background: rgba(255,255,255,0.03); }
.m-alert.warn { border-left: 2px solid #e6a23c; }
.m-alert.danger { border-left: 2px solid #f56c6c; }
.m-alert.safe { border-left: 2px solid #67c23a; }
.m-icon { font-size: 10px; }
.m-atxt { font-size: 9px; color: rgba(255,255,255,0.6); }

/* 监控页 mock */
.m-cam-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-bottom: 6px; }
.m-cam { background: rgba(0,0,0,0.4); border-radius: 4px; height: 60px; position: relative; overflow: hidden; border: 1px solid rgba(255,255,255,0.05); }
.m-cam.active { border-color: rgba(74,158,255,0.4); }
.m-cam-label { font-size: 7px; color: rgba(255,255,255,0.4); padding: 3px 5px; position: absolute; top: 0; left: 0; z-index: 1; }
.m-cam-wave { position: absolute; bottom: 0; left: 0; right: 0; height: 20px; background: linear-gradient(180deg, transparent, rgba(74,158,255,0.08)); }
.m-cam.active .m-cam-wave { background: linear-gradient(180deg, transparent, rgba(74,158,255,0.15)); animation: camWave 2s ease-in-out infinite; }
@keyframes camWave { 0%,100%{height:15px} 50%{height:25px} }

.m-detect-bar { display: flex; align-items: center; gap: 5px; padding: 4px 8px; background: rgba(103,194,58,0.08); border-radius: 4px; border-left: 2px solid #67c23a; }
.m-detect-dot { width: 5px; height: 5px; border-radius: 50%; background: #67c23a; animation: pulse 1.5s infinite; }
.m-detect-txt { font-size: 7px; color: rgba(255,255,255,0.45); }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }

/* 地图页 mock */
.m-map-area { height: 80px; background: rgba(255,255,255,0.02); border-radius: 4px; position: relative; overflow: hidden; margin-bottom: 6px; }
.m-map-grid { position: absolute; inset: 0; background-image: linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px); background-size: 20px 20px; }
.m-pin { width: 8px; height: 8px; border-radius: 50%; position: absolute; }
.m-pin::after { content: ''; position: absolute; top: -3px; left: -3px; width: 14px; height: 14px; border-radius: 50%; border: 1px solid; opacity: 0.3; animation: pinPulse 2s infinite; }
.m-pin.p1 { background: #67c23a; top: 30%; left: 20%; } .m-pin.p1::after { border-color: #67c23a; }
.m-pin.p2 { background: #f56c6c; top: 45%; left: 50%; } .m-pin.p2::after { border-color: #f56c6c; }
.m-pin.p3 { background: #67c23a; top: 55%; left: 35%; } .m-pin.p3::after { border-color: #67c23a; }
.m-pin.p4 { background: #67c23a; top: 25%; left: 70%; } .m-pin.p4::after { border-color: #67c23a; }
.m-pin.p5 { background: #409eff; top: 65%; left: 75%; } .m-pin.p5::after { border-color: #409eff; }
@keyframes pinPulse { 0%{transform:scale(1);opacity:0.3} 50%{transform:scale(1.8);opacity:0} 100%{transform:scale(1);opacity:0.3} }

.m-pin-tip { position: absolute; bottom: 100%; left: 50%; transform: translateX(-50%); font-size: 6px; color: rgba(255,255,255,0.5); white-space: nowrap; padding: 1px 3px; background: rgba(0,0,0,0.3); border-radius: 2px; margin-bottom: 2px; opacity: 0; transition: opacity 0.2s; }
.m-pin:hover .m-pin-tip { opacity: 1; }

.m-map-legend { display: flex; gap: 10px; justify-content: center; }
.m-legend-item { display: flex; align-items: center; gap: 4px; font-size: 7px; color: rgba(255,255,255,0.4); }
.m-legend-dot { width: 5px; height: 5px; border-radius: 50%; display: inline-block; }
.m-legend-dot.online { background: #67c23a; }
.m-legend-dot.offline { background: #909399; }

/* 预警卡片 */
.m-cards .warn-card { border-left-color: #e6a23c; }
.m-cards .warn-card .m-val { color: #e6a23c; }

.base { width: 360px; height: 10px; background: linear-gradient(180deg, #444, #333); border-radius: 0 0 6px 6px; }
.base::before { content: ''; display: block; width: 50px; height: 3px; background: #555; border-radius: 0 0 3px 3px; margin: 0 auto; }
.shadow { width: 300px; height: 8px; background: radial-gradient(ellipse, rgba(0,0,0,0.15), transparent); margin-top: 4px; border-radius: 50%; }

/* ===== 屏幕3D翻页 ===== */
.screen-flip-enter-active {
  animation: flipIn 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}
.screen-flip-leave-active {
  animation: flipOut 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.screen-flip-back-enter-active {
  animation: flipIn 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}
.screen-flip-back-leave-active {
  animation: flipOut 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes flipOut {
  0%   { transform: perspective(400px) rotateY(0); opacity: 1; }
  100% { transform: perspective(400px) rotateY(-90deg); opacity: 0; }
}
@keyframes flipIn {
  0%   { transform: perspective(400px) rotateY(90deg); opacity: 0; }
  100% { transform: perspective(400px) rotateY(0); opacity: 1; }
}

/* 笔记本翻页时微弹 */
.laptop.flip-0 { animation: laptopBounce 0.6s ease-out; }
.laptop.flip-1 { animation: laptopBounce 0.6s ease-out; }
.laptop.flip-2 { animation: laptopBounce 0.6s ease-out; }
@keyframes laptopBounce {
  0%   { transform: perspective(800px) rotateX(3deg) rotateY(-2deg) translateY(0) scale(1); }
  30%  { transform: perspective(800px) rotateX(5deg) rotateY(-4deg) translateY(-8px) scale(1.02); }
  60%  { transform: perspective(800px) rotateX(1deg) rotateY(0deg) translateY(-2px) scale(0.99); }
  100% { transform: perspective(800px) rotateX(3deg) rotateY(-2deg) translateY(0) scale(1); }
}

/* 文案滑入 */
.text-slide-enter-active {
  animation: textSlideIn 0.5s cubic-bezier(0.4, 0, 0.2, 1) 0.15s both;
}
.text-slide-leave-active {
  animation: textSlideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1) both;
}
@keyframes textSlideIn {
  0%   { transform: translateY(16px); opacity: 0; filter: blur(4px); }
  100% { transform: translateY(0); opacity: 1; filter: blur(0); }
}
@keyframes textSlideOut {
  0%   { transform: translateY(0); opacity: 1; filter: blur(0); }
  100% { transform: translateY(-12px); opacity: 0; filter: blur(4px); }
}

.right-text { text-align: center; margin-top: 20px; position: relative; z-index: 1; }
.right-text h3 { color: #fff; font-size: 18px; font-weight: 700; margin: 0 0 6px; letter-spacing: 1px; }
.right-text p { color: rgba(255,255,255,0.45); font-size: 12px; margin: 0; letter-spacing: 1px; line-height: 1.6; }

.dots { display: flex; gap: 8px; margin-top: 20px; position: relative; z-index: 1; }
.dots .dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(255,255,255,0.15); display: block; cursor: pointer; transition: all 0.4s cubic-bezier(0.4,0,0.2,1); }
.dots .dot:hover { background: rgba(255,255,255,0.3); }
.dots .dot.active { width: 28px; border-radius: 4px; background: rgba(255,255,255,0.6); box-shadow: 0 0 8px rgba(255,255,255,0.2); }

@media (max-width: 900px) {
  .page-card { flex-direction: column; height: auto; }
  .left-panel { width: 100%; padding: 40px 32px; }
  .right-panel { width: 100%; padding: 36px 24px; min-height: 400px; }
  .screen { width: 260px; }
  .base { width: 290px; }
  .laptop-box { width: 280px; height: 240px; }
}




/* ============================================================
   参照稿：左侧极简白 + 右侧「大面积撞色块」
   关键不是撒小图形，而是用大色块把画面切开（蒙德里安那种构成），
   小圆点阵和细圆环只做点缀。
   ============================================================ */

/* ---------- 左侧 ---------- */
.left-panel {
  background: #ffffff !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}
.particle-group,
.deco-lines,
.logo-glow { display: none !important; }

.logo-icon-wrap {
  background: #f2f3f5 !important;
  border-radius: 14px;
  width: 62px; height: 62px;
  display: flex; align-items: center; justify-content: center;
}
.logo-title { color: #0b0b10 !important; font-weight: 700 !important; font-size: 21px !important; letter-spacing: -0.2px; }
.logo-sub { color: #a0a4ae !important; font-size: 11.5px !important; letter-spacing: 0.6px; }

.left-panel :deep(.el-input__wrapper) {
  height: 50px !important;
  border-radius: 10px !important;
  background: #f5f6f8 !important;
  box-shadow: none !important;
  border: 1px solid transparent;
  transition: all 0.18s ease;
}
.left-panel :deep(.el-input__wrapper:hover) { background: #f0f1f4 !important; }
.left-panel :deep(.el-input__wrapper.is-focus) {
  background: #ffffff !important;
  border-color: #0b0b10 !important;
}
.left-panel :deep(.el-input__inner) { font-size: 14.5px; }

.login-btn {
  height: 50px !important;
  border-radius: 10px !important;
  background: #0b0b10 !important;
  border: none !important;
  box-shadow: none !important;
  font-weight: 600;
  letter-spacing: 4px !important;
  transition: all 0.2s ease !important;
}
.login-btn:hover { background: #23232d !important; transform: translateY(-1px); }

/* ---------- 右侧：三套撞色 ---------- */
.theme-blue { background: #1b3bc7 !important; }
.theme-cyan { background: #2dd4bf !important; }
.theme-navy { background: #6d28d9 !important; }

.geo-group { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }

/* —— 大色块 1：右上方的斜切块 —— */
.geo-group::before {
  content: '';
  position: absolute;
  top: 0; right: 0;
  width: 78%; height: 62%;
  clip-path: polygon(30% 0, 100% 0, 100% 100%, 0 42%);
  transition: background 0.8s ease;
  z-index: 0;
}

/* —— 大色块 2：左下方的斜切块 —— */
.geo-group::after {
  content: '';
  position: absolute;
  left: 0; bottom: 0;
  width: 66%; height: 52%;
  clip-path: polygon(0 0, 100% 46%, 100% 100%, 0 100%);
  transition: background 0.8s ease;
  z-index: 0;
}

/* 三套主题各自的色块颜色 */
.theme-blue .geo-group::before { background: #ff3d7f; }
.theme-blue .geo-group::after  { background: #0a1b5e; }

.theme-cyan .geo-group::before { background: #ef3b3b; }
.theme-cyan .geo-group::after  { background: #101014; }

.theme-navy .geo-group::before { background: #fb923c; }
.theme-navy .geo-group::after  { background: #3c1287; }

/* —— 实心大圆：压在色块交界处，制造层次 —— */
.arc-1 {
  position: absolute;
  width: 132px !important; height: 132px !important;
  border: none !important;
  border-radius: 50% !important;
  top: 54% !important; right: 12% !important;
  animation: floatY 9s ease-in-out infinite !important;
  z-index: 1;
  transition: background 0.8s ease;
}
.theme-blue .arc-1 { background: #ffffff; }
.theme-cyan .arc-1 { background: rgba(255, 255, 255, 0.9); }
.theme-navy .arc-1 { background: #ffffff; }

/* 半透明白圆，叠在色块上做层次 */
.arc-2 {
  position: absolute;
  width: 96px !important; height: 96px !important;
  border: 14px solid rgba(255, 255, 255, 0.35) !important;
  border-radius: 50% !important;
  bottom: 18% !important; left: 12% !important;
  animation: floatY 11s ease-in-out infinite reverse !important;
  z-index: 1;
}
.theme-cyan .arc-2 { border-color: rgba(16, 16, 20, 0.35) !important; }

/* 其余小元素降到很轻，只做点缀 */
.arc-3 { width: 26px !important; height: 26px !important; border-width: 5px !important; border-color: rgba(255,255,255,0.5) !important; top: 12% !important; left: 12% !important; }

.tri-1 {
  border-left-width: 34px !important; border-right-width: 34px !important;
  border-bottom-width: 58px !important;
  top: 22% !important; left: 20% !important;
  border-bottom-color: rgba(255, 255, 255, 0.75) !important;
  animation: none !important;
}
.tri-2 {
  border-left-width: 22px !important; border-right-width: 22px !important;
  border-bottom-width: 38px !important;
  bottom: 12% !important; right: 16% !important;
  border-bottom-color: rgba(255, 255, 255, 0.55) !important;
}

.dot-1 { width: 14px !important; height: 14px !important; background: #fff !important; top: 72% !important; left: 34% !important; }
.dot-2 { width: 8px !important; height: 8px !important; background: rgba(255,255,255,0.8) !important; top: 30% !important; right: 30% !important; }
.dot-3 { width: 9px !important; height: 9px !important; background: rgba(255,255,255,0.7) !important; bottom: 30% !important; left: 20% !important; }
.dot-4 { width: 6px !important; height: 6px !important; background: rgba(255,255,255,0.6) !important; top: 88% !important; right: 10% !important; }

.ring-1 { width: 44px !important; height: 44px !important; border: 6px solid rgba(255,255,255,0.3) !important; bottom: 8% !important; right: 30% !important; }
.ring-2 { width: 22px !important; height: 22px !important; border: 5px solid rgba(255,255,255,0.45) !important; top: 44% !important; left: 8% !important; }

/* —— 点阵矩阵：右上角，参照稿里最标志性的那个元素 —— */
.dots-matrix {
  position: absolute;
  top: 6%; right: 5%;
  width: 118px; height: 118px;
  background-image: radial-gradient(circle, rgba(255, 255, 255, 0.95) 2.4px, transparent 2.5px);
  background-size: 16px 16px;
  z-index: 2;
  pointer-events: none;
}
.theme-cyan .dots-matrix {
  background-image: radial-gradient(circle, rgba(16, 16, 20, 0.85) 2.4px, transparent 2.5px);
}

@keyframes floatY {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

/* 笔记本压在最上层，阴影加实 */
.laptop-box { z-index: 3; }
.laptop { filter: drop-shadow(0 18px 36px rgba(0, 0, 0, 0.35)); }

/* 底部文案 */
.right-text { z-index: 4; }
.right-text h3 { color: #fff !important; font-weight: 700; text-shadow: 0 2px 14px rgba(0,0,0,0.25); }
.right-text p { color: rgba(255,255,255,0.86) !important; }

.dots { z-index: 5; }
.dots .dot { background: rgba(255,255,255,0.4) !important; }
.dots .dot.active { background: #fff !important; width: 20px !important; border-radius: 3px; }

/* 外框 */
.login-page { background: #eceef2 !important; }
.page-card {
  border-radius: 16px !important;
  box-shadow: 0 1px 2px rgba(16,24,40,0.05), 0 24px 60px rgba(16,24,40,0.12) !important;
}


/* ============================================================
   真正的主题过渡
   之前失败的三个原因，这次全避开：
   1. 不能用 :key 重建元素 —— 一重建就没有 transition
   2. 不能在 scoped 里用 :root 定义变量 —— 会被编译成 :root[data-v-x]，不生效
   3. 不能用 background 简写 —— 要用 background-color 才参与过渡
   ============================================================ */

.right-panel {
  transition: background-color 1.15s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

/* 两个大色块：颜色和形变一起过渡 */
.geo-group::before,
.geo-group::after {
  will-change: transform, background-color;
  transition:
    background-color 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    transform 1.15s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.theme-blue .geo-group::before { background-color: #ff3d7f; transform: rotate(0deg) scale(1); }
.theme-blue .geo-group::after  { background-color: #0a1b5e; transform: rotate(0deg) scale(1); }

.theme-cyan .geo-group::before { background-color: #ef3b3b; transform: rotate(-7deg) scale(1.07); }
.theme-cyan .geo-group::after  { background-color: #101014; transform: rotate(5deg) scale(1.04); }

.theme-navy .geo-group::before { background-color: #fb923c; transform: rotate(6deg) scale(0.95); }
.theme-navy .geo-group::after  { background-color: #3c1287; transform: rotate(-4deg) scale(1.05); }

/* 几何元素：颜色和位置都过渡 */
.arc-1, .arc-2, .tri-1, .tri-2, .dots-matrix {
  transition:
    background-color 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    border-color 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    top 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    left 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    right 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    bottom 1.15s cubic-bezier(0.4, 0, 0.2, 1),
    transform 1.15s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

/* 每屏的构图不同，切换时图形会滑动 */
.theme-blue .arc-1 { top: 54% !important; right: 12% !important; }
.theme-cyan .arc-1 { top: 18% !important; right: 32% !important; background-color: rgba(255,255,255,0.92) !important; }
.theme-navy .arc-1 { top: 60% !important; right: 20% !important; background-color: #ffffff !important; }

.theme-blue .arc-2 { bottom: 18% !important; left: 12% !important; }
.theme-cyan .arc-2 { bottom: 42% !important; left: 30% !important; }
.theme-navy .arc-2 { bottom: 12% !important; left: 8% !important; }

.theme-blue .tri-1 { top: 22% !important; left: 20% !important; }
.theme-cyan .tri-1 { top: 42% !important; left: 42% !important; }
.theme-navy .tri-1 { top: 14% !important; left: 10% !important; }

.theme-blue .dots-matrix { transform: rotate(0deg); }
.theme-cyan .dots-matrix { transform: rotate(12deg); }
.theme-navy .dots-matrix { transform: rotate(-9deg); }

/* 底部文案跟着淡入 */
.right-text h3,
.right-text p {
  animation: textRise 0.7s cubic-bezier(0.4, 0, 0.2, 1) both;
}
@keyframes textRise {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* ============================================================
   左侧表单：改参考稿那种排版
   左对齐、大标题、下划线输入框，留白撑开
   ============================================================ */

.form-area {
  max-width: 370px !important;
  text-align: left;
}

/* Logo 区左对齐、去掉居中留白 */
.logo-area {
  align-items: flex-start !important;
  margin-bottom: 42px !important;
  gap: 0;
}
.logo-icon-wrap {
  width: 44px !important;
  height: 44px !important;
  margin-bottom: 22px !important;
  border-radius: 12px !important;
  background: #0b0b10 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}
.logo-icon-wrap svg {
  width: 26px !important;
  height: 26px !important;
}
/* logo 里那个盾牌换成白线，配深底更干净 */
.logo-icon-wrap svg path { fill: none !important; stroke: #ffffff !important; }
.logo-icon-wrap svg circle { fill: #ffffff !important; }

.logo-title {
  font-size: 30px !important;
  line-height: 1.22 !important;
  font-weight: 800 !important;
  letter-spacing: -0.6px !important;
  color: #0b0b10 !important;
  text-align: left !important;
  margin: 0 0 10px 0 !important;
}
.logo-sub {
  font-size: 12.5px !important;
  color: #a3a7b0 !important;
  letter-spacing: 0.3px !important;
  text-align: left !important;
  margin: 0 !important;
}

/* 下划线输入框：去掉灰底和圆角 */
.left-panel :deep(.el-form-item) {
  margin-bottom: 26px !important;
}
.left-panel :deep(.el-input__wrapper) {
  background: transparent !important;
  border: none !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  border-bottom: 1.5px solid #dcdfe5 !important;
  padding: 4px 2px 10px 2px !important;
  transition: border-color 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}
.left-panel :deep(.el-input__wrapper:hover) {
  background: transparent !important;
  border-bottom-color: #b9bec7 !important;
}
.left-panel :deep(.el-input__wrapper.is-focus) {
  background: transparent !important;
  border-bottom-color: #0b0b10 !important;
  box-shadow: none !important;
}
.left-panel :deep(.el-input__inner) {
  font-size: 15px !important;
  color: #0b0b10 !important;
}
.left-panel :deep(.el-input__inner::placeholder) {
  color: #b6bac2 !important;
}
/* 前缀图标淡一点 */
.left-panel :deep(.el-input__prefix .el-icon) {
  color: #b6bac2 !important;
  margin-right: 4px;
}

/* 验证码画布：跟下划线输入框同高对齐 */
.captcha-canvas {
  height: 46px !important;
  border-radius: 8px !important;
  border: 1px solid #e6e8ec !important;
  background: #fafbfc !important;
}
.captcha-row {
  align-items: flex-end !important;
}

/* 记住密码 */
.form-options {
  margin-bottom: 26px !important;
}
.form-options :deep(.el-checkbox__label) {
  font-size: 13px !important;
  color: #6b7280 !important;
}

/* 按钮：黑色全宽，字重加大 */
.login-btn {
  height: 52px !important;
  border-radius: 10px !important;
  background: #0b0b10 !important;
  font-size: 15px !important;
  font-weight: 700 !important;
  letter-spacing: 5px !important;
  border: none !important;
  box-shadow: none !important;
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1) !important;
}
.login-btn:hover {
  background: #23232d !important;
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(11, 11, 16, 0.18) !important;
}
.login-btn:active {
  transform: translateY(0);
}

/* 去掉左侧顶部的 logo 方块，让标题直接起头 */
.logo-icon-wrap {
  display: none !important;
}
/* 标题成为最上面的元素，上留白重新分配 */
.logo-area {
  margin-bottom: 44px !important;
  padding-top: 8px;
}
.logo-title {
  margin: 0 0 12px 0 !important;
}

/* 修复：全局按钮文字色是"绿底配近黑"，登录页是黑底，必须强制白字 */
.login-btn,
.login-btn:hover,
.login-btn:focus,
.login-btn:active {
  color: #ffffff !important;
}
.login-btn:hover {
  background: #23232d !important;
}
.login-btn:hover > span,
.login-btn > span {
  color: #ffffff !important;
}
</style>
