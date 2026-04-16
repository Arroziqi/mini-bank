<script setup>
import { ref, onMounted } from 'vue';
import { useAccountStore } from '../stores/account';
import api from '../api';
import { useRouter } from 'vue-router';

const accountStore = useAccountStore();
const router = useRouter();

const fromAccount = ref('');
const toAccount = ref('');
const amount = ref(0);
const loading = ref(false);
const error = ref('');

onMounted(async () => {
  await accountStore.fetchMyAccounts();
  if (accountStore.accounts.length > 0) {
    fromAccount.value = accountStore.accounts[0].accountNumber;
  }
});

const handleTransfer = async () => {
  if (amount.value <= 0) {
    error.value = 'Amount must be greater than 0';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    await api.post('/transactions/transfer', {
      sourceAccountNumber: fromAccount.value,
      targetAccountNumber: toAccount.value,
      amount: amount.value,
      type: 'TRANSFER'
    });
    alert('Transfer successful!');
    router.push('/dashboard');
  } catch (err) {
    error.value = err.response?.data?.message || 'Transfer failed';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="transfer-view">
    <header class="header">
      <h1>Transfer Funds</h1>
      <p>Send money to any account securely.</p>
    </header>

    <div class="card form-card">
      <form @submit.prevent="handleTransfer">
        <div class="form-group">
          <label>From Account</label>
          <select v-model="fromAccount" class="input">
            <option v-for="acc in accountStore.accounts" :key="acc.id" :value="acc.accountNumber">
              {{ acc.accountNumber }} - ${{ acc.balance }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>Target Account Number</label>
          <input v-model="toAccount" type="text" class="input" placeholder="ACC-XXXXXX" required />
        </div>

        <div class="form-group">
          <label>Amount ($)</label>
          <input v-model="amount" type="number" step="0.01" class="input" required />
        </div>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <button type="submit" class="btn btn-primary" :disabled="loading">
          {{ loading ? 'Processing...' : 'Send Money' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.header { margin-bottom: 2rem; }
.form-card { max-width: 500px; margin: 0 auto; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; margin-bottom: 0.5rem; font-size: 0.9rem; color: var(--text-secondary); }
.error-msg { color: var(--error); margin-bottom: 1.5rem; font-size: 0.9rem; }
.btn { width: 100%; }
</style>
