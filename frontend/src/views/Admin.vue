<script setup>
import { onMounted, ref } from 'vue';
import api from '../api';

const users = ref([]);
const logs = ref([]);
const activeTab = ref('users');

onMounted(() => {
  fetchUsers();
  fetchLogs();
});

const fetchUsers = async () => {
  try {
    const resp = await api.get('/admin/users');
    users.value = resp.data.content;
  } catch (err) {
    console.error(err);
  }
};

const fetchLogs = async () => {
  try {
    const resp = await api.get('/admin/audit-logs');
    logs.value = resp.data.content;
  } catch (err) {
    console.error(err);
  }
};
</script>

<template>
  <div class="admin-portal">
    <header class="header">
      <h1>Admin Portal</h1>
    </header>

    <div class="tabs">
      <button :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">User Management</button>
      <button :class="{ active: activeTab === 'logs' }" @click="activeTab = 'logs'">Audit Logs</button>
    </div>

    <div v-if="activeTab === 'users'" class="card">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Username</th>
            <th>Email</th>
            <th>Role</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>{{ user.id }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.email }}</td>
            <td>{{ user.role }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="card">
      <table class="admin-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Action</th>
            <th>User</th>
            <th>Details</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ new Date(log.createdAt).toLocaleString() }}</td>
            <td><span class="action-tag">{{ log.action }}</span></td>
            <td>{{ log.user?.username || 'System' }}</td>
            <td class="details">{{ log.details }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.header { margin-bottom: 2rem; }
.tabs { margin-bottom: 1.5rem; display: flex; gap: 1rem; }
.tabs button {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-weight: 600;
  cursor: pointer;
  padding: 0.5rem 1rem;
  border-bottom: 2px solid transparent;
}
.tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.admin-table { width: 100%; border-collapse: collapse; text-align: left; }
.admin-table th { padding: 1rem; color: var(--text-secondary); border-bottom: 1px solid rgba(255,255,255,0.05); }
.admin-table td { padding: 1rem; border-bottom: 1px solid rgba(255,255,255,0.05); }

.action-tag { font-size: 0.7rem; background: var(--secondary); padding: 2px 6px; border-radius: 4px; }
.details { font-size: 0.85rem; color: var(--text-secondary); }
</style>
