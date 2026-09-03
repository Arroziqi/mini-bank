import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import Login from '../Login.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/login', component: Login },
    { path: '/dashboard', component: { template: '<div />' } },
    { path: '/register', component: { template: '<div />' } },
  ],
});

describe('Login.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router.push('/');
  });

  it('renders login form elements', () => {
    const wrapper = mount(Login, {
      global: { plugins: [router] },
    });

    expect(wrapper.find('h2').text()).toBe('Welcome Back');
    expect(wrapper.find('input[type="text"]').exists()).toBe(true);
    expect(wrapper.find('input[type="password"]').exists()).toBe(true);
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true);
  });

  it('displays register link', () => {
    const wrapper = mount(Login, {
      global: { plugins: [router] },
    });

    const link = wrapper.find('a');
    expect(link.exists()).toBe(true);
    expect(link.text()).toContain('Register');
  });

  it('has initial empty state', () => {
    const wrapper = mount(Login, {
      global: { plugins: [router] },
    });

    const usernameInput = wrapper.find('input[type="text"]');
    const passwordInput = wrapper.find('input[type="password"]');
    expect(usernameInput.element.value).toBe('');
    expect(passwordInput.element.value).toBe('');
  });

  it('shows loading state during login', async () => {
    const wrapper = mount(Login, {
      global: { plugins: [router] },
    });

    await wrapper.find('input[type="text"]').setValue('testuser');
    await wrapper.find('input[type="password"]').setValue('password');

    const submitButton = wrapper.find('button[type="submit"]');
    expect(submitButton.text()).toContain('Sign In');
  });

  it('displays error message on login failure', async () => {
    const wrapper = mount(Login, {
      global: { plugins: [router] },
    });

    await wrapper.find('input[type="text"]').setValue('wronguser');
    await wrapper.find('input[type="password"]').setValue('wrongpass');
    await wrapper.find('form').trigger('submit');

    await vi.waitFor(() => {
      expect(wrapper.find('.error-msg').exists()).toBe(true);
    });
  });
});
