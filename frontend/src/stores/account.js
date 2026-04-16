import { defineStore } from 'pinia';
import api from '../api';

export const useAccountStore = defineStore('account', {
    state: () => ({
        accounts: [],
        loading: false,
    }),
    actions: {
        async fetchMyAccounts() {
            this.loading = true;
            try {
                const resp = await api.get('/accounts/my');
                this.accounts = resp.data;
            } finally {
                this.loading = false;
            }
        },
    },
});
