Kalau kita mau mendesain **API transfer yang mendekati sistem perbankan sungguhan**, saya akan mulai dari prinsip paling penting:

> **Jangan menganggap transfer sebagai `UPDATE balance A` lalu `UPDATE balance B`. Transfer adalah sebuah financial transaction yang harus punya identity, state, immutable ledger, idempotency, concurrency control, dan mekanisme recovery.**

Kita bikin contoh:

```text
Account A
Rp10.000.000

        transfer Rp2.000.000

Account B
Rp5.000.000
```

---

# 1. Arsitektur high-level

Untuk awal, saya justru **tidak akan langsung membuat microservices terlalu banyak**.

Architecture yang cukup realistis:

```text
                     ┌─────────────────┐
                     │ Mobile / Web    │
                     └────────┬────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │ API Gateway     │
                     └────────┬────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │ Transfer Service  │
                    └─────────┬─────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
       ┌────────────┐  ┌─────────────┐  ┌─────────────┐
       │ Account DB │  │ Ledger DB    │  │ Outbox      │
       └────────────┘  └─────────────┘  └──────┬──────┘
                                               │
                                               ▼
                                           ┌───────┐
                                           │ Kafka │
                                           └───┬───┘
                                               │
                          ┌────────────────────┼───────────────────┐
                          ▼                    ▼                   ▼
                   Notification          Fraud Detection    Reconciliation
```

Untuk **internal transfer dalam satu bank**, transaction utama bisa berada dalam satu database transaction.

Untuk **antar-bank**, ceritanya berbeda karena kita berhadapan dengan distributed system.

---

# 2. API contract

Misalnya:

```http
POST /v1/transfers
```

Request:

```json
{
  "source_account_id": "ACC-001",
  "destination_account_id": "ACC-002",
  "amount": 2000000,
  "currency": "IDR",
  "description": "Payment"
}
```

Header:

```http
Idempotency-Key: 8f9a7c12-...
Authorization: Bearer ...
```

Response:

```json
{
  "transfer_id": "TRX-20260903-001",
  "status": "COMPLETED",
  "amount": 2000000,
  "currency": "IDR"
}
```

Kenapa `Idempotency-Key` penting?

Nanti kita bahas.

---

# 3. Database schema

Saya akan memisahkan minimal menjadi:

```text
accounts
transfers
ledger_transactions
ledger_entries
outbox_events
```

## `accounts`

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Misalnya:

```text
id       balance
--------------------
ACC-A    10,000,000
ACC-B     5,000,000
```

### Kenapa `BIGINT`, bukan `FLOAT`?

Karena uang **jangan menggunakan floating point**.

Jangan:

```text
balance = 1000000.50
```

dengan tipe float.

Lebih aman:

```text
amount = 100000050
```

dalam satuan terkecil.

Untuk IDR, biasanya rupiah tidak memiliki fractional currency sehingga bisa langsung menggunakan rupiah sebagai integer.

---

# 4. `transfers`

Ini merepresentasikan **business transaction**.

```sql
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,

    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,

    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Contoh:

```text
TRX-001

source      ACC-A
destination ACC-B
amount      2,000,000
status      COMPLETED
```

---

# 5. Jangan hanya menyimpan `balance`

Ini bagian yang sangat penting.

Misalnya account:

```text
balance = 8,000,000
```

Kalau cuma melihat angka itu, kita tidak tahu:

```text
kenapa saldo menjadi 8 juta?
```

Maka diperlukan **ledger**.

---

# 6. Ledger transaction

```sql
CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

Kemudian:

```sql
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    ledger_transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,

    entry_type VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL
);
```

Satu transfer:

```text
Transfer Rp2 juta

Ledger
--------------------------------
Account A   DEBIT    2,000,000
Account B   CREDIT   2,000,000
```

Secara accounting:

```text
Total Debit  = 2,000,000
Total Credit = 2,000,000
```

Harus selalu balance.

---

# 7. Flow transfer normal

Sekarang kita masuk ke bagian paling penting.

Client:

```http
POST /transfers
Idempotency-Key: ABC123
```

Backend:

```text
1. Authenticate
2. Validate request
3. Check idempotency
4. Begin DB transaction
5. Lock source account
6. Validate balance
7. Create transfer
8. Create ledger transaction
9. Create ledger entries
10. Update balance
11. Create outbox event
12. Commit
13. Return success
```

Secara visual:

```text
Client
  │
  ▼
POST /transfers
  │
  ▼
Idempotency check
  │
  ▼
BEGIN TRANSACTION
  │
  ├── Lock Account A
  │
  ├── Check balance
  │
  ├── Create transfer
  │
  ├── Create ledger
  │
  ├── Debit A
  │
  ├── Credit B
  │
  └── Create outbox event
         │
         ▼
       COMMIT
         │
         ▼
      Response
```

---

# 8. Race condition

Ini masalah klasik.

Misalnya saldo:

```text
Account A = Rp1.000.000
```

Ada dua request bersamaan:

```text
Request 1 → transfer Rp800.000
Request 2 → transfer Rp800.000
```

Kalau implementation bodoh:

```text
Request 1:
read balance = 1m

Request 2:
read balance = 1m

Request 1:
balance = 200k

Request 2:
balance = 200k
```

Database akhirnya:

```text
200k
```

Padahal seharusnya salah satu transaksi gagal.

Ini disebut **race condition / lost update problem**.

---

# 9. Solusi pertama: pessimistic locking

Kita bisa menggunakan:

