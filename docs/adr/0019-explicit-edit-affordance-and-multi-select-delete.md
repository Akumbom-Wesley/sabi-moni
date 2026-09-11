# ADR-0019: Explicit edit affordance, and delete as a multi-select

- **Status:** Accepted
- **Date:** 2026-09-10
- **Supersedes:** the tap-target and delete-affordance decisions in
  [ADR-0018](0018-corrections-and-manual-entry.md). Everything else in ADR-0018 — the
  five-field UPDATE, no re-parse after a correction, manual entries as thread items, the
  category rule, the retry guard, what "today" means — stands unchanged.

## Context

ADR-0018 made the whole entry row the tap target for a correction, reading FR1.4's "each
parsed line is tappable to correct directly" literally, and put delete inside the editor so
that removing an entry took two deliberate taps.

Using it revealed two problems with that pair:

1. **A whole-row tap target has no resting state.** The thread is mostly something you
   *read* — it is the confirmation of what the app understood. Making every line of it a
   live control means any stray tap while scrolling or scanning opens a form over the
   thing you were reading. The line is a statement of record; a mis-tap on it should cost
   nothing.
2. **Delete-one-at-a-time is the wrong shape for the actual failure.** When a parse goes
   wrong it usually goes wrong in bulk — a message split into four entries when it should
   have been two, or a MoMo SMS logged alongside the typed entry for the same purchase.
   Clearing that through the editor is: open, delete, close, open, delete, close. The unit
   of the mistake is a *set* of entries, so the unit of the fix should be too.

A third, smaller problem: the date row in the editor put `2026-09-10` in a chip, which is
wide enough that three chips no longer fit the dialog and the label wrapped inside the
chip.

## Decision

### Editing is an explicit button on the row

Each entry carries a pencil `IconButton` at its trailing edge. Tapping the row itself does
nothing outside a selection.

This keeps the thread readable-by-default and puts the correction behind a target the user
aims at deliberately. FR1.4's requirement is that a line be correctable *without re-typing
the message* — an affordance on the line satisfies that as well as the line itself does,
and it is what the app's own design principle 2 ("works at low energy, at the end of a
tiring day") actually asks for: nothing destructive or modal on an accidental touch.

### Long-press starts a selection; the button gives way to a checkbox

Long-pressing any entry selects it and puts the thread in selection mode: checkboxes
replace the edit buttons, and a plain tap now toggles selection. The header swaps the
running total for a contextual bar — *"N selected"*, a clear button, a delete button —
rather than adding a bar, so the header keeps its height and the thread underneath does
not jump as the selection grows.

Long-press rather than a "Select" mode button: it is the platform idiom, it costs no
permanent screen space in the common case, and the common case is not selecting anything.

### A selection deletes in one SQL statement

`TransactionDao.deleteByIds` is a single `DELETE … WHERE id IN (:ids)`.

Not a loop over single deletes, and the reason is visible in the UI: every delete emits a
change to the day-total flow, so N deletes would make the running total count *down*
through N intermediate values on its way to the right one. One statement, one recalculation,
one new number. It is also atomic — a selection cannot half-delete.

### Bulk delete asks first; single delete in the editor does not

A selection delete opens a confirmation naming the count. This is the one place where
ADR-0018's "two deliberate taps" reasoning no longer holds on its own: a long-press plus a
tap can select several entries, and a mis-aimed delete then destroys several rows with no
undo. The confirmation says the messages will stay and the total will be recalculated.

Delete inside the editor stays unconfirmed — you are already looking at the single entry
you opened.

### The editor opens on formatted values

The amount field is pre-filled with the amount already grouped — `12 500`, not `12500`.
The form opens on what the parser understood, in the form the user reads elsewhere in the
app, and `parseMoney` already reads its own formatted output back (ADR-0018), so the round
trip needs no special case.

### Date chips carry short, single-line labels

`Today`, `Yesterday`, and either `Pick…` or a `d MMM` date — with `maxLines = 1` and
`softWrap = false`, so a label can never wrap a chip. An ISO date is the widest thing that
row could have held and the only one that did not fit.

## Alternatives rejected

- **Keep the whole row tappable *and* add an edit button.** Fewer ways to be wrong for the
  user. Rejected: two affordances for one action, and it keeps the accidental-tap problem
  that motivated the change.
- **Swipe-to-delete on a row.** Fast, familiar, and needs no selection mode. Rejected —
  it is a gesture with no visible affordance, it is exactly the kind of accident this ADR
  is trying to remove, and it still only deletes one entry at a time.
- **A persistent "Select" button in the header.** More discoverable than long-press.
  Rejected: it spends header space permanently on the rarest action on the screen, and the
  header is where the running total lives.
- **Loop single deletes for a selection.** Simpler DAO, no `IN` clause. Rejected because
  the running total is observing: the user would watch it tick down through wrong values.
- **Undo (a snackbar) instead of a confirmation.** Better in the abstract — no dialog, and
  a mistake is recoverable. Rejected for now because undo means keeping deleted rows
  somewhere, which is a data-model decision, not a UI one. Worth revisiting if delete
  turns out to be frequent.
- **Reformat the amount as the user types.** Consistent with opening formatted. Rejected:
  reformatting under a live cursor moves it, which is precisely the keyboard fight
  Sprint 2 exists to end.

## Consequences

- `TransactionRepository.deleteEntry(id)` is gone, replaced by
  `deleteEntries(ids: Collection<Long>)`. The editor's own delete calls it with one id, so
  there is one delete path rather than two.
- The thread has a mode. Selection state lives in the composable, not the ViewModel: it is
  ephemeral view state, and it is deliberately dropped when the screen is left.
- A selection is pruned if any selected entry disappears from the thread underneath it, so
  the count can never promise to delete rows that are already gone.
- Tapping an entry row outside selection mode is a no-op that still shows a ripple. Left
  as-is: the ripple is a hint that long-press does something, and suppressing it would
  cost a second code path.
- Still no undo. The confirmation is the whole safety net for a bulk delete.
