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

const selectAccount = (acc) => {
  selectedAccount.value = acc;
  fetchHistory(acc.accountNumber);
};

const fetchHistory = async (accNum) => {
  try {
    const resp = await api.get(`/transactions/history/${accNum}`);
    history.value = resp.data.content;
  } catch (err) {
    console.error(err);
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleString('id-ID', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
};

const formatAmount = (tx) => {
  const prefix = tx.sourceAccountNumber === selectedAccount.value?.accountNumber ? '-' : '+';
  return `${prefix}Rp${Number(tx.amount).toLocaleString()}`;
};

const getCounterparty = (tx) => {
  if (tx.type === 'DEPOSIT') return tx.targetAccountNumber || '-';
  if (tx.type === 'WITHDRAWAL') return tx.sourceAccountNumber || '-';
  return tx.sourceAccountNumber === selectedAccount.value?.accountNumber
    ? tx.targetAccountNumber
    : tx.sourceAccountNumber;
};
</script>

<template>
  <div class="dashboard">
    <header class="header">
      <h1>Account Overview</h1>
      <p>Welcome back, manage your finances with ease.</p>
    </header>

    <div class="account-grid">
      <div
        v-for="acc in accountStore.accounts"
        :key="acc.id"
        class="card account-card"
        :class="{ active: selectedAccount?.id === acc.id }"
        @click="selectAccount(acc)"
      >
        <div class="acc-info">
          <span class="acc-num">{{ acc.accountNumber }}</span>
          <span :class="['acc-status', acc.status.toLowerCase()]">{{ acc.status }}</span>
        </div>
        <div class="acc-balance">
          <span class="currency">Rp</span>
          <span class="amount">{{ acc.balance?.toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <section class="transactions card">
      <div class="tx-header">
        <h3>Transaction History</h3>
        <span class="tx-count" v-if="history.length">{{ history.length }} transactions</span>
      </div>

      <div v-if="history.length === 0" class="tx-empty">
        No transactions yet
      </div>

      <table v-else class="tx-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Type</th>
            <th>Counterparty</th>
            <th>Status</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tx in history" :key="tx.id">
            <td>{{ formatDate(tx.createdAt) }}</td>
            <td><span :class="['tx-type', tx.type.toLowerCase()]">{{ tx.type }}</span></td>
            <td>{{ getCounterparty(tx) }}</td>
            <td>
              <span :class="['tx-status', tx.status?.toLowerCase()]">{{ tx.status || '-' }}</span>
            </td>
            <td :class="tx.sourceAccountNumber === selectedAccount?.accountNumber ? 'neg' : 'pos'">
              {{ formatAmount(tx) }}
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
  cursor: pointer;
  transition: border-color 0.2s;
}
.account-card:hover { border-color: var(--accent); }
.account-card.active { border-color: var(--accent); }

.acc-info { display: flex; justify-content: space-between; align-items: center; }
.acc-num { color: var(--text-secondary); font-size: 0.9rem; }
.acc-status { font-size: 0.7rem; padding: 2px 8px; border-radius: 4px; border: 1px solid currentColor; }
.acc-status.active { color: var(--success); }
.acc-balance { margin-top: 1.5rem; }
.currency { font-size: 1.2rem; color: var(--accent); vertical-align: top; }
.amount { font-size: 2.5rem; font-weight: 700; margin-left: 0.2rem; }

.tx-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
.tx-count { color: var(--text-secondary); font-size: 0.85rem; }
.tx-empty { text-align: center; padding: 3rem; color: var(--text-secondary); }

.tx-table { width: 100%; border-collapse: collapse; text-align: left; }
.tx-table th { padding: 1rem; color: var(--text-secondary); font-size: 0.9rem; border-bottom: 1px solid rgba(255,255,255,0.05); }
.tx-table td { padding: 1rem; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.95rem; }

.tx-type { font-size: 0.75rem; padding: 2px 6px; border-radius: 4px; background: rgba(255,255,255,0.05); }
.tx-type.deposit { color: var(--success); }
.tx-type.withdrawal { color: var(--error); }
.tx-type.transfer { color: #3b82f6; }

.tx-status { font-size: 0.75rem; padding: 2px 6px; border-radius: 4px; }
.tx-status.completed { color: var(--success); background: rgba(34,197,94,0.1); }
.tx-status.initiated { color: #f59e0b; background: rgba(245,158,11,0.1); }
.tx-status.failed { color: var(--error); background: rgba(239,68,68,0.1); }

.pos { color: var(--success); }
.neg { color: var(--error); }
</style>