```sql
SELECT *
FROM accounts
WHERE id = 'ACC-A'
FOR UPDATE;
```

`FOR UPDATE` membuat row tersebut di-lock selama database transaction.

Request 1:

```text
BEGIN

SELECT account
FOR UPDATE

        ↓
Account A LOCKED
```

Request 2:

```text
BEGIN

SELECT account
FOR UPDATE

        ↓
WAIT...
```

Request 1:

```text
balance = 1m
transfer = 800k

balance = 200k

COMMIT
```

Baru Request 2 mendapatkan lock:

```text
balance = 200k
transfer = 800k

❌ insufficient balance
```

Hasil akhirnya:

```text
Account A = 200k
```

dan hanya satu transfer berhasil.

---

# 10. Alternatif: optimistic locking

Kita tadi punya:

```sql
version BIGINT
```

Misalnya:

```text
balance = 1,000,000
version = 10
```

Update:

```sql
UPDATE accounts
SET
    balance = 200000,
    version = version + 1
WHERE id = 'ACC-A'
AND version = 10;
```

Kalau request lain sudah mengubah version:

```text
version = 11
```

maka:

```text
rows_updated = 0
```

Artinya transaction gagal karena data sudah berubah.

---

# 11. Pessimistic vs optimistic

Untuk financial transaction, **pessimistic locking sering lebih straightforward** karena kita memang ingin memastikan satu account tidak diproses secara bersamaan dengan cara yang tidak aman.

Tapi pilihan tergantung:

```text
Pessimistic:
SELECT FOR UPDATE

Pros:
+ simple reasoning
+ strong concurrency control

Cons:
- lock contention
- transaction bisa menunggu
```

Optimistic:

```text
version number

Pros:
+ tidak menahan lock lama
+ bagus untuk high concurrency

Cons:
- perlu retry
- logic lebih kompleks
```

---

# 12. Deadlock

Ada masalah berikutnya.

Misalnya transfer:

```text
A → B
```

dan secara bersamaan:

```text
B → A
```

Transaction 1:

```text
LOCK A
WAIT B
```

Transaction 2:

```text
LOCK B
WAIT A
```

Hasil:

```text
A ← T1
     ↓
    WAIT
     ↑
B ← T2
```

**Deadlock.**

Database biasanya akan mendeteksi dan membatalkan salah satu transaction.

Tapi kita juga bisa mencegahnya dengan **consistent lock ordering**.

Contoh:

```text
sort account ID

ACC-A
ACC-B
```

Selalu lock:

```text
min(account_id)
        ↓
max(account_id)
```

Jadi baik:

```text
A → B
```

maupun:

```text
B → A
```

selalu:

```text
LOCK A
LOCK B
```

bukan berdasarkan source/destination.

---

# 13. Idempotency

Sekarang masalah lain.

User klik:

```text
Transfer
```

Request dikirim.

Server berhasil:

```text
Transfer Rp1 juta
```

Tetapi response:

```text
timeout
```

Mobile tidak tahu apakah transaksi berhasil.

User klik lagi.

```text
POST /transfers
```

Tanpa idempotency:

```text
Transfer #1 → -1m
Transfer #2 → -1m

Total → -2m
```

Padahal user hanya ingin transfer Rp1 juta.

---

# 14. Idempotency key

Client mengirim:

```http
Idempotency-Key: 8f9a7c12
```

Server menyimpan:

```text
idempotency_key
----------------
8f9a7c12
```

Request pertama:

```text
8f9a7c12
→ tidak ditemukan
→ execute transfer
→ SUCCESS
```

Request kedua:

```text
8f9a7c12
→ ditemukan
→ jangan execute lagi
→ return hasil sebelumnya
```

Jadi:

```text
POST #1
     ↓
TRANSFER
     ↓
SUCCESS
     ↓
store result

POST #2
     ↓
same idempotency key
     ↓
return previous result
```

---

# 15. Tapi idempotency check juga harus atomic

Jangan melakukan:

```text
SELECT idempotency_key

if not exists:
    INSERT
```

karena dua request bersamaan bisa melakukan:

```text
Request A → SELECT → tidak ada
Request B → SELECT → tidak ada

Request A → execute
Request B → execute
```

Gunakan:

```sql
UNIQUE(idempotency_key)
```

sebagai database constraint.

Atau gunakan atomic insert/upsert.

---

# 16. State machine

Transfer sebaiknya mempunyai state yang jelas.

Misalnya:

```text
PENDING
   │
   ▼
PROCESSING
   │
   ├──────────────► FAILED
   │
   ▼
COMPLETED
```

Untuk transfer eksternal:

```text
PENDING
   ↓
PROCESSING
   ↓
SUBMITTED
   ↓
SETTLED
```

Dan jangan sembarangan:

```text
COMPLETED → PENDING
```

State transition harus dikontrol.

---

# 17. Outbox Pattern

Sekarang masuk ke bagian yang sering bikin distributed system tricky.

Misalnya setelah transfer berhasil kita ingin publish:

```text
TransferCompleted
```

ke Kafka.

Naifnya:

```text
DB transaction
     ↓
COMMIT
     ↓
Kafka.publish()
```

Masalah:

```text
DB COMMIT berhasil
       ↓
Kafka.publish()
       ↓
💥 service crash
```

Transfer sudah sukses, tetapi event tidak pernah masuk Kafka.

Akibatnya:

```text
Ledger = correct
Kafka = missing event
```

---

# 18. Solusinya: Transactional Outbox

Dalam **database transaction yang sama**, kita insert:

