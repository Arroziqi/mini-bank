<script setup>
import { ref, onMounted, computed } from 'vue';
import { useAccountStore } from '../stores/account';
import api from '../api';
import { useRouter } from 'vue-router';

const accountStore = useAccountStore();
const router = useRouter();

const fromAccount = ref('');
const toAccount = ref('');
const amount = ref(0);
const description = ref('');
const loading = ref(false);
const error = ref('');
const step = ref('form'); // 'form' | 'confirm' | 'success'
const transferResult = ref(null);
const targetLookup = ref(null);
const looking = ref(false);

onMounted(async () => {
  await accountStore.fetchMyAccounts();
  if (accountStore.accounts.length > 0) {
    fromAccount.value = accountStore.accounts[0].accountNumber;
  }
});

const selectedSource = computed(() =>
  accountStore.accounts.find(a => a.accountNumber === fromAccount.value)
);

const generateIdempotencyKey = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
};

const validateAndPreview = async () => {
  error.value = '';

  if (!fromAccount.value) {
    error.value = 'Please select a source account';
    return;
  }
  if (!toAccount.value) {
    error.value = 'Please enter a target account number';
    return;
  }
  if (fromAccount.value === toAccount.value) {
    error.value = 'Cannot transfer to the same account';
    return;
  }
  if (amount.value <= 0) {
    error.value = 'Amount must be greater than 0';
    return;
  }
  if (selectedSource.value && amount.value > selectedSource.value.balance) {
    error.value = 'Insufficient balance';
    return;
  }

  // Look up target account
  looking.value = true;
  try {
    const resp = await api.get(`/accounts/lookup/${toAccount.value}`);
    targetLookup.value = resp.data;
  } catch (err) {
    error.value = 'Target account not found';
    looking.value = false;
    return;
  }
  looking.value = false;

  step.value = 'confirm';
};

const handleTransfer = async () => {
  loading.value = true;
  error.value = '';
  try {
    const idempotencyKey = generateIdempotencyKey();
    console.log('[Transfer] Sending request:', {
      source: fromAccount.value,
      target: toAccount.value,
      amount: amount.value,
      idempotencyKey
    });
    const resp = await api.post('/transactions/transfer', {
      sourceAccountNumber: fromAccount.value,
      targetAccountNumber: toAccount.value,
      amount: amount.value,
      description: description.value || null,
    }, {
      headers: { 'Idempotency-Key': idempotencyKey }
    });
    console.log('[Transfer] Success:', resp.data);
    transferResult.value = resp.data;
    step.value = 'success';
    await accountStore.fetchMyAccounts();
  } catch (err) {
    console.error('[Transfer] Failed:', err.response?.status, err.response?.data, err.message);
    error.value = err.response?.data?.message || err.message || 'Transfer failed';
    // Stay on confirm step so user can see error and retry
  } finally {
    loading.value = false;
  }
};

const goToDashboard = () => {
  router.push('/dashboard');
};

const makeAnother = () => {
  step.value = 'form';
  toAccount.value = '';
  amount.value = 0;
  description.value = '';
  transferResult.value = null;
  targetLookup.value = null;
};

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return date.toLocaleString('id-ID', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).replace(',', '');
};
</script>

