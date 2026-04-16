<script setup>
import { onMounted, ref } from 'vue';
import { useAccountStore } from '../stores/account';
import api from '../api';

const accountStore = useAccountStore();
const history = ref([]);
const selectedAccount = ref(null);

onMounted(async () => {
  await accountStore.fetchMyAccounts();
  if (accountStore.accounts.length > 0) {
    selectedAccount.value = accountStore.accounts[0];
    fetchHistory(selectedAccount.value.accountNumber);
  }
});

const fetchHistory = async (accNum) => {
  try {
    const resp = await api.get(`/transactions/history/${accNum}`);
    history.value = resp.data.content;
  } catch (err) {
    console.error(err);
  }
};
</script>

<template>
  <div class="dashboard">
    <header class="header">
      <h1>Account Overview</h1>
      <p>Welcome back, manage your finances with ease.</p>
    </header>

    <div class="account-grid">
      <div v-for="acc in accountStore.accounts" :key="acc.id" class="card account-card">
        <div class="acc-info">
          <span class="acc-num">{{ acc.accountNumber }}</span>
          <span :class="['acc-status', acc.status.toLowerCase()]">{{ acc.status }}</span>
        </div>
        <div class="acc-balance">
          <span class="currency">$</span>
          <span class="amount">{{ acc.balance.toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <section class="transactions card">
      <div class="tx-header">
        <h3>Recent Transactions</h3>
      </div>
      <table class="tx-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Type</th>
            <th>From/To</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tx in history" :key="tx.id">
            <td>{{ new Date(tx.createdAt).toLocaleDateString() }}</td>
            <td><span :class="['tx-type', tx.type.toLowerCase()]">{{ tx.type }}</span></td>
            <td>{{ tx.sourceAccountNumber === selectedAccount?.accountNumber ? tx.targetAccountNumber : tx.sourceAccountNumber || 'External' }}</td>
            <td :class="tx.sourceAccountNumber === selectedAccount?.accountNumber ? 'neg' : 'pos'">
              {{ tx.sourceAccountNumber === selectedAccount?.accountNumber ? '-' : '+' }}${{ tx.amount }}
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.header { margin-bottom: 2.5rem; }
.header h1 { color: var(--accent); margin-bottom: 0.5rem; }
.header p { color: var(--text-secondary); }

.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2.5rem;
}

.account-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.acc-info { display: flex; justify-content: space-between; align-items: center; }
.acc-num { color: var(--text-secondary); font-size: 0.9rem; }
.acc-status { font-size: 0.7rem; padding: 2px 8px; border-radius: 4px; border: 1px solid currentColor; }
.acc-status.active { color: var(--success); }
.acc-balance { margin-top: 1.5rem; }
.currency { font-size: 1.2rem; color: var(--accent); vertical-align: top; }
.amount { font-size: 2.5rem; font-weight: 700; margin-left: 0.2rem; }

.tx-header { margin-bottom: 1.5rem; }
.tx-table { width: 100%; border-collapse: collapse; text-align: left; }
.tx-table th { padding: 1rem; color: var(--text-secondary); font-size: 0.9rem; border-bottom: 1px solid rgba(255,255,255,0.05); }
.tx-table td { padding: 1rem; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.95rem; }

.tx-type { font-size: 0.75rem; padding: 2px 6px; border-radius: 4px; background: rgba(255,255,255,0.05); }
.tx-type.deposit { color: var(--success); }
.tx-type.withdrawal { color: var(--error); }
.tx-type.transfer { color: #3b82f6; }

.pos { color: var(--success); }
.neg { color: var(--error); }
</style>
