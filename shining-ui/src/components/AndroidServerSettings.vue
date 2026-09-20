<template>
  <div class="android-server-settings" :class="{ 'with-player': withPlayer }">
    <button class="server-launcher" type="button" @click="open">服务器</button>
    <div v-if="visible" class="server-backdrop">
      <form class="server-dialog" @submit.prevent="save">
        <h2>服务器地址</h2>
        <p>请填写能从手机访问的 Shining Music 服务地址。服务需要提供 /api/ 和 /minio/ 路径。</p>
        <label for="android-server-origin">服务地址</label>
        <input id="android-server-origin" v-model.trim="origin" type="url" required autocomplete="url" />
        <p v-if="error" class="server-error">{{ error }}</p>
        <div class="server-actions">
          <button type="button" @click="visible = false">取消</button>
          <button type="submit">保存并重新连接</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { getAndroidServer, setAndroidServer } from '@/utils/androidServer';

export default {
  name: 'AndroidServerSettings',
  props: { withPlayer: { type: Boolean, default: false } },
  data() {
    return { visible: false, origin: '', error: '' };
  },
  methods: {
    open() {
      this.origin = getAndroidServer();
      this.error = '';
      this.visible = true;
    },
    save() {
      if (!setAndroidServer(this.origin)) {
        this.error = '请输入以 http:// 或 https:// 开头的完整服务地址，不要附加路径。';
        return;
      }
      window.location.reload();
    },
  },
};
</script>

<style scoped>
.android-server-settings { position: fixed; bottom: 16px; right: 12px; z-index: 9999; }
.android-server-settings.with-player { bottom: 112px; }
.server-launcher { border: 1px solid #64748b; border-radius: 20px; background: #fff; color: #334155; padding: 5px 12px; font-size: 12px; }
.server-backdrop { position: fixed; inset: 0; background: #0009; display: flex; justify-content: center; align-items: center; padding: 20px; }
.server-dialog { background: #fff; border-radius: 12px; padding: 20px; width: min(100%, 420px); box-sizing: border-box; color: #1e293b; }
.server-dialog h2 { margin: 0 0 12px; font-size: 20px; }
.server-dialog p { font-size: 14px; line-height: 1.5; }
.server-dialog label { display: block; margin-bottom: 6px; }
.server-dialog input { box-sizing: border-box; width: 100%; padding: 10px; font-size: 14px; }
.server-error { color: #b91c1c; }
.server-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 18px; }
.server-actions button { padding: 9px; }
</style>