```text
transfer
ledger
balance update
outbox event
```

Misalnya:

```sql
BEGIN;

INSERT INTO transfers ...

INSERT INTO ledger_transactions ...

INSERT INTO ledger_entries ...

UPDATE accounts ...

INSERT INTO outbox_events (
    event_type,
    aggregate_id,
    payload,
    status
);

COMMIT;
```

Kalau commit berhasil:

```text
DB
├── transfer ✓
├── ledger ✓
├── balance ✓
└── outbox ✓
```

Karena semuanya berada di **satu DB transaction**.

---

# 19. Outbox table

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL
);
```

Contoh:

```json
{
  "id": "EVENT-001",
  "type": "TransferCompleted",
  "transfer_id": "TRX-001",
  "amount": 2000000
}
```

---

# 20. Outbox publisher

Worker secara asynchronous membaca:

```text
outbox_events
```

kemudian:

```text
Outbox
   ↓
Publisher
   ↓
Kafka
```

Misalnya:

```text
EVENT-001
   ↓
Kafka publish
   ↓
success
   ↓
status = PUBLISHED
```

---

# 21. Kenapa Kafka tidak boleh dianggap exactly-once?

Ini juga penting.

Misalnya:

```text
Outbox
   ↓
Kafka.publish()
   ↓
Kafka SUCCESS
   ↓
💥 crash
```

Worker belum sempat:

```sql
UPDATE outbox
SET status = 'PUBLISHED'
```

Ketika restart:

```text
Outbox EVENT-001
→ masih dianggap unpublished
→ publish lagi
```

Kafka bisa menerima duplicate.

Jadi consumer harus **idempotent** juga.

---

# 22. Idempotent consumer

Consumer menerima:

```text
TransferCompleted
event_id = EVENT-001
```

Simpan processed event:

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
```

Saat menerima:

```text
EVENT-001
```

check:

```text
already processed?
```

Kalau iya:

```text
ACK
don't process again
```

Jadi arsitekturnya:

```text
Producer
   │
   ▼
Outbox
   │
   ▼
Kafka
   │
   ▼
Consumer
   │
   ├── check event_id
   │
   ├── process
   │
   └── mark processed
```

---

# 23. Failure scenario

Sekarang kita lihat kasus yang benar-benar penting.

### Case 1 — Client timeout

```text
Client
  ↓
Server
  ↓
DB COMMIT ✓
  ↓
Response
  ↓
💥 timeout
```

Client retry:

```text
same idempotency key
```

Server:

```text
already exists
→ return previous result
```

**No duplicate transfer.**

---

### Case 2 — DB gagal sebelum commit

```text
BEGIN
 ↓
debit
 ↓
ledger
 ↓
💥 DB ERROR
```

Database:

```text
ROLLBACK
```

Tidak ada transfer.

---

### Case 3 — Service crash setelah commit

```text
DB COMMIT ✓
 ↓
💥 application crash
```

Transfer tetap ada karena sudah committed.

Ketika service hidup lagi:

```text
transfer = COMPLETED
```

Tidak perlu melakukan transfer ulang.

---

### Case 4 — Kafka mati

```text
DB transaction
     ↓
COMMIT ✓
     ↓
Outbox ✓
     ↓
Kafka ❌
```

Transfer tetap sukses.

Outbox worker akan retry:

```text
Kafka down
   ↓
retry
   ↓
retry
   ↓
Kafka up
   ↓
publish
```

---

### Case 5 — Kafka duplicate

```text
EVENT-001
   ↓
Kafka
   ↓
Consumer
```

Consumer crash setelah processing.

Event dikirim ulang:

```text
EVENT-001
   ↓
already processed
   ↓
ignore
```

---

# 24. Bagaimana kalau debit berhasil tetapi credit gagal?

Untuk **internal transfer dalam satu database**, kita hindari kondisi ini menggunakan database transaction.

```text
BEGIN

Debit A
Credit B

COMMIT
```

Kalau credit gagal:

```text
ROLLBACK
```

Jadi:

```text
Debit A ✓
Credit B ❌

→ rollback debit
```

---

# 25. Tapi kalau antar-bank?

Ini yang paling berbeda.

Misalnya:

```text
Bank A
   ↓
Bank B
```

Tidak ada satu database transaction:

```text
BEGIN
    debit Bank A
    credit Bank B
COMMIT
```

Karena database Bank A dan Bank B berbeda.

Ini **distributed transaction**.

Dan kita biasanya **tidak menggunakan 2PC untuk transaksi perbankan eksternal secara sederhana**.

Lebih realistis menggunakan model:

```text
Bank A
  ↓
Payment instruction
  ↓
Payment network
  ↓
Bank B
  ↓
Settlement
```

Dengan state machine dan reconciliation.

---

# 26. Contoh distributed transfer

Misalnya:

```text
TRX-001

Bank A
Rp10m
```

User transfer:

```text
Rp2m → Bank B
```

Bank A bisa membuat:

```text
status = PROCESSING
```

Kemudian mengirim payment instruction:

```text
PaymentMessage {
    transaction_id: TRX-001,
    source: BankA,
    destination: BankB,
    amount: 2m
}
```

Bank B:

```text
validate
   ↓
credit
   ↓
acknowledge
```

Bank A:

```text
SUBMITTED
   ↓
SETTLED
```

---

# 27. Jangan langsung menganggap timeout = failed

Misalnya:

```text
Bank A → Bank B
```

Bank A mengirim:

```text
TRANSFER TRX-001
```

Bank B sebenarnya berhasil:

