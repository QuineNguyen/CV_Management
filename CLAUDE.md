# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository status

This repo currently contains **specification documents only** — there is no source code, build system, dependency manifest, or test suite yet. `docs/` is the entire repository, and it is not a git repository.

Do not invent build/test/lint commands. When implementation starts, the stack below is the one the specs commit to, and this file should be updated with real commands at that point.

## Documentation sources

| File | Content |
|---|---|
| `docs/01_MoTaMucDichHeThong.md` | System purpose, scope (in/out), tech stack, roles |
| `docs/02_MoTaHoatDongNghiepVu.md` | Full business-flow spec — 11 sections, actor + flow per use case |
| `docs/03_MoHinhDoiTuongQuanHe.md` | Object/entity model derived from `02` — 22 entities with attributes, R01…R39 relationships with cardinality, unique + delete constraints |
| `docs/Technical document.docx` / `.pdf` | Combined spec (same material, most recently edited) |

All docs are in Vietnamese. The `.pdf`/`.docx` are the consolidated source authored by the user; the two `.md` files are the working spec and have been **reconciled against the PDF**.

The `.md` files are now the authoritative pair. Contradictions found in the PDF were resolved and recorded as decisions **[QĐ-01] … [QĐ-31]** in section 8 of `01_MoTaMucDichHeThong.md` — that table is the place to look when the PDF and the `.md` files disagree, and it explains why. **No open questions remain**: section 13 of `02_MoTaHoatDongNghiepVu.md` now only maps each late decision ([QĐ-24]…[QĐ-31]) to the sections that implement it. The spec is settled enough to design the data model from.

`02_MoTaHoatDongNghiepVu.md` also carries three sections written specifically for data modelling: **§0.2** (seven separated state machines), **§0.3** (CV content structure), and **§12** (business rules BR-01…BR-63). `03_MoHinhDoiTuongQuanHe.md` is the entity/relationship layer built on top of those three sections — go there first when asking "what objects exist and how do they relate", and treat §8 of that file (19 common modelling mistakes) as the review checklist before schema work. **§11 of that file** records a field-by-field audit (`[QĐ-38]`…`[QĐ-42]`): which columns were dropped for having no reader, which were kept and why, and the two relationships that had no column implementing them. Read it before adding a column that "might be useful later".

## Planned stack

- **Frontend**: AngularJS SPA
- **Backend**: Spring Boot (Java), RESTful API
- **DB**: MariaDB — CV content is stored in **JSON columns**; full-text search over CV content
- **Auth**: JWT + Spring Security, plus Google OAuth2
- **Email**: Gmail SMTP via JavaMail, sent with `@Async`
- **Scheduling**: Spring `@Scheduled` for the daily reminder cronjob
- **Diff**: `java-diff-utils` for CV version comparison
- **File storage**: MinIO (S3-compatible) for CV avatars — an `image_files` row holds the object key and `users` / `cv_versions` / `cv_drafts` point at it via an `avatar_image_id` **foreign key** (`ON DELETE RESTRICT` from versions, so "don't delete an image a published version still uses" is enforced by the DB). The object key never appears inside CV content JSON; the UI renders via a backend-issued **presigned URL**; never base64-embed images in CV content

Explicitly out of scope: SSO/LDAP, AI content suggestions, native mobile, Slack/Teams, LinkedIn import.

## Domain model — the parts that span multiple documents

### Three orthogonal axes — do not collapse them

A CV is identified by **(employee × profile × language)**. Three separate axes, and conflating any two of them is the classic modelling mistake here:

- **Version** — same CV, changing over time (v1 → v2 → v3).
- **Language** — same content, expressed in vi/en/ja.
- **Profile** (`Hồ sơ năng lực`, `[QĐ-32]`) — same person, *different professional persona*: "Software Developer" (projects A, B, C) vs "AI Engineer" (projects D, E). Different job title, career objective, skills, project set.

Profiles exist because one person can be proposed to customers in two roles. Never model that as a new version (version history would mix "newer" with "for a different role", destroying diff/rollback semantics) and never as a language.

