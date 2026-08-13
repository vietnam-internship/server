# System Admin (`/system/admin`) Design

## Purpose

Give the existing `ADMIN` role (currently unused beyond sharing the branch-admin
login gate) a dedicated console, separate from the branch-staff console at
`/admin/*`, so a system administrator can:

1. Log in at `/system/admin/login`.
2. See every registered user.
3. Withdraw (soft-delete) a user account.
4. Track every reservation across all branches in one list.

## Non-goals

- No new admin account provisioning flow — an `ADMIN`-role user already exists
  in the DB and is used as-is.
- No changes to the existing `/admin/*` (branch-admin) console or its API.
- No hard delete of user data. No cascading deletion/anonymization of
  reservations, payments, etc.
- No per-branch statistics dashboard — a single combined reservation list
  across all branches is sufficient for this iteration.

## Backend

### Auth

- `AdminAuthService` gains `loginAsSystemAdmin(email, rawPassword)`, mirroring
  `loginWithPassword` but validating against `Set.of(Role.ADMIN)` only (instead
  of `Set.of(Role.ADMIN, Role.BRANCH_ADMIN)`). The existing
  `loginWithPassword` / `/auth/admin/login` path is untouched.
- `AuthController` gains `POST /auth/system-admin/login`, same shape as
  `/auth/admin/login` (`AdminLoginRequest` in, `ApiResponse<GoogleLoginResponse>`
  out), calling the new service method.

### Soft-delete (withdraw)

- New migration `V17__add_status_to_users.sql`: adds `status VARCHAR(20) NOT
  NULL DEFAULT 'ACTIVE'` to `users`.
- New `UserStatus` enum (`ACTIVE`, `WITHDRAWN`) in `domain.user`.
- `User` gets a `status` field (default `ACTIVE` via `@Builder.Default`) and a
  `withdraw()` method that sets `status = WITHDRAWN`.
- Withdrawn users keep their row and all FK references (reservations, etc.)
  intact — nothing else changes shape.
- Every login path that issues tokens (`GoogleAuthService.loginWithGoogle`,
  `AdminAuthService.loginWithPassword`, the new `loginAsSystemAdmin`, and
  `/auth/reissue` in `AuthController`) must reject `WITHDRAWN` users with
  `BusinessException(UNAUTHORIZED)` — a withdrawn account cannot obtain or
  refresh a session, even if a still-valid access token exists until it
  expires naturally.

### New API surface (all under `@RequireAuth(roles = {"ADMIN"})`)

New packages: `presentation.systemadmin`, `application.systemadmin` (kept
separate from `presentation.admin` / `application.admin`, which stay
branch-admin-only).

- `GET /system-admin/users?q=&page=&size=`
  Lists users (id, name, email, role, status, createdAt), optionally filtered
  by name/email substring (case-insensitive), newest first, paginated.
- `POST /system-admin/users/{id}/withdraw`
  Calls `User.withdraw()`. Idempotent (withdrawing an already-withdrawn user
  is a no-op success, not an error). Returns `204`.
- `GET /system-admin/reservations?status=&q=&page=&size=`
  Same status-bucket semantics as `AdminReservationService`
  (`ALL`/`PENDING`/`COMPLETED`/`CANCELLED`) and same `q` (reservation number or
  customer name) search, but with no branch filter — returns reservations from
  every branch, each row annotated with branch name.

`UserRepository` gets a paged search query; `ReservationRepository` gets a
`findAllReservations(statuses, q, pageable)` query mirroring
`findBranchReservations` minus the `branchId` predicate.

## Frontend

Entirely new, parallel to the existing `/admin/*` console — no shared state
with it.

- `SYSTEM_ADMIN_AUTH`: new `HttpAuthConfig` (own localStorage keys
  `travelx.systemAdmin.accessToken` / `travelx.systemAdmin.user`, login path
  `/system/admin/login`), added next to `USER_AUTH`/`ADMIN_AUTH` in
  `utils/http.ts`.
- `useSystemAdminAuth` hook: copy of `useAdminAuth`, backed by the new storage
  keys and a new `systemAdminLogin` API call.
- `api/systemAdmin.ts`: `systemAdminLogin`, `listUsers`, `withdrawUser`,
  `listReservations`.
- `pages/SystemAdmin/`:
  - `SystemAdminLoginPage` — same shape as `AdminLoginPage`, posts to
    `systemAdminLogin`.
  - `SystemAdminLayout` — same shape as `AdminLayout`, two nav items: Users,
    Reservations.
  - `SystemAdminUsersPage` — searchable, paginated table (name, email, role,
    status, joined date); a "Withdraw" button per active row that opens a
    confirm step before calling `withdrawUser`; withdrawn rows show a status
    badge and no withdraw action.
  - `SystemAdminReservationsPage` — searchable, paginated table across all
    branches (reservation number, customer, branch, currency pair, amount,
    status), with the same status-filter tabs as `AdminReservationsPage`.
- `App.tsx` routes: `/system/admin/login`, `/system/admin` (redirect to
  `/system/admin/users`), `/system/admin/users`, `/system/admin/reservations`,
  each gated by `systemAdmin.isLoggedIn` the same way `requireAdmin` gates
  `/admin/*`.

## Error handling

- Withdraw a non-existent user id → 404 (existing `BusinessErrorCode` for
  not-found, reused from user lookup elsewhere).
- Withdraw called twice → second call succeeds as a no-op (see above).
- System-admin login with a `BRANCH_ADMIN` or `USER` account → `401
  UNAUTHORIZED`, same as a wrong password (no information leak about which
  part failed).
- List endpoints: standard pagination bounds, no special-casing.

## Testing

- `AdminAuthServiceTest` (or equivalent): `loginAsSystemAdmin` rejects
  `BRANCH_ADMIN`/`USER` accounts and withdrawn `ADMIN` accounts.
- Withdraw flow: withdrawing a user, then attempting Google/password login
  with that account, fails with `UNAUTHORIZED`.
- `system-admin/users` and `system-admin/reservations` endpoints: 403 for
  non-`ADMIN` callers (including `BRANCH_ADMIN`), 200 for `ADMIN`.
- Reservation list returns rows from multiple branches with correct branch
  names attached.