```text
credit ✓
```

Tapi response:

```text
💥 network timeout
```

Bank A tidak tahu:

```text
SUCCESS?
FAILED?
```

Ini disebut **unknown outcome**.

Jangan langsung:

```text
timeout → FAILED
```

Karena bisa menyebabkan duplicate transfer jika retry.

Lebih aman:

```text
UNKNOWN / PENDING
```

kemudian:

```text
status inquiry
```

atau reconciliation.

---

# 28. Reconciliation

Ini salah satu komponen yang sangat penting dalam banking.

Misalnya Bank A memiliki:

```text
TRX-001 = SENT
TRX-002 = SENT
TRX-003 = SENT
```

Bank B/payment network memberikan settlement report:

```text
TRX-001 = SUCCESS
TRX-002 = SUCCESS
TRX-003 = FAILED
```

Reconciliation system membandingkan:

```text
Internal records
       vs
External records
```

Kalau mismatch:

```text
Internal: SUCCESS
External: FAILED
```

→ masuk exception queue.

---

# 29. Jadi Kafka digunakan untuk apa?

Kafka **bukan sumber kebenaran utama saldo**.

Jangan membuat:

```text
Kafka event
   ↓
update balance
```

sebagai satu-satunya mekanisme financial correctness.

Lebih aman:

```text
Database / Ledger
      ↓
Source of truth
      ↓
Outbox
      ↓
Kafka
      ↓
Side effects
```

Kafka cocok untuk:

```text
Notification
Fraud detection
Analytics
Audit pipeline
Search index
Reporting
Reconciliation
```

Sedangkan financial state tetap harus punya source of truth yang kuat.

---

# 30. Full architecture

Kalau digabung:

```text
                       CLIENT
                          │
                          ▼
                  ┌──────────────┐
                  │ API Gateway  │
                  └──────┬───────┘
                         │
                         ▼
                 ┌────────────────┐
                 │ Transfer API   │
                 └───────┬────────┘
                         │
             ┌───────────▼────────────┐
             │ Idempotency Validation │
             └───────────┬────────────┘
                         │
                         ▼
                  BEGIN TRANSACTION
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
        Lock Accounts          Validate Balance
              │                     │
              └──────────┬──────────┘
                         ▼
                  Create Transfer
                         │
                         ▼
                  Create Ledger
                         │
                         ▼
                 Update Balances
                         │
                         ▼
                  Create Outbox
                         │
                         ▼
                       COMMIT
                         │
                         ▼
                       200 OK
                         
                         ...async...

                    ┌──────────┐
                    │  Outbox  │
                    │  Worker  │
                    └────┬─────┘
                         │
                         ▼
                       Kafka
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
         Fraud       Notification  Analytics
```

---

# 31. Prinsip yang saya pegang

Kalau ini keluar sebagai **system design interview**, saya akan menyebut 8 prinsip utama:

### 1. Ledger sebagai financial source of truth

Jangan hanya mengandalkan:

```text
accounts.balance
```

Harus ada immutable ledger.

### 2. Database transaction untuk atomicity

Untuk internal transfer:

```text
Debit + Credit + Ledger + Outbox
```

harus commit bersama.

### 3. Concurrency control

Gunakan:

```text
SELECT FOR UPDATE
```

atau optimistic locking.

### 4. Idempotency

Setiap request transfer harus memiliki:

```text
Idempotency-Key
```

dan database constraint.

### 5. Outbox

Jangan:

```text
DB commit
+
Kafka publish
```

sebagai dua operasi yang tidak terkoordinasi.

Gunakan:

```text
DB transaction
→ Outbox
→ Kafka
```

### 6. Idempotent consumer

Kafka delivery bisa duplicate.

Consumer harus bisa menerima:

```text
EVENT-001
EVENT-001
EVENT-001
```

tanpa menyebabkan side effect tiga kali.

### 7. Distributed transaction ≠ database transaction

Untuk antar-bank:

```text
PENDING
→ PROCESSING
→ SUBMITTED
→ SETTLED
```

dengan retry, status inquiry, timeout handling, dan reconciliation.

### 8. Auditability

Untuk financial system, kita harus bisa menjawab:

> "Kenapa saldo account ini berubah?"

Bukan hanya:

```text
balance = 8m
```

tetapi:

```text
10m
 ↓
-2m transfer TRX-001
 ↓
8m
```

dan transfer tersebut punya:

```text
who
what
when
amount
source
destination
status
reference
```

---

## Kalau saya implementasikan dengan stack modern

Misalnya menggunakan **NestJS + PostgreSQL + Kafka**, struktur servicenya kira-kira:

```text
apps/
└── transfer-service/
    ├── transfer/
    │   ├── transfer.controller.ts
    │   ├── transfer.service.ts
    │   ├── transfer.repository.ts
    │   └── transfer.state-machine.ts
    │
    ├── ledger/
    │   ├── ledger.service.ts
    │   └── ledger.repository.ts
    │
    ├── account/
    │   ├── account.repository.ts
    │   └── account.service.ts
    │
    ├── outbox/
    │   ├── outbox.repository.ts
    │   └── outbox.worker.ts
    │
    └── kafka/
        └── kafka.publisher.ts
```

Dan **core transaction** saya akan usahakan sesingkat mungkin:

```text
BEGIN
  ↓
lock account(s)
  ↓
validate
  ↓
create transfer
  ↓
create ledger entries
  ↓
update balance
  ↓
insert outbox
  ↓
COMMIT
```

Sedangkan hal-hal seperti:

