import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '../auth';

vi.mock('../../api', () => ({
  default: {
    post: vi.fn(),
  },
}));

import api from '../../api';

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('should start unauthenticated', () => {
    const auth = useAuthStore();
    expect(auth.isAuthenticated).toBe(false);
    expect(auth.isAdmin).toBe(false);
    expect(auth.user).toBeNull();
    expect(auth.token).toBeNull();
  });

  it('login should set token and user', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-123', username: 'user1', role: 'CUSTOMER' },
    });

    const auth = useAuthStore();
    await auth.login('user1', 'pass123');

    expect(auth.isAuthenticated).toBe(true);
    expect(auth.token).toBe('jwt-123');
    expect(auth.user).toEqual({ username: 'user1', role: 'CUSTOMER' });
  });

  it('login should persist to localStorage', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-123', username: 'user1', role: 'CUSTOMER' },
    });

    const auth = useAuthStore();
    await auth.login('user1', 'pass123');

    expect(localStorage.getItem('token')).toBe('jwt-123');
    expect(JSON.parse(localStorage.getItem('user'))).toEqual({
      username: 'user1',
      role: 'CUSTOMER',
    });
  });

  it('login should throw on API error', async () => {
    api.post.mockRejectedValue(new Error('Invalid credentials'));

    const auth = useAuthStore();
    await expect(auth.login('user1', 'wrong')).rejects.toThrow('Invalid credentials');
    expect(auth.isAuthenticated).toBe(false);
  });

  it('logout should clear state', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-123', username: 'user1', role: 'CUSTOMER' },
    });

    const auth = useAuthStore();
    await auth.login('user1', 'pass123');
    auth.logout();

    expect(auth.isAuthenticated).toBe(false);
    expect(auth.token).toBeNull();
    expect(auth.user).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('register should call API', async () => {
    api.post.mockResolvedValue({ data: 'User registered successfully!' });

    const auth = useAuthStore();
    await auth.register('newuser', 'password123', 'new@example.com');

    expect(api.post).toHaveBeenCalledWith('/auth/register', {
      username: 'newuser',
      password: 'password123',
      email: 'new@example.com',
    });
  });

  it('isAdmin should return true for ADMIN role', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-admin', username: 'admin', role: 'ADMIN' },
    });

    const auth = useAuthStore();
    await auth.login('admin', 'admin123');

    expect(auth.isAdmin).toBe(true);
  });

  it('isAdmin should return false for CUSTOMER role', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-cust', username: 'cust', role: 'CUSTOMER' },
    });

    const auth = useAuthStore();
    await auth.login('cust', 'cust123');

    expect(auth.isAdmin).toBe(false);
  });
});