<template>
  <div class="transfer-view">
    <header class="header">
      <h1>Transfer Funds</h1>
      <p>Send money to any account securely.</p>
    </header>

    <!-- Step 1: Form -->
    <div v-if="step === 'form'" class="card form-card">
      <form @submit.prevent="validateAndPreview">
        <div class="form-group">
          <label>From Account</label>
          <select v-model="fromAccount" class="input">
            <option v-for="acc in accountStore.accounts" :key="acc.id" :value="acc.accountNumber">
              {{ acc.accountNumber }} - Rp{{ acc.balance?.toLocaleString() }}
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>Target Account Number</label>
          <input v-model="toAccount" type="text" class="input" placeholder="Enter account number" required />
        </div>

        <div class="form-group">
          <label>Amount (Rp)</label>
          <input v-model.number="amount" type="number" min="1" step="1" class="input" required />
        </div>

        <div class="form-group">
          <label>Description (optional)</label>
          <input v-model="description" type="text" class="input" placeholder="e.g. Payment for invoice" maxlength="200" />
        </div>

        <div v-if="error" class="error-msg">{{ error }}</div>

        <button type="submit" class="btn btn-primary" :disabled="looking">
          {{ looking ? 'Checking account...' : 'Preview Transfer' }}
        </button>
      </form>
    </div>

    <!-- Step 2: Confirmation -->
    <div v-if="step === 'confirm'" class="card form-card">
      <div class="confirm-header">
        <h3>Confirm Transfer</h3>
        <p>Please review the details below</p>
      </div>

      <div class="confirm-details">
        <div class="confirm-row">
          <span class="confirm-label">From</span>
          <span class="confirm-value">{{ fromAccount }}</span>
        </div>
        <div class="confirm-row">
          <span class="confirm-label">To</span>
          <span class="confirm-value">{{ toAccount }}</span>
        </div>
        <div v-if="targetLookup" class="confirm-row recipient">
          <span class="confirm-label">Recipient</span>
          <span class="confirm-value">
            <span class="recipient-name">{{ targetLookup.holderName }}</span>
            <span class="recipient-verified">&#10003; Verified</span>
          </span>
        </div>
        <div class="confirm-row highlight">
          <span class="confirm-label">Amount</span>
          <span class="confirm-value amount">Rp{{ amount.toLocaleString() }}</span>
        </div>
        <div v-if="description" class="confirm-row">
          <span class="confirm-label">Description</span>
          <span class="confirm-value">{{ description }}</span>
        </div>
      </div>

      <div v-if="error" class="error-msg">{{ error }}</div>

      <div class="confirm-actions">
        <button class="btn btn-secondary" @click="step = 'form'" :disabled="loading">Back</button>
        <button class="btn btn-primary" @click="handleTransfer" :disabled="loading">
          {{ loading ? 'Processing...' : 'Confirm Transfer' }}
        </button>
      </div>
    </div>

    <!-- Step 3: Success - Digital Bank Style Receipt/Struk -->
    <div v-if="step === 'success'" class="receipt-container">
      <div class="receipt-card">
        <!-- Bank Header -->
        <div class="receipt-header">
          <div class="bank-brand">
            <div class="bank-logo">MiniBank</div>
            <span class="bank-tagline">Digital Banking</span>
          </div>
          <div class="receipt-status">
            <div class="status-badge completed">
              <svg class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              <span>Berhasil</span>
            </div>
          </div>
        </div>

        <!-- Transaction Type -->
        <div class="transaction-type">
          <span class="type-label">Transfer Dana</span>
        </div>

        <!-- Amount Section -->
        <div class="amount-section">
          <div class="amount-label">Jumlah Transfer</div>
          <div class="amount-value">Rp{{ transferResult.amount?.toLocaleString('id-ID') }}</div>
        </div>

        <!-- Divider -->
        <div class="divider"></div>

        <!-- Details Grid -->
        <div class="details-grid">
          <div class="detail-item">
            <div class="detail-label">Dari Rekening</div>
            <div class="detail-value">{{ transferResult.sourceAccountNumber }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">Ke Rekening</div>
            <div class="detail-value">{{ transferResult.targetAccountNumber }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">Penerima</div>
            <div class="detail-value recipient-name">{{ targetLookup?.holderName || transferResult.targetAccountNumber }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">Transaksi ID</div>
            <div class="detail-value tx-id">#{{ transferResult.id }}</div>
          </div>
          <div class="detail-item">
            <div class="detail-label">Waktu</div>
            <div class="detail-value">{{ formatDateTime(transferResult.createdAt) }}</div>
          </div>
          <div v-if="transferResult.description" class="detail-item full-width">
            <div class="detail-label">Catatan</div>
            <div class="detail-value">{{ transferResult.description }}</div>
          </div>
        </div>

        <!-- Divider -->
        <div class="divider"></div>

        <!-- Fee Info -->
        <div class="fee-info">
          <div class="fee-row">
            <span>Biaya Admin</span>
            <span class="fee-amount">Gratis</span>
          </div>
          <div class="fee-row total">
            <span>Total Dibayarkan</span>
            <span class="total-amount">Rp{{ transferResult.amount?.toLocaleString('id-ID') }}</span>
          </div>
        </div>

        <!-- Divider -->
        <div class="divider"></div>

        <!-- Balance After -->
        <div class="balance-after">
          <div class="balance-label">Saldo Sekarang</div>
          <div class="balance-value">Rp{{ selectedSource?.balance?.toLocaleString('id-ID') }}</div>
        </div>

        <!-- QR Code Placeholder -->
        <div class="qr-section">
          <div class="qr-code">
            <svg viewBox="0 0 80 80" width="80" height="80">
              <rect width="80" height="80" fill="white"/>
              <rect x="10" y="10" width="10" height="10" fill="black"/>
              <rect x="25" y="10" width="5" height="5" fill="black"/>
              <rect x="35" y="10" width="10" height="10" fill="black"/>
              <rect x="50" y="10" width="5" height="5" fill="black"/>
              <rect x="60" y="10" width="10" height="10" fill="black"/>
              <rect x="10" y="25" width="5" height="5" fill="black"/>
              <rect x="20" y="25" width="10" height="10" fill="black"/>
              <rect x="35" y="25" width="5" height="5" fill="black"/>
              <rect x="45" y="25" width="5" height="5" fill="black"/>
              <rect x="55" y="25" width="10" height="10" fill="black"/>
              <rect x="10" y="35" width="10" height="10" fill="black"/>
              <rect x="25" y="35" width="5" height="5" fill="black"/>
              <rect x="35" y="35" width="5" height="5" fill="black"/>
              <rect x="45" y="35" width="10" height="10" fill="black"/>
              <rect x="10" y="45" width="5" height="5" fill="black"/>
              <rect x="20" y="45" width="10" height="10" fill="black"/>
              <rect x="35" y="45" width="5" height="5" fill="black"/>
              <rect x="45" y="45" width="10" height="10" fill="black"/>
              <rect x="10" y="55" width="5" height="5" fill="black"/>
              <rect x="20" y="55" width="10" height="10" fill="black"/>
              <rect x="35" y="55" width="5" height="5" fill="black"/>
              <rect x="45" y="55" width="5" height="5" fill="black"/>
              <rect x="55" y="55" width="10" height="10" fill="black"/>
              <rect x="10" y="65" width="10" height="10" fill="black"/>
              <rect x="25" y="65" width="5" height="5" fill="black"/>
              <rect x="35" y="65" width="10" height="10" fill="black"/>
              <rect x="10" y="70" width="5" height="5" fill="black"/>
              <rect x="20" y="70" width="10" height="10" fill="black"/>
            </svg>
          </div>
          <div class="qr-label">Simpan struk ini</div>
        </div>

        <!-- Actions -->
        <div class="receipt-actions">
          <button class="btn btn-outline" @click="goToDashboard">Kembali ke Beranda</button>
          <button class="btn btn-primary" @click="makeAnother">Transfer Lagi</button>
        </div>

        <!-- Footer -->
        <div class="receipt-footer">
          <p>Terima kasih telah menggunakan MiniBank</p>
          <p class="disclaimer">Struk ini sah sebagai bukti transaksi digital. Simpan untuk keperluan record.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== Base Styles ===== */
.header { margin-bottom: 2rem; }
.form-card { max-width: 500px; margin: 0 auto; }
.form-group { margin-bottom: 1.5rem; }
.form-group label { display: block; margin-bottom: 0.5rem; font-size: 0.9rem; color: var(--text-secondary); }
.error-msg { color: var(--error); margin-bottom: 1.5rem; font-size: 0.9rem; padding: 0.75rem; background: rgba(239, 68, 68, 0.1); border-radius: 6px; }
.btn { width: 100%; padding: 0.875rem; font-size: 0.95rem; font-weight: 600; border-radius: 8px; border: none; cursor: pointer; transition: all 0.2s; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary { background: linear-gradient(135deg, var(--accent), #0d9488); color: white; box-shadow: 0 4px 14px rgba(6, 182, 212, 0.3); }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(6, 182, 212, 0.4); }
.btn-secondary { background: transparent; border: 1px solid var(--text-secondary); color: var(--text-secondary); }
.btn-secondary:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.btn-outline { background: transparent; border: 1px solid var(--accent); color: var(--accent); }
.btn-outline:hover:not(:disabled) { background: rgba(6, 182, 212, 0.08); }

/* ===== Form & Confirm Styles ===== */
.confirm-header { text-align: center; margin-bottom: 1.5rem; }
.confirm-header h3 { color: var(--accent); margin-bottom: 0.25rem; }
.confirm-header p { color: var(--text-secondary); font-size: 0.9rem; }

.confirm-details { margin-bottom: 2rem; }
.confirm-row { display: flex; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
.confirm-label { color: var(--text-secondary); font-size: 0.9rem; }
.confirm-value { font-weight: 500; }
.confirm-row.highlight { border-bottom: none; padding: 1rem 0; }
.amount { font-size: 1.5rem; color: var(--accent); font-weight: 700; }

.confirm-actions { display: flex; gap: 1rem; }
.confirm-actions .btn { flex: 1; }

.recipient { background: rgba(34, 197, 94, 0.05); border-radius: 6px; padding: 0.75rem !important; margin: 0.5rem 0; }
.recipient-name { font-weight: 600; color: var(--success); }
.recipient-verified { font-size: 0.75rem; color: var(--success); margin-left: 0.5rem; opacity: 0.8; }

/* ===== Digital Bank Receipt (Struk) Styles ===== */
.receipt-container {
  max-width: 420px;
  margin: 0 auto;
  padding: 1rem;
}

.receipt-card {
  background: linear-gradient(180deg, #0f172a 0%, #1e293b 100%);
  border-radius: 20px;
  padding: 1.75rem 1.5rem;
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(148, 163, 184, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  position: relative;
  overflow: hidden;
}

.receipt-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #06b6d4, #22d3ee, #06b6d4);
  background-size: 200% 100%;
  animation: shimmer 3s linear infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Header */
.receipt-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.bank-brand {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.bank-logo {
  font-size: 1.5rem;
  font-weight: 800;
  background: linear-gradient(135deg, #06b6d4, #22d3ee, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
}

.bank-tagline {
  font-size: 0.7rem;
  color: rgba(148, 163, 184, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: 500;
}

.receipt-status {
  display: flex;
  align-items: center;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.status-badge.completed {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.check-icon {
  width: 14px;
  height: 14px;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  animation: checkIn 0.3s ease-out;
}

@keyframes checkIn {
  from { stroke-dashoffset: 50; opacity: 0; }
  to { stroke-dashoffset: 0; opacity: 1; }
}

/* Transaction Type */
.transaction-type {
  text-align: center;
  margin-bottom: 1rem;
}

.type-label {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  color: rgba(148, 163, 184, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Amount Section */
.amount-section {
  text-align: center;
  margin: 1rem 0 1.5rem;
}

.amount-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: rgba(148, 163, 184, 0.6);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 0.5rem;
}

.amount-value {
  font-size: 2.75rem;
  font-weight: 800;
  background: linear-gradient(135deg, #ffffff 0%, #e2e8f0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

/* Divider */
.divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.2), transparent);
  margin: 1rem 0;
}

/* Details Grid */
.details-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.detail-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 12px;
  padding: 0.875rem 1rem;
  transition: all 0.2s;
}

.detail-item:hover {
  border-color: rgba(6, 182, 212, 0.3);
  box-shadow: 0 4px 12px rgba(6, 182, 212, 0.08);
}

.detail-item.full-width {
  grid-column: 1 / -1;
}

.detail-label {
  font-size: 0.65rem;
  font-weight: 600;
  color: rgba(148, 163, 184, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 0.375rem;
}

.detail-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: #f1f5f9;
  word-break: break-all;
}

.tx-id {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
  color: #94a3b8;
}

.recipient-name {
  font-weight: 700;
  color: #22c55e;
}

/* Fee Info */
.fee-info {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 12px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.fee-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.375rem 0;
}

.fee-row:not(:last-child) {
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);
}

.fee-row.total {
  margin-top: 0.25rem;
  padding-top: 0.625rem;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
}

.fee-amount {
  font-weight: 600;
  color: #22c55e;
  font-size: 0.9rem;
}

.total-amount {
  font-weight: 700;
  font-size: 1.1rem;
  color: #ffffff;
}

/* Balance After */
.balance-after {
  text-align: center;
  padding: 1rem;
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1), rgba(6, 182, 212, 0.02));
  border: 1px solid rgba(6, 182, 212, 0.2);
  border-radius: 12px;
  margin-bottom: 1rem;
}

.balance-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: rgba(6, 182, 212, 0.8);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 0.375rem;
}

.balance-value {
  font-size: 1.5rem;
  font-weight: 800;
  color: #06b6d4;
}

/* QR Section */
.qr-section {
  text-align: center;
  margin: 1rem 0;
}

.qr-code {
  display: inline-block;
  padding: 0.75rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.qr-label {
  margin-top: 0.625rem;
  font-size: 0.7rem;
  color: rgba(148, 163, 184, 0.5);
}

/* Actions */
.receipt-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.receipt-actions .btn {
  flex: 1;
  padding: 0.875rem 1rem;
}

/* Footer */
.receipt-footer {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
  text-align: center;
}

.receipt-footer p {
  font-size: 0.85rem;
  color: rgba(148, 163, 184, 0.7);
  margin: 0.25rem 0;
}

.disclaimer {
  font-size: 0.65rem !important;
  color: rgba(148, 163, 184, 0.4) !important;
}

/* Responsive */
@media (max-width: 480px) {
  .receipt-container {
    padding: 0.5rem;
  }
  
  .receipt-card {
    padding: 1.5rem 1.25rem;
    border-radius: 16px;
  }
  
  .amount-value {
    font-size: 2.25rem;
  }
  
  .details-grid {
    grid-template-columns: 1fr;
  }
  
  .detail-item.full-width {
    grid-column: auto;
  }
}

/* Print Styles */
@media print {
  .receipt-container {
    max-width: 100%;
    padding: 0;
  }
  
  .receipt-card {
    box-shadow: none;
    border: 1px solid #e2e8f0;
    background: white;
    color: #0f172a;
  }
  
  .receipt-actions {
    display: none;
  }
}
</style>