```text
send notification
fraud analysis
analytics
email
push notification
```

**jangan dikerjakan di dalam DB transaction tersebut**.

Itulah salah satu pembeda antara desain transfer sederhana:

```text
POST /transfer
→ UPDATE balance
```

dengan desain yang lebih mendekati **production-grade financial system**:

```text
API
 ↓
Idempotency
 ↓
Concurrency control
 ↓
Atomic ledger transaction
 ↓
Outbox
 ↓
Kafka
 ↓
Idempotent consumers
 ↓
Reconciliation
```

Kalau dibawa ke level **system-design interview**, bagian paling menarik berikutnya adalah **mendesain schema + sequence diagram lengkap untuk `POST /transfers`, termasuk apa yang terjadi ketika 2 request transfer bersamaan, client timeout, DB crash, Kafka crash, dan bank tujuan timeout**. Itu bisa kita bedah sampai level SQL/transaction isolation dan pseudocode NestJS.

Oke. Kita bedah seperti **system-design interview**, dari `POST /transfers` sampai failure scenario. Saya pakai contoh **internal transfer dalam satu bank** dulu, karena di sini kita bisa benar-benar melihat atomic transaction, locking, isolation, dan outbox.

---

# 1. Requirement dulu

Misalkan API:

```http
POST /v1/transfers
Idempotency-Key: 7f3a-abc-123
```

```json
{
  "source_account_id": "A",
  "destination_account_id": "B",
  "amount": 800000
}
```

Requirement:

* saldo tidak boleh negatif
* transfer tidak boleh terduplikasi
* dua transfer concurrent tidak boleh membuat overspending
* debit dan credit harus atomic
* setiap perubahan uang harus tercatat di ledger
* event harus eventually masuk Kafka
* retry aman
* semua transaksi dapat diaudit

---

# 2. Schema yang lebih production-like

Saya sederhanakan PostgreSQL-nya.

### accounts

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    balance BIGINT NOT NULL CHECK (balance >= 0),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

### transfers

```sql
CREATE TABLE transfers (
    id UUID PRIMARY KEY,

    idempotency_key VARCHAR(255) NOT NULL UNIQUE,

    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,

    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

### ledger transactions

```sql
CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL
);
```

### ledger entries

```sql
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,

    ledger_transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,

    entry_type VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),

    created_at TIMESTAMPTZ NOT NULL
);
```

### outbox

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,

    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,

    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,

    published_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL
);
```

---

# 3. Sequence diagram normal

Flow normalnya:

```text
Client
  │
  │ POST /transfers
  │ Idempotency-Key: ABC
  ▼
Transfer API
  │
  │ BEGIN
  ▼
PostgreSQL
  │
  ├── check idempotency
  │
  ├── lock accounts
  │
  ├── validate balance
  │
  ├── create transfer
  │
  ├── create ledger
  │
  ├── update balances
  │
  ├── create outbox
  │
  └── COMMIT
  │
  ▼
Client
  │
  │ 200 OK
  ▼
```

Lalu **di luar transaction**:

```text
Outbox Worker
      │
      ▼
    Kafka
      │
      ├── Notification
      ├── Fraud
      └── Analytics
```

---

# 4. Pseudocode service

Kira-kira seperti ini:

```ts
async transfer(command: TransferCommand) {
  return db.transaction(async (tx) => {

    // 1. Idempotency
    const existing = await tx.transfers.findUnique({
      where: {
        idempotencyKey: command.idempotencyKey
      }
    });

    if (existing) {
      return existing;
    }

    // 2. Lock accounts
    const accounts = await lockAccounts(
      tx,
      command.sourceAccountId,
      command.destinationAccountId
    );

    const source = accounts.source;
    const destination = accounts.destination;

    // 3. Validation
    if (source.status !== "ACTIVE") {
      throw new Error("SOURCE_ACCOUNT_INACTIVE");
    }

    if (destination.status !== "ACTIVE") {
      throw new Error("DESTINATION_ACCOUNT_INACTIVE");
    }

    if (source.currency !== destination.currency) {
      throw new Error("CURRENCY_MISMATCH");
    }

    if (source.balance < command.amount) {
      throw new Error("INSUFFICIENT_BALANCE");
    }

    // 4. Create transfer
    const transfer = await tx.transfers.create({
      data: {
        idempotencyKey: command.idempotencyKey,
        sourceAccountId: source.id,
        destinationAccountId: destination.id,
        amount: command.amount,
        currency: source.currency,
        status: "COMPLETED"
      }
    });

    // 5. Create ledger
    const ledger = await tx.ledgerTransactions.create({
      data: {
        transferId: transfer.id
      }
    });

    // 6. Ledger entries
    await tx.ledgerEntries.createMany({
      data: [
        {
          ledgerTransactionId: ledger.id,
          accountId: source.id,
          entryType: "DEBIT",
          amount: command.amount
        },
        {
          ledgerTransactionId: ledger.id,
          accountId: destination.id,
          entryType: "CREDIT",
          amount: command.amount
        }
      ]
    });

    // 7. Update balances
    await updateBalance(
      tx,
      source.id,
      -command.amount
    );

    await updateBalance(
      tx,
      destination.id,
      command.amount
    );

    // 8. Outbox
    await tx.outboxEvents.create({
      data: {
        aggregateType: "TRANSFER",
        aggregateId: transfer.id,
        eventType: "TransferCompleted",
        payload: {
          transferId: transfer.id,
          amount: command.amount
        }
      }
    });

    return transfer;
  });
}
```

