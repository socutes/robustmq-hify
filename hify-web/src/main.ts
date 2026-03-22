import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/element-override.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(ElementPlus)
app.use(router)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
