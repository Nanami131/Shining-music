<template>
  <div class="home-container">
    <h1>欢迎体验音乐社区</h1>
    <p>发现音乐，参与讨论，获取个性化推荐！</p>
  </div>
</template>

<script>
export default {
  name: 'ShiningHome',
};
</script>

<style scoped>
.home-container {
  text-align: center;
  padding: 40px 20px;
  min-height: calc(100vh - 60px); /* 减去顶部栏高度 */
  background: linear-gradient(to bottom, #e0f7fa, #ffffff);
}
h1 {
  color: #2c3e50;
  font-size: 32px;
  margin-bottom: 20px;
}
p {
  color: #555;
  font-size: 18px;
}
</style>