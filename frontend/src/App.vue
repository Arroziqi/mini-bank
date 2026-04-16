<script setup>
import { useAuthStore } from './stores/auth';
import { useRouter } from 'vue-router';

const auth = useAuthStore();
const router = useRouter();

const logout = () => {
  auth.logout();
  router.push('/login');
};
</script>

<template>
  <nav v-if="auth.isAuthenticated" class="navbar">
    <div class="nav-brand">MiniBank</div>
    <div class="nav-links">
      <router-link to="/dashboard">Dashboard</router-link>
      <router-link to="/transfer">Transfer</router-link>
      <router-link v-if="auth.isAdmin" to="/admin">Admin</router-link>
      <button @click="logout" class="btn-logout">Logout</button>
    </div>
  </nav>
  
  <main class="container">
    <router-view />
  </main>
</template>

<style scoped>
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 5%;
  background: var(--surface);
  border-bottom: 1px solid rgba(100, 255, 218, 0.1);
}

.nav-brand {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--accent);
}

.nav-links {
  display: flex;
  gap: 2rem;
  align-items: center;
}

.nav-links a {
  color: var(--text-secondary);
  text-decoration: none;
  font-weight: 500;
}

.nav-links a.router-link-active {
  color: var(--accent);
}

.btn-logout {
  background: transparent;
  border: 1px solid var(--error);
  color: var(--error);
  padding: 0.4rem 1rem;
  border-radius: 6px;
  cursor: pointer;
}

.container {
  padding: 2rem 5%;
  max-width: 1200px;
  margin: 0 auto;
}
</style>
