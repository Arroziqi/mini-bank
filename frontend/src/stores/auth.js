import { defineStore } from 'pinia';
import api from '../api';

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: JSON.parse(localStorage.getItem('user')) || null,
        token: localStorage.getItem('token') || null,
    }),
    getters: {
        isAuthenticated: (state) => !!state.token,
        isAdmin: (state) => state.user?.role === 'ADMIN',
    },
    actions: {
        async login(username, password) {
            const resp = await api.post('/auth/login', { username, password });
            this.token = resp.data.token;
            this.user = { username: resp.data.username, role: resp.data.role };
            localStorage.setItem('token', this.token);
            localStorage.setItem('user', JSON.stringify(this.user));
        },
        async register(username, password, email) {
            await api.post('/auth/register', { username, password, email });
        },
        logout() {
            this.token = null;
            this.user = null;
            localStorage.removeItem('token');
            localStorage.removeItem('user');
        },
    },
});
