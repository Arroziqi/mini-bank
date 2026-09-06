# E2E Test Scenarios - Mini Core Banking System (MVP2)

## Overview

Test suite ini menguji fitur transfer production-grade yang diimplementasikan di MVP2.
Setiap test scenario dirancang untuk membuktikan bahwa solusi teknis yang diterapkan benar-benar menjawab real-world banking problems.

---

## Test Scenarios

### Scenario 1: Normal Transfer (Happy Path)
- **Edge Case:** User melakukan transfer normal
- **Proves:** Core transfer flow berfungsi end-to-end
- **Flow:** Login → Cek Saldo → Transfer → Verifikasi Saldo Berkurang
- **Expected:** Saldo source berkurang, status = COMPLETED

### Scenario 2: Idempotency (Double-Click Protection)
- **Edge Case:** User double-click tombol transfer / client timeout lalu retry
- **Proves:** Idempotency mencegah duplicate transfer
- **Flow:** Kirim 2x request dengan idempotency key yang sama
- **Expected:** Hanya 1 transfer yang terjadi, response kedua = response pertama
- **Why Important:** Di production, client timeout sering terjadi. Tanpa idempotency, user bisa kehilangan uang 2x.

### Scenario 3: Insufficient Balance
- **Edge Case:** User transfer melebihi saldo
- **Proves:** Balance validation bekerja
- **Flow:** Transfer Rp999999 dari account yang saldo-nya tidak cukup
- **Expected:** Returns error, saldo tidak berubah
- **Why Important:** Mencegah negative balance yang bisa merusak integritas data.

### Scenario 4: Account Not Found
- **Edge Case:** User salah memasukkan nomor account
- **Proves:** Account validation bekerja
- **Flow:** Transfer ke account yang tidak ada
- **Expected:** Returns 404/400
- **Why Important:** Mencegah transfer ke account phantom.

### Scenario 5: Unauthenticated Access
- **Edge Case:** Request tanpa token JWT (session expired / unauthorized)
- **Proves:** Authentication enforcement bekerja
- **Flow:** Transfer tanpa Authorization header
- **Expected:** Returns 401 Unauthorized
- **Why Important:** Mencegah unauthorized access ke sistem perbankan.

### Scenario 6: Invalid Amount
- **Edge Case:** User input amount 0 atau negatif
- **Proves:** Input validation bekerja
- **Flow:** Transfer dengan amount 0 dan -100
- **Expected:** Returns 400 Bad Request
- **Why Important:** Mencegah data corruption dari input invalid.

### Scenario 7: Self Transfer
- **Edge Case:** User transfer ke account yang sama
- **Proves:** Ledger entries balance (DEBIT = CREDIT)
- **Flow:** Transfer dari ACC-123456 ke ACC-123456
- **Expected:** Saldo tidak berubah (net effect = 0)
- **Why Important:** Membuktikan double-entry accounting benar. DEBIT Rp50 + CREDIT Rp50 = net Rp0.

### Scenario 8: Concurrent Transfers (Race Condition)
- **Edge Case:** 3 user transfer dari account yang sama secara bersamaan
- **Proves:** Pessimistic locking mencegah overspending
- **Flow:** Kirim 3 transfer simultan dari ACC-123456
- **Expected:** Tidak ada overspending, saldo tidak negatif
- **Why Important:** Tanpa locking, concurrent transfers bisa menyebabkan double-spending (contoh: saldo Rp1000, 2 transfer Rp800 bersamaan = saldo -Rp600).

### Scenario 9: Transfer History
- **Edge Case:** User melihat riwayat transaksi
- **Proves:** Audit trail lengkap
- **Flow:** GET /transactions/history/{accountNumber}
- **Expected:** Return paginated list dengan field lengkap (id, amount, type, status, createdAt)
- **Why Important:** User harus bisa memverifikasi transaksi mereka.

### Scenario 10: Account Lookup
- **Edge Case:** User mencari account sebelum transfer
- **Proves:** Account verification bekerja
- **Flow:** Lookup valid account dan invalid account
- **Expected:** Valid → 200, Invalid → 404
- **Why Important:** Mencegah transfer ke account yang salah.

### Scenario 11: Transfer Status
- **Edge Case:** User memeriksa status transfer setelah timeout
- **Proves:** Transfer tracking bekerja
- **Flow:** GET /transactions/{transferId}
- **Expected:** Return detail transfer dengan status
- **Why Important:** Setelah client timeout, user perlu mengecek apakah transfer berhasil atau tidak.

### Scenario 12: Admin Access Control
- **Edge Case:** Customer mencoba akses admin endpoint
- **Proves:** Role-Based Access Control bekerja
- **Flow:** Customer akses /admin/users
- **Expected:** Returns 403 Forbidden
- **Why Important:** Mencegah unauthorized access ke data sensitif.

### Scenario 13: Deposit & Withdrawal
- **Edge Case:** Verifikasi semua jenis transaksi
- **Proves:** Deposit dan withdrawal juga berfungsi
- **Flow:** Deposit → verifikasi saldo bertambah → Withdraw → verifikasi saldo berkurang
- **Expected:** Saldo akhir = saldo awal + deposit - withdrawal
- **Why Important:** Memastikan semua jenis transaksi bekerja dengan benar.

### Scenario 14: Network Timeout
- **Edge Case:** Server tidak tersedia (simulasi network failure)
- **Proves:** Error handling untuk network issues
- **Flow:** Request ke port yang tidak ada
- **Expected:** Graceful error handling, tidak crash
- **Why Important:** Di production, server bisa down kapan saja. Error handling harus graceful.

---

## Technology Evidence

| Technology | Evidence | Test Scenario |
|------------|----------|---------------|
| **Pessimistic Locking** | Concurrent transfers tidak overspend | #8 |
| **Idempotency Key** | Double-click tidak duplicate | #2 |
| **Double-Entry Ledger** | Self transfer saldo tidak berubah | #7 |
| **State Machine** | Transfer status = COMPLETED | #1 |
| **JWT Authentication** | Unauthenticated = 401 | #5 |
| **Role-Based Access** | Customer = 403 on admin | #12 |
| **Input Validation** | Invalid amount = 400 | #6 |
| **Structured Logging** | Trace ID di setiap request | All |
| **Outbox Pattern** | Outbox event created per transfer | #1 |
| **Structured Error Response** | Error response dengan traceId | #3, #4, #6 |

---

## How to Run

```bash
# Pastikan backend berjalan
cd e2e-tests
node run-tests.js

# Atau dengan verbose output
VERBOSE=1 node run-tests.js
```

## Output

- Console output: Real-time test results
- `TEST-REPORT.md`: Generated report dengan semua results
- Backend logs: Trace ID untuk setiap request (lihat `logs/audit.log`)