Semua yang berhubungan dengan **financial state** berada dalam satu transaction.

---

# 5. Locking-nya bagaimana?

Kita perlu berhati-hati.

Misalnya:

```text
A → B
```

dan bersamaan:

```text
B → A
```

Kalau kita melakukan:

```sql
SELECT *
FROM accounts
WHERE id = source_account
FOR UPDATE;
```

maka bisa terjadi deadlock.

Lebih aman kita lock berdasarkan urutan ID.

Contoh:

```text
A < B
```

Selalu:

```text
LOCK A
LOCK B
```

bahkan ketika transaksi sebenarnya:

```text
B → A
```

Jadi:

```text
Transfer A → B
    ↓
LOCK A
LOCK B
```

dan:

```text
Transfer B → A
    ↓
LOCK A
LOCK B
```

Urutan lock konsisten.

---

# 6. Race condition yang sebenarnya

Sekarang kasus yang lebih menarik.

Saldo:

```text
A = Rp1.000.000
```

Dua request:

```text
T1: A → B Rp800.000
T2: A → C Rp800.000
```

Tanpa locking:

```text
T1 read = 1m
T2 read = 1m

T1 → approve
T2 → approve
```

Hasilnya:

```text
A = -600k
```

atau lebih buruk, tergantung implementation, balance bisa terlihat tidak konsisten.

Dengan:

```sql
SELECT *
FROM accounts
WHERE id = 'A'
FOR UPDATE;
```

T1 memperoleh lock.

```text
T1
│
├── LOCK A
│
├── balance = 1m
│
├── debit 800k
│
└── COMMIT
```

T2 menunggu.

```text
T2
│
└── WAIT A
```

Setelah T1 commit:

```text
A = 200k
```

T2 akhirnya memperoleh lock:

```text
T2
│
├── LOCK A
├── balance = 200k
├── 200k < 800k
└── INSUFFICIENT_BALANCE
```

**Hanya T1 yang berhasil.**

---

# 7. Kenapa isolation level juga penting?

PostgreSQL default biasanya:

```text
READ COMMITTED
```

Untuk banyak kasus ini sudah cukup jika kita menggunakan explicit row locking dengan benar.

Tapi kita perlu memahami bahwa:

```text
isolation level
≠
concurrency control secara otomatis
```

Misalnya sekadar:

```sql
SELECT balance FROM accounts;
```

tidak otomatis membuat account aman dari concurrent update.

Untuk operasi financial yang memerlukan serialized modification:

```sql
SELECT ...
FOR UPDATE;
```

lebih eksplisit.

---

# 8. Idempotency + transaction

Sekarang kasus:

```text
Client
  ↓
POST transfer
  ↓
Server
  ↓
COMMIT ✓
  ↓
💥 response timeout
```

Client berpikir:

> "Waduh gagal."

Lalu retry:

```http
POST /transfers
Idempotency-Key: ABC
```

Server:

```sql
SELECT *
FROM transfers
WHERE idempotency_key = 'ABC';
```

Ketemu:

```text
TRX-001
COMPLETED
```

Server tidak menjalankan transfer lagi.

Return:

```json
{
  "transfer_id": "TRX-001",
  "status": "COMPLETED"
}
```

Makanya **idempotency key bukan sekadar fitur API**, tetapi bagian dari financial correctness.

---

# 9. Ada subtle problem dengan idempotency

Misalnya request pertama:

```json
{
  "source": "A",
  "destination": "B",
  "amount": 800000
}
```

Key:

```text
ABC
```

Kemudian request kedua:

```json
{
  "source": "A",
  "destination": "C",
  "amount": 900000
}
```

tetap:

```text
ABC
```

Ini harus ditolak.

Karena idempotency key yang sama seharusnya merepresentasikan **request yang sama**.

Kita bisa menyimpan request fingerprint/hash:

```text
idempotency_key
request_hash
response
status
```

Kemudian:

```text
ABC + hash X
```

valid.

Tetapi:

```text
ABC + hash Y
```

→

```text
409 IDEMPOTENCY_KEY_REUSED
```

---

# 10. Outbox secara detail

Misalkan transaction berhasil.

Kita punya:

```text
transfers
TRX-001 COMPLETED

ledger
DEBIT A 800k
CREDIT B 800k

outbox
EVENT-001 TransferCompleted
```

Semua:

```text
COMMIT ✓
```

Kemudian worker:

```text
SELECT *
FROM outbox_events
WHERE published_at IS NULL
ORDER BY created_at
LIMIT 100;
```

Publish:

```text
EVENT-001
     ↓
   Kafka
```

Setelah berhasil:

```sql
UPDATE outbox_events
SET published_at = NOW()
WHERE id = 'EVENT-001';
```

---

# 11. Kenapa outbox harus satu DB transaction?

Bayangkan tanpa outbox:

```text
BEGIN
  update balance
COMMIT

Kafka.publish()
```

Crash di sini:

```text
COMMIT ✓
   ↓
💥 CRASH
   ↓
Kafka.publish() never happens
```

Transfer:

```text
SUCCESS
```

tetapi:

```text
TransferCompleted event
MISSING
```

Dengan outbox:

```text
BEGIN
  update balance
  create ledger
  insert outbox
COMMIT
```

Kalau commit berhasil:

```text
financial state ✓
outbox ✓
```

Kafka boleh mati sekalipun.

Worker akan retry.

---

# 12. Failure scenario: application crash

Misalnya:

