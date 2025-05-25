<template>
  <div class="shining-home">
    <h1>Welcome to Shining Music Community</h1>
    <p>Explore music, join discussions, and get personalized recommendations!</p>
    <button @click="testUserService">Test User Service</button>
    <p v-if="message">Response: {{ message }}</p>
    <p v-if="data">Data: {{ data }}</p>
  </div>
</template>

<script>
import api from '@/api/index.js';

export default {
  name: 'ShiningHome',
  data() {
    return {
      message: '',
      data: '',
    };
  },
  methods: {
    async testUserService() {
      try {
        const response = await api.testUserService();
        this.message = response.data.message;
        this.data = response.data.data;
      } catch (error) {
        console.error('Error:', error);
        this.message = 'Failed to connect to user service';
        this.data = '';
      }
    },
  },
};
</script>

<style scoped>
.shining-home {
  text-align: center;
  padding: 20px;
}
h1 {
  color: #2c3e50;
}
button {
  padding: 10px 20px;
  background-color: #42b983;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
}
button:hover {
  background-color: #3aa876;
}
</style>