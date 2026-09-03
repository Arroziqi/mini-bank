import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAccountStore } from '../account';

vi.mock('../../api', () => ({
  default: {
    get: vi.fn(),
  },
}));

import api from '../../api';

describe('Account Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('should start with empty accounts', () => {
    const accountStore = useAccountStore();
    expect(accountStore.accounts).toEqual([]);
    expect(accountStore.loading).toBe(false);
  });

  it('fetchMyAccounts should populate accounts', async () => {
    const mockAccounts = [
      { id: 1, accountNumber: 'ACC-123', balance: 1000, status: 'ACTIVE' },
      { id: 2, accountNumber: 'ACC-456', balance: 500, status: 'ACTIVE' },
    ];
    api.get.mockResolvedValue({ data: mockAccounts });

    const accountStore = useAccountStore();
    await accountStore.fetchMyAccounts();

    expect(accountStore.accounts).toEqual(mockAccounts);
    expect(accountStore.loading).toBe(false);
  });

  it('fetchMyAccounts should set loading during fetch', async () => {
    let resolvePromise;
    const promise = new Promise((resolve) => {
      resolvePromise = resolve;
    });
    api.get.mockReturnValue(promise);

    const accountStore = useAccountStore();
    const fetchPromise = accountStore.fetchMyAccounts();

    expect(accountStore.loading).toBe(true);

    resolvePromise({ data: [] });
    await fetchPromise;

    expect(accountStore.loading).toBe(false);
  });

  it('fetchMyAccounts should handle API error', async () => {
    api.get.mockRejectedValue(new Error('Request failed'));

    const accountStore = useAccountStore();
    await expect(accountStore.fetchMyAccounts()).rejects.toThrow('Request failed');

    expect(accountStore.accounts).toEqual([]);
    expect(accountStore.loading).toBe(false);
  });
});