```text
BEGIN
 ↓
lock A
 ↓
lock B
 ↓
debit
 ↓
credit
 ↓
ledger
 ↓
outbox
 ↓
💥 application crash
```

Kalau belum `COMMIT`:

```text
DATABASE
→ rollback
```

Tidak ada perubahan financial state.

Kalau crash setelah:

```text
COMMIT ✓
```

maka data tetap ada.

Jadi restart service tidak boleh:

```text
"transfer belum ada di memory → execute ulang"
```

Server harus selalu mempercayai database sebagai source of truth.

---

# 13. Failure scenario: Kafka mati

```text
DB
│
├── transfer ✓
├── ledger ✓
├── balance ✓
└── outbox ✓
       │
       ▼
     Kafka
       X
```

Transfer tetap:

```text
COMPLETED
```

Outbox:

```text
published_at = NULL
```

Worker retry:

```text
retry
retry
retry
```

Ketika Kafka kembali:

```text
outbox
  ↓
Kafka ✓
```

---

# 14. Failure scenario: Kafka duplicate

Ini juga penting.

Misalnya worker:

```text
publish EVENT-001
```

Kafka menerima:

```text
✓
```

Tapi sebelum worker update database:

```text
💥 crash
```

Setelah restart:

```text
EVENT-001
published_at = NULL
```

Worker publish lagi.

Kafka:

```text
EVENT-001
EVENT-001
```

Maka consumer harus idempotent.

Contohnya:

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
```

Consumer:

```ts
async handle(event) {

  const alreadyProcessed =
    await db.processedEvents.findUnique({
      where: {
        eventId: event.id
      }
    });

  if (alreadyProcessed) {
    return;
  }

  await db.transaction(async tx => {

    await processEvent(tx, event);

    await tx.processedEvents.create({
      data: {
        eventId: event.id
      }
    });

  });
}
```

---

# 15. Failure scenario paling sulit: external bank timeout

Sekarang kita pindah ke **antar-bank**.

```text
Bank A
   │
   │ transfer Rp800k
   ▼
Payment Network
   │
   ▼
Bank B
```

Bank A kirim:

```text
TRX-001
```

Bank B sebenarnya:

```text
credit ✓
```

Tetapi response:

```text
💥 timeout
```

Bank A tidak tahu apakah:

```text
SUCCESS
```

atau:

```text
FAILED
```

Ini **bukan sekadar error biasa**.

Statusnya:

```text
UNKNOWN
```

Jangan:

```text
timeout → FAILED
```

karena kalau sebenarnya berhasil, retry bisa menyebabkan duplicate.

---

# 16. State machine external transfer

Saya akan menggunakan state:

```text
                ┌───────────┐
                │  PENDING  │
                └─────┬─────┘
                      │
                      ▼
                ┌───────────┐
                │ PROCESSING│
                └─────┬─────┘
                      │
                ┌─────┴─────┐
                ▼           ▼
           ┌─────────┐  ┌─────────┐
           │ SETTLED │  │ UNKNOWN │
           └─────────┘  └────┬────┘
                              │
                              ▼
                         STATUS INQUIRY
                              │
                       ┌──────┴──────┐
                       ▼             ▼
                    SETTLED        FAILED
```

Jadi `UNKNOWN` bukan berarti failed.

---

# 17. Bagaimana recovery dari UNKNOWN?

Misalnya:

```text
TRX-001 = UNKNOWN
```

Reconciliation/status inquiry:

```text
GET transaction status TRX-001
```

External system:

```text
TRX-001 = SETTLED
```

Maka:

```text
UNKNOWN → SETTLED
```

Kalau:

```text
TRX-001 = FAILED
```

maka:

```text
UNKNOWN → FAILED
```

Ini jauh lebih aman daripada blindly retry.

---

# 18. Di sinilah reconciliation sangat penting

Pada skala bank, kita tidak hanya mengandalkan API response.

Misalnya internal:

```text
TRX-001 → SETTLED
TRX-002 → SETTLED
TRX-003 → PROCESSING
```

Kemudian external settlement report:

```text
TRX-001 → SETTLED
TRX-002 → FAILED
TRX-003 → SETTLED
```

Ada mismatch:

```text
TRX-002
Internal  = SETTLED
External  = FAILED
```

Ini masuk:

```text
RECONCILIATION_EXCEPTION
```

dan harus ditangani berdasarkan settlement rules.

---

# 19. Distributed transaction: jangan dipaksakan seperti ACID DB

Ini kesalahan umum saat system design.

Orang sering berpikir:

```text
Bank A DB
      +
Bank B DB

BEGIN
UPDATE A
UPDATE B
COMMIT
```

Secara teori bisa menggunakan distributed transaction/2PC pada sistem yang terkontrol.

Tetapi untuk ekosistem payment antar institusi, lebih realistis menggunakan:

```text
state machine
+
durable messaging
+
idempotency
+
retry
+
status inquiry
+
reconciliation
```

Karena kita tidak bisa mengontrol transaction boundary database pihak lain.

---

# 20. Satu detail yang sangat penting: jangan hold DB lock saat call external service

Misalnya:

```text
BEGIN

LOCK account A
LOCK account B

call Bank B API
   ↓
   waiting 5 seconds

COMMIT
```

Ini buruk.

Selama 5 detik:

```text
Account A = LOCKED
Account B = LOCKED
```

Traffic meningkat → lock contention meningkat.

Bahkan bisa menyebabkan cascading failure.

Lebih baik:

```text
DB transaction
   ↓
reserve/debit according to business model
   ↓
COMMIT