Rules that follow:
- Every employee has ≥1 profile with exactly **one primary** (`is_primary`); the first profile is auto-created, so single-persona employees see no difference. Profile names are unique per employee.
- **Master/localization coupling lives entirely inside one profile** — the en CV of "AI Engineer" syncs with that profile's own master, never across profiles. Item IDs are not shared across profiles either, even when one profile was copied from another.
- A new profile's CV goes through the **full 2-level approval** even when copied from an already-approved profile — a Tech Lead must vouch for the new persona separately — and its version numbering **restarts at v1**.
- A profile may declare a **linked team**, which takes priority when picking the level-1 Tech Lead (an "AI Engineer" CV should be reviewed by the AI project's lead, not the web project's).
- Update Requests target **exactly one profile** (default: primary); the duplicate guard keys on **(employee, profile, language)**. Batch requests always target the primary profile.
- The primary profile cannot be deleted (reassign primary first); deleting a profile soft-deletes all its CVs.

### CV is versioned, never overwritten

A CV is not a row you update. Every accepted change creates a **new version** (v1 → v2 → …; version number is a plain incrementing integer, no minor part), and no version is ever deleted. The current version is **derived, not stored** — it is simply the row with the highest `version_number` for that CV, which holds because numbering only increases, nothing is deleted, and rollback appends a new version rather than restoring an old row. Do not add a `current_version_id` pointer or an `is_current` flag; the latter would also force an UPDATE on an immutable table. Each version stores a **full JSON snapshot** of the content, never a delta — at ~50 employees the storage cost is irrelevant and rollback/diff/PDF export all read one row. Rollback does *not* restore an old row — it **creates a new version** whose content is copied from the chosen one, preserving the intervening history.

**Only the CV owner ever writes CV content** (`[QĐ-35]`). Admin/HR do not create or edit other people's CVs at all — there is no "HR fills in the CV for the employee" path:
- **Employee editing their own CV** → **Draft** → full 2-level approval. The published version stays live and usable throughout the cycle; only HR's final approval materializes the new version.
- **Owner is Admin/HR editing their own CV** → new version published immediately, no approval (`[QĐ-06]`).
- **Rollback** publishes directly too. There is **no exception at all** — no initial-import mode, no go-live path: Admin/HR never create or edit another employee's CV, and `cv_versions.source` has exactly three values (`APPROVAL` / `DIRECT_EDIT` / `ROLLBACK`), with `DIRECT_EDIT` valid only when the editor *is* the owner (BR-51).

Admin/HR influence someone's CV **only** through an Update Request carrying a reason plus optional **notes anchored to `(section, item, field)`** (spec §5.1) — stored as JSON on the request, one-way, no reply thread (threaded discussion belongs to review-round inline comments). The reason authoring was taken away: HR's level-2 role is *format and spelling only* (spec §6.4), so HR is by the spec's own definition not a technical reviewer, and letting HR author technical content produced versions **nobody vouched for technically**, published in the employee's name.

Because owner = author = submitter always, `cv_drafts` needs just `owner_id`, and BR-15's "approver ≠ submitter" automatically also means "approver ≠ CV owner".

A version is flagged **not technically reviewed** in exactly **one** case: `LEVEL1_NO_CANDIDATE` — no active Tech Lead available, which in practice narrows to "the employee belongs to no team", since every team must always have an `ACTIVE` Tech Lead (BR-04, BR-56). It is **not** flagged when the owner is Admin/HR editing their own CV, nor when level 1 is skipped because the owner *is* the Tech Lead — an owner is competent to vouch for their own CV (`[QĐ-35]`). A flagged version warns on screen and before PDF export (never watermarked into the file itself — that goes to customers) and appears in an Admin/HR dashboard widget. The flag clears only when a later version goes through level 1 for real. A rollback **inherits** the source version's flag.

### Approval state machine

```
Draft ──submit──▶ Pending_Tech_Lead ──approve──▶ Pending_HR ──approve──▶ published (new version)
   ▲                     │                            │
   └──── Rejected ◀──────┴────────reject──────────────┘
```

