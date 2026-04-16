<script setup>
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

const auth = useAuthStore();
const router = useRouter();

const username = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);

const handleLogin = async () => {
  loading.ref = true;
  error.value = '';
  try {
    await auth.login(username.value, password.value);
    router.push('/dashboard');
  } catch (err) {
    error.value = 'Invalid credentials';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="auth-wrapper">
    <div class="card auth-card">
      <h2>Welcome Back</h2>
      <p>Secure login to your account</p>
      
      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" class="input" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="password" type="password" class="input" required />
        </div>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Logging in...' : 'Sign In' }}
        </button>
      </form>
      <div class="auth-footer">
        Don't have an account? <router-link to="/register">Register</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 70vh;
}
.auth-card {
  width: 100%;
  max-width: 400px;
  text-align: center;
}
.auth-card h2 { color: var(--accent); margin-bottom: 0.5rem; }
.auth-card p { color: var(--text-secondary); margin-bottom: 2rem; }
.auth-form { text-align: left; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; margin-bottom: 0.5rem; font-size: 0.9rem; }
.error-msg { color: var(--error); margin-bottom: 1rem; font-size: 0.9rem; }
.auth-footer { margin-top: 1.5rem; font-size: 0.9rem; color: var(--text-secondary); }
.auth-footer a { color: var(--accent); text-decoration: none; }
</style>
