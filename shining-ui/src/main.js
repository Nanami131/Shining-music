import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import mitt from 'mitt';

const app = createApp(App);
app.config.devtools = false;
app.config.globalProperties.$bus = mitt();
app.use(router).mount('#app');