Key rules that are easy to get wrong:
- Re-submitting a rejected draft restarts at **Pending_Tech_Lead**, never resuming at the HR step — Tech Lead reviews again from scratch.
- A CV created by an **Employee** is also a Draft and needs both approvals before v1 exists; only an owner who is Admin/HR publishes v1 directly (`[QĐ-35]`). A CV has **at most one open Draft** at a time.
- If no eligible approver remains at a level (the submitter is the only candidate), that level is **skipped** with a recorded reason — this is how a Tech Lead's or HR's own CV gets through. Note level 2 prefers *another* active HR and only skips when there is none.
- The approval matrix is **fixed company-wide**; it is not configurable per department. Don't build a rules engine for it.
- Each pending draft is **assigned to exactly one approver per level** (`[QĐ-23]`, spec §6.2). Only the assignee sees it in the approval queue, can open it, comment, or approve/reject — this is what prevents two Tech Leads (or two HRs) from deciding the same CV concurrently. Level 1 picks among the Tech Leads of the teams the employee belongs to (primary team first, then fewest open assignments); level 2 picks any active HR by fewest open assignments. Re-submits after a rejection go back to the **same** approver when still available. **Only Admin** can reassign, at both levels, with a reason (`[QĐ-37]`) — HR lost the level-2 reassign right because every HR is a peer at level 2 ("any active HR"), so an HR reassigning is a peer taking a colleague's work or offloading their own with nobody vouching for it, and it would turn the SLA into something you escape by reassigning. HR still gets the overdue notification and asks Admin. On top of assignment, every draft state transition must be a compare-and-set on `(expected state, current assignee)` — assignment removes the contention, the CAS is the last line of defence against double-clicks and mid-flight reassignment.
- A CV in a pending-approval state **cannot be deleted** — the approval must be cancelled first, via **Cancel Draft** (spec §6.6, `[QĐ-39]`). Cancelling is a state change, never a row delete: the draft goes to `CANCELLED`, the open assignment closes as `CANCELLED` (*not* `COMPLETED` — nobody decided anything), open inline comments flip to `RESOLVED`, and the published version and any linked Update Request are untouched. **Only Admin** can cancel a draft that is already in a pending state; the owner can only cancel their own `DRAFT`/`REJECTED` — letting a submitter withdraw mid-review would reopen the SLA escape hatch `[QĐ-37]` just closed. A cancellation reason is required **only on the Admin path**, because it is the body of the notification sent to the owner and the assignee; the owner dropping their own unseen draft is never asked for one.
- Submit-time validation requires non-empty Personal Info, Skills, and Experience sections.
- Rejections carry both an overall reason and **inline comments anchored to specific CV lines/sections**; employees can reply to individual comments. Comment threads are part of the draft, not the published version. Each comment belongs to **one review round**: on re-submit, the previous round's comments flip to `RESOLVED` — they are kept and stay readable as history, never deleted.
- Each assignment carries a **3-day SLA** (configurable). Past it the cronjob only *reminds* the assignee and notifies HR — the system never auto-reassigns, since bouncing a CV between reviewers loses the review context. Reassignment stays a manual **Admin-only** action (`[QĐ-37]`). Deactivating a user is the one path that moves assignments automatically (level 1 → the Admin-named replacement Tech Lead, level 2 → another active HR) — that is a data constraint, not an HR decision.
- HR's approval also flips any linked Update Request to `Completed`.

### RBAC — role determines *data scope*, not just menu access

Every CV/request list query must be scoped by role, not filtered in the UI:

| Role | Scope |
|---|---|
| Admin | Everything; user & department management |
| HR | All CVs company-wide; creates update requests; format/spelling approval |
| Tech Lead | CVs of employees **in the teams/projects they lead** |
| Employee | Only their own CV; only requests addressed to them |

An employee has **one primary department** but **many teams/projects**, so Tech Lead scope resolves through team membership (many-to-many), not through the department tree. Role changes take effect on the **next request**. Team membership is **not time-versioned** — it always reflects the present. A member moving teams therefore never rewrites an in-flight approval: the current assignment stands, and the new team only matters from the next review round, which is why each assignment record stores the *reason it was chosen*.

