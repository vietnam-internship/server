# Test fixture data

These SQL files are **not** Flyway migrations — `spring.flyway.locations` still only points at
`classpath:db/migration`, so nothing here runs automatically on boot. They exist purely so a
local/test DB can be seeded by hand (e.g. `mysql travelx < 01_currencies.sql`, in order) before
exercising the reservation → payment flow end to end.

Load order matters (foreign keys): `01_currencies.sql` → `02_branches.sql` →
`03_branch_currency_setup.sql` → `04_demo_branch_admin.sql`.

Assumes an empty/fresh schema — the files pin explicit ids (currencies 1-5, branches 1-4) for
readability and cross-references. Adjust ids if the target DB already has rows in these tables.

## Demo BRANCH_ADMIN account (`04_demo_branch_admin.sql`)

For demoing the admin QR scan flow (`/admin/qr-scan`), a `BRANCH_ADMIN` user mapped to
`branch_id=1` (TravelX Myeongdong — matches `AdminQrScanPage.tsx`'s hardcoded `BRANCH_ID=1`):

- login: `POST /auth/admin/login` with `email=branch1.admin@travelx.test`, `password=Demo1234!`
- `BRANCH_ADMIN` is restricted to its own `branch_id` by `assertBranchAccess()` on every
  branch-scoped admin endpoint (rates/inventory/QR redeem) — this account can only act on
  branch 1. Delete the row after the demo if you don't want a standing password-login account.

Not included on purpose:
- The system `ADMIN` role account — the user inserts that directly into the DB (see the removed
  `V14__seed_admin_account.sql`).
- Regular customer test accounts — with `travelx.dev.auth.enabled=true` (local profile default),
  `POST /dev/auth/token` with any `{ "email": "...", "role": "USER" }` creates (or reuses) a user
  and hands back a ready-to-use access token, phone-verification already marked done. No SQL
  needed for that (note: this dev endpoint doesn't accept a `branchId`, so it can't stand in for
  the `BRANCH_ADMIN` demo account above).
- `branch_time_slots` — `ReservationHoldService.lockTimeSlot` calls `ensureExists(...)`, which
  creates the slot row lazily on first reservation for a given (branch, date, time). No seed
  needed.

Reservation creation also calls out to Stripe for a real `PaymentIntent` (`PaymentGatewayImpl`) —
`STRIPE_SECRET_KEY` must be set to a valid Stripe **test** key for `POST /reservations` to succeed
at runtime. That's an env var, not something this SQL can provide.
