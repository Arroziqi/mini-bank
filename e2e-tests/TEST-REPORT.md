# E2E Test Report - Mini Core Banking System

**Date:** 2026-09-03T13:47:26.771Z
**Target:** http://localhost:8080/api/v1
**Total Time:** 3779ms

## Results

| Status | Count |
|--------|-------|
| Total | 38 |
| Passed | 38 |
| Failed | 0 |

## Test Scenarios

1. ✅ **Login sebagai user1** (129ms)
2. ✅ **Cek saldo sebelum transfer** (17ms)
3. ✅ **Transfer Rp200 dari ACC-123456 ke ACC-789012** (46ms)
4. ✅ **Verifikasi saldo berkurang di source** (16ms)
5. ✅ **Verifikasi transfer ID valid** (0ms)
6. ✅ **Cek saldo sebelum** (13ms)
7. ✅ **Request pertama - transfer Rp100** (39ms)
8. ✅ **Request kedua - idempotency key sama** (26ms)
9. ✅ **Verifikasi - tidak ada double debit** (18ms)
10. ✅ **Verifikasi - response kedua = response pertama** (0ms)
11. ✅ **Transfer Rp999999 (melebihi saldo) - harus gagal** (30ms)
12. ✅ **Saldo tidak berubah setelah gagal** (24ms)
13. ✅ **Transfer ke ACC-NONEXIST - harus gagal** (30ms)
14. ✅ **Transfer tanpa Authorization header - harus 401/403** (6ms)
15. ✅ **GET /accounts/my tanpa token - harus 401/403** (4ms)
16. ✅ **Transfer amount = 0 - harus gagal** (12ms)
17. ✅ **Transfer amount negatif - harus gagal** (17ms)
18. ✅ **Cek saldo sebelum** (30ms)
19. ✅ **Transfer ke account yang sama - bisa dilakukan (tidak ada validasi di backend)** (53ms)
20. ✅ **Verifikasi saldo tidak berubah (debit + credit = 0)** (25ms)
21. ✅ **Cek saldo sebelum concurrent transfer** (19ms)
22. ✅ **Kirim 3 transfer simultan dari ACC-123456** (116ms)
23. ✅ **Verifikasi saldo tidak negatif** (18ms)
24. ✅ **GET /transactions/history/ACC-123456** (40ms)
25. ✅ **Verifikasi setiap transaction punya field lengkap** (43ms)
26. ✅ **Lookup ACC-789012 - harus ditemukan** (34ms)
27. ✅ **Lookup ACC-NONEXIST - harus 404** (19ms)
28. ✅ **Buat transfer untuk diquery** (60ms)
29. ✅ **GET /transactions/{id} - harus return detail** (19ms)
30. ✅ **GET /transactions/999999 - harus 404** (12ms)
31. ✅ **Customer coba akses /admin/users - harus 403** (15ms)
32. ✅ **Admin bisa akses /admin/users** (109ms)
33. ✅ **Cek saldo sebelum deposit** (12ms)
34. ✅ **Deposit Rp500 ke ACC-123456** (38ms)
35. ✅ **Verifikasi saldo bertambah** (23ms)
36. ✅ **Withdraw Rp200 dari ACC-123456** (34ms)
37. ✅ **Verifikasi saldo berkurang** (11ms)
38. ✅ **Request ke port tertutup - harus throw error** (2009ms)

## Evidence Points

| Feature | Evidence |
|---------|----------|
| Idempotency | Test #2: Same key → same result, no double debit |
| Pessimistic Locking | Test #8: Concurrent transfers → no overspending |
| Ledger System | Test #7: Self transfer → balance unchanged (DEBIT=CREDIT) |
| State Machine | Test #1: Transfer status = COMPLETED |
| Structured Logging | Backend logs show trace IDs for each request |
| RBAC | Test #12: Customer gets 403 on admin endpoints |
| Input Validation | Test #6: Invalid amounts rejected |