Departments are a **self-referencing tree** (parent department) covering both functional departments (KTCN, QA, HR) and business units (BU1–BU5). A department with employees cannot be deleted; deleting a team only unlinks members.

### Multi-language CVs are structurally coupled

Localized CVs are not independent documents. Within a single profile, the first-created language CV is the **Master**; others link via `master_cv_id` and **inherit its structure**:
- Adding a section/entry to the Master auto-creates an empty placeholder in every other language.
- Deleting from the Master **flags** the counterpart elsewhere (prompts the user) rather than deleting it.
- Reordering in the Master propagates ordering everywhere.
- Personal-info fields are copied automatically; all other content starts as untranslated placeholders.

Languages: `vi` (default), `en`, `ja`. The standard template has 9 fixed sections (Personal info, Career objective, Skills, Experience, Education, Certifications, Projects, Languages, Additional info).

### Async email + reminder escalation

Email is always sent via `@Async` — never on the request thread. Failures retry **3× at 5-minute intervals**, with per-message status logged as `Pending / Sent / Failed`.

Batch update requests create **1 Batch Request + N Update Requests**, processed by a background worker that reports progress ("15/50 sent") to the UI.

An Update Request keys on **(employee, profile, language)** and its CV reference is **nullable** — requesting an update from someone who has no CV in that language is valid, and the system does *not* auto-create an empty CV. When they later create that CV, the pending request is linked to it automatically; it only reaches `Completed` when a new version is published.

A daily cronjob (default 09:00, configurable by Admin/HR, and disableable) scans pending requests and escalates by deadline proximity:

| Time to deadline | Action |
|---|---|
| > 3 days | nothing |
| 1–3 days | email employee |
| < 1 day | email employee, high priority |
| overdue | email employee, highest priority |

Update-request reminders go to the **CV owner only**, at every escalation level (`[QĐ-44]`) — HR is never mailed per request. HR cannot update someone else's CV (`[QĐ-35]`), so the mail carries no action for them, and the "how many are overdue" question is already answered by the Dashboard's *Sắp quá hạn / Đã quá hạn* widgets, which are correct whenever opened rather than once per cron run.

Cancelling a request stops reminders but **does not delete the employee's draft**.

The same cron run also scans overdue **approval assignments**, and that produces **two different notifications** (`[QĐ-43]`): a per-assignment **personal reminder** to the assignee only, and **one daily digest** to **Admin + every active HR** listing every overdue assignment at once. Admin is in the list because Admin is the only role that can reassign (`[QĐ-37]`) — warning HR alone told the people who can't act. Never send the supervisory warning per assignment: cron × days overdue × recipients multiplies fast. Consequence for the schema: `reminder_logs`' dedup key is `(target_type, target_id, recipient_id, sent_date)` — the digest dedups **per recipient**, not per assignment, and its `target_id` points at the recipient (NOT NULL, because a NULL in a MariaDB unique index does not block duplicates). Every non-digest reminder row now has exactly one recipient.

The rule behind both `[QĐ-43]` and `[QĐ-44]`, to apply to any new notification: **mail only the people who can act on it**. A supervisory "so you know" need belongs on the Dashboard, or in one daily digest if it genuinely has to be pushed — never as one email per object per day.

### Google OAuth2 never auto-provisions

Google login matches on email against an existing Admin-created account. No match → login is refused with a "contact Admin" message; a new account is **never** created. Roles always come from the internal system, never from Google. Inactive accounts are rejected on the Google path too.

## Conventions

- Spec documents and all user-facing strings are Vietnamese; keep new documentation in Vietnamese to match.
- Statuses are split across seven independent state machines — see `docs/02_MoTaHoatDongNghiepVu.md` §0.2. Do not reintroduce a single overloaded "CV status": update-compliance (`Đã/Chưa cập nhật`) is **derived** from whether a `PENDING` update request exists, and `Hủy yêu cầu` was removed as a CV status because cancellation belongs to the request.
- Target scale is ~50 employees — prefer straightforward implementations over infrastructure built for scale.
