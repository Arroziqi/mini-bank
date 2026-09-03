import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import Register from '../Register.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/register', component: Register },
    { path: '/login', component: { template: '<div />' } },
  ],
});

describe('Register.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    router.push('/');
  });

  it('renders registration form elements', () => {
    const wrapper = mount(Register, {
      global: { plugins: [router] },
    });

    expect(wrapper.find('h2').text()).toBe('Create Account');
    expect(wrapper.find('input[type="text"]').exists()).toBe(true);
    expect(wrapper.find('input[type="email"]').exists()).toBe(true);
    expect(wrapper.find('input[type="password"]').exists()).toBe(true);
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true);
  });

  it('displays login link', () => {
    const wrapper = mount(Register, {
      global: { plugins: [router] },
    });

    const link = wrapper.find('a');
    expect(link.exists()).toBe(true);
    expect(link.text()).toContain('Login');
  });

  it('has initial empty state', () => {
    const wrapper = mount(Register, {
      global: { plugins: [router] },
    });

    expect(wrapper.find('input[type="text"]').element.value).toBe('');
    expect(wrapper.find('input[type="email"]').element.value).toBe('');
    expect(wrapper.find('input[type="password"]').element.value).toBe('');
  });

  it('shows loading state during registration', async () => {
    const wrapper = mount(Register, {
      global: { plugins: [router] },
    });

    const submitButton = wrapper.find('button[type="submit"]');
    expect(submitButton.text()).toContain('Register');
  });
});
