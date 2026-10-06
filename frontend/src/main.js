import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import { initAMapApiLoader } from '@vuemap/vue-amap'
import VueAMap from '@vuemap/vue-amap'

import './style.css'
import App from './App.vue'
import router from './router'

// Initialize AMap
initAMapApiLoader({
  key: '9cfeb65d9e8b30a0042bbdf1b481127b',
  version: '2.0',
  plugins: ['AMap.Scale', 'AMap.ToolBar', 'AMap.Marker', 'AMap.InfoWindow', 'AMap.CircleMarker']
})

const app = createApp(App)

// Register all Element Plus icons
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { size: 'default' })
app.use(VueAMap)

app.mount('#app')
