import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue'),
        meta: { guest: true },
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('../views/Register.vue'),
        meta: { guest: true },
    },
    {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { auth: true },
    },
    {
        path: '/transfer',
        name: 'Transfer',
        component: () => import('../views/Transfer.vue'),
        meta: { auth: true },
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        meta: { auth: true, admin: true },
    },
    {
        path: '/',
        redirect: '/dashboard',
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach((to, from, next) => {
    const auth = useAuthStore();

    if (to.meta.auth && !auth.isAuthenticated) {
        next('/login');
    } else if (to.meta.guest && auth.isAuthenticated) {
        next('/dashboard');
    } else if (to.meta.admin && !auth.isAdmin) {
        next('/dashboard');
    } else {
        next();
    }
});

export default router;
