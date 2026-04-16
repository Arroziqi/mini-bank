<script setup>
import { ref } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useRouter } from 'vue-router';

const auth = useAuthStore();
const router = useRouter();

const username = ref('');
const password = ref('');
const email = ref('');
const error = ref('');
const loading = ref(false);

const handleRegister = async () => {
  loading.value = true;
  error.value = '';
  try {
    await auth.register(username.value, password.value, email.value);
    alert('Registration successful! Please login.');
    router.push('/login');
  } catch (err) {
    error.value = err.response?.data || 'Registration failed';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="auth-wrapper">
    <div class="card auth-card">
      <h2>Create Account</h2>
      <p>Join MiniBank today</p>
      
      <form @submit.prevent="handleRegister" class="auth-form">
        <div class="form-group">
          <label>Username</label>
          <input v-model="username" type="text" class="input" required />
        </div>
        <div class="form-group">
          <label>Email</label>
          <input v-model="email" type="email" class="input" required />
        </div>
        <div class="form-group">
          <label>Password</label>
          <input v-model="password" type="password" class="input" required />
        </div>
        <div v-if="error" class="error-msg">{{ error }}</div>
        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Creating...' : 'Register' }}
        </button>
      </form>
      <div class="auth-footer">
        Already have an account? <router-link to="/login">Login</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Same as Login.vue */
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