external call
   ↓
Bank B
```

Kemudian state machine menangani asynchronous result.

---

# 21. Kalau saldo harus di-reserve?

Untuk external transfer, kita bisa punya konsep:

```text
available_balance
ledger_balance
reserved_amount
```

Misalnya:

```text
Ledger balance    = 10m
Reserved          = 2m
Available         = 8m
```

Saat transfer:

```text
10m
 ↓
reserve 2m
 ↓
available = 8m
```

Kemudian kalau settlement berhasil:

```text
reserved 2m
 ↓
final debit
```

Kalau gagal:

```text
reserved 2m
 ↓
release
 ↓
available kembali 10m
```

Ini lebih cocok untuk transaksi asynchronous.

---

# 22. Jadi model account bisa berkembang

Daripada hanya:

```text
balance
```

kita bisa punya:

```text
accounts
-----------------------
ledger_balance
available_balance
reserved_balance
```

dengan invariant:

```text
available_balance
+
reserved_balance
=
ledger_balance
```

Tentunya implementasi sebenarnya bisa berbeda tergantung ledger architecture.

---

# 23. Apa yang terjadi kalau user double-click?

Request:

```text
POST #1
Key = ABC
```

dan:

```text
POST #2
Key = ABC
```

hampir bersamaan.

Database:

```text
UNIQUE(idempotency_key)
```

akan memastikan hanya satu yang menjadi owner.

Misalnya:

```text
T1 → INSERT ABC ✓
T2 → INSERT ABC ✗
```

T2 kemudian membaca transaction T1.

Hasil:

```text
T1 → transfer executed
T2 → return same transfer
```

Tidak ada double debit.

---

# 24. Endpoint GET juga penting

Jangan hanya:

```http
POST /transfers
```

Tetapi:

```http
GET /transfers/{transferId}
```

Response:

```json
{
  "id": "TRX-001",
  "status": "PROCESSING",
  "amount": 800000,
  "currency": "IDR"
}
```

Ini sangat penting untuk mobile app.

Misalnya:

```text
POST
 ↓
timeout
```

Mobile bisa:

```text
GET /transfers/TRX-001
```

untuk mengetahui status.

---

# 25. API error semantics

Jangan semua error jadi:

```http
500 Internal Server Error
```

Contoh:

```text
400
INVALID_AMOUNT

403
ACCOUNT_NOT_AUTHORIZED

409
IDEMPOTENCY_KEY_REUSED

422
INSUFFICIENT_BALANCE

503
PAYMENT_NETWORK_UNAVAILABLE
```

Tapi hati-hati:

```text
503
```

tidak selalu berarti transfer gagal.

Untuk financial transaction, client harus membedakan:

```text
definitely failed
```

dengan:

```text
outcome unknown
```

---

# 26. Kalau saya ditanya interviewer: "Kenapa tidak cukup pakai Redis?"

Jawaban saya:

Redis bisa membantu:

```text
distributed lock
rate limiting
idempotency cache
```

tetapi saya **tidak akan menjadikan Redis sebagai source of truth untuk financial balance**.

Misalnya:

```text
Redis
balance = 1m
```

kemudian Redis crash.

Kita tidak boleh kehilangan financial state.

Source of truth harus durable, biasanya database/ledger system yang dirancang untuk itu.

---

# 27. Kalau ditanya "Kenapa Kafka?"

Jawaban:

> Kafka bukan digunakan untuk memastikan transfer atomic. Atomicity financial transaction tetap ditangani oleh database transaction/ledger. Kafka digunakan untuk mendistribusikan event secara asynchronous ke downstream services, sedangkan Outbox Pattern memastikan event tidak hilang setelah financial transaction berhasil.

Ini jawaban yang cukup kuat untuk interview.

---

# 28. Final architecture

Kalau saya gambar di whiteboard interview, saya akan buat seperti ini:

```text
                         CLIENT
                           │
                           ▼
                    ┌─────────────┐
                    │ API Gateway │
                    └──────┬──────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ Transfer Service │
                  └────────┬─────────┘
                           │
                    Idempotency
                           │
                           ▼
                  ┌──────────────────┐
                  │   PostgreSQL     │
                  │                  │
                  │ Accounts         │
                  │ Transfers        │
                  │ Ledger           │
                  │ Outbox           │
                  └────────┬─────────┘
                           │
                         COMMIT
                           │
                           ▼
                    ┌─────────────┐
                    │Outbox Worker│
                    └──────┬──────┘
                           │
                           ▼
                        ┌───────┐
                        │ Kafka │
                        └───┬───┘
                            │
             ┌──────────────┼─────────────┐
             ▼              ▼             ▼
           Fraud      Notification     Analytics


External Transfer:

PostgreSQL
    │
    ▼
Transfer State Machine
    │
    ▼
Payment Network
    │
    ▼
External Bank
    │
    ├── SUCCESS
    ├── FAILED
    └── UNKNOWN
            │
            ▼
      Status Inquiry
            │
            ▼
      Reconciliation
```

Dan kalau diringkas menjadi **satu kalimat untuk system-design interview**:

> **"I would treat the ledger as the financial source of truth, use PostgreSQL transactions with deterministic row locking for atomic internal transfers, enforce idempotency at the database level, use transactional outbox to reliably publish events to Kafka, make consumers idempotent, and handle external transfers through an explicit state machine with retries, status inquiry, and reconciliation rather than trying to force a distributed ACID transaction."**

Itu sudah masuk level **production-grade system design**, bukan sekadar CRUD transfer.
