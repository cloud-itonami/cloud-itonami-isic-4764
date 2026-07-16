# cloud-itonami-isic-4764

Open Business Blueprint for **ISIC Rev.5 4764**: retail sale of games and
toys in specialized stores.

This repository publishes a specialized games/toys-retail
operations-COORDINATION actor -- sales/inventory/return transaction
logging, floor-staff scheduling, toy/game merchandise supply-order
coordination with registered vendors, and product-safety-concern
flagging -- as an OSS business that any qualified operator can fork,
deploy, run, improve and sell, so an independent games/toys store never
surrenders its operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **ToyGameRetailAdvisor
⊣ ToyGameRetailGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:toy-game-retail-governor`, is a
distinct, independent build (no naming-collision precedent question --
distinct from ISIC 4719's own `:merchandise-retail-governor` and ISIC
4711's own `:retail-governor`).

> **Why an actor layer at all?** An LLM is great at drafting a sales-
> record summary, a staffing proposal, or a supply-order request -- but
> it has no license to actually finalize a product-recall-compliance
> decision or an age-grading-safety decision against a child product, no
> way to independently confirm a store or a supply-order vendor is
> actually a registered/verified counterparty, and no notion of when a
> "flag this concern" op quietly turns into a claim to have already
> adjudicated it. Letting it act directly invites an unverified store's
> data entering the ledger, an unverified import broker receiving a toy
> order, or -- worst of all -- a fabricated claim to have cleared a
> recall or certified an age-grading label without a human ever looking
> at the specific SKU, exposing children to real harm and the shop to
> real liability. This project seals the ToyGameRetailAdvisor into a
> single node and wraps it with an independent
> **ToyGameRetailGovernor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: coordination only, never a recall or age-grading authority

This actor is **operations coordination only**. It never performs or
authorizes:

- setting or overriding a shelf/unit price
- directly finalizing a product-recall-compliance decision (declaring a
  SKU recall-cleared, initiating or executing a recall, closing out a
  recall case)
- directly finalizing an age-grading-safety decision (certifying or
  approving an age-grade/age-appropriate-use label as the OFFICIAL
  determination)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a safety concern
for a human to triage is exactly this actor's job --
`:flag-safety-concern` is never excluded by this check, only
FINALIZING/certifying/executing a recall or age-grading decision is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`toygameops.governor`'s `effect-not-propose-violations` HARD check and
`toygameops.phase`'s phase table, which never puts `:flag-safety-concern`
in any phase's `:auto` set, at any phase, including a hypothetical future
phase 4). A human store operator/product-safety coordinator is always
the one who actually acts on a flagged concern or confirms a high-cost
supply order.

## The core contract

```
store/vendor registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ ToyGameRetail-         │ ─────────────▶ │ ToyGameRetailGovernor       │  (independent system)
   │ Advisor (sealed)       │  + citations    │ store-unverified ·          │
   └───────────────────────┘                 │ vendor-unverified ·         │
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (recall-compliance / │
    record + ledger        escalate ┼ age-grading-safety finalization) · │
          │              (ALWAYS for│ op-not-allowed                      │
          │        :flag-safety-    │                                      │
          │        concern/high-cost└────────────────────────────┘
          │        supply-order)
          ▼
      human approval
```

**The ToyGameRetailAdvisor never commits a proposal the
ToyGameRetailGovernor would reject, and a product-safety-concern flag or
a high-cost supply order never commits without a human sign-off.** Hard
violations (an unregistered/unverified store; an unregistered/unverified
supply-order vendor; a non-`:propose` effect; content touching
recall-compliance or age-grading-safety finalization; an op outside the
closed allowlist) force **hold** and *cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: shelfing, picking, restocking,
point-of-sale handling) under human/robot floor operations gated by store
policy. This actor itself does not dispatch robot/hardware actions -- it
is strictly the operations-coordination layer (sales-record logging,
staffing scheduling, supply-order coordination, product-safety-concern
flagging) any physical-dispatch layer could eventually feed proposals
into, always gated the same way by the independent ToyGameRetailGovernor.

## Features

- **Closed proposal-op allowlist**: `log-sales-record`,
  `schedule-staffing-operation`, `coordinate-supply-order`,
  `flag-safety-concern` (all `:effect :propose`). No op in this
  allowlist directly finalizes a recall-compliance or age-grading-safety
  decision -- structurally, not as a rollout gap.
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Store unverified** -- the target store's business registration
     must exist AND be independently registered/verified in the store.
  2. **Vendor unverified** -- for `:coordinate-supply-order` only, the
     named vendor must exist AND be independently registered/verified --
     a supply-chain counterparty-verification gate that matters
     especially in this vertical, since an unverified import broker is
     exactly the channel through which unsafe or mislabeled toy/game
     stock enters a store.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a recall-compliance
     decision or an age-grading-safety decision, and an op outside the
     closed allowlist, are both permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-safety-concern` -- ALWAYS escalates, regardless of confidence
    or phase. A "flag a concern" op is never auto-commit eligible and
    never finalizes a recall-compliance or age-grading-safety decision
    itself -- it only surfaces the concern for a human. This is the
    CHILD-PRODUCT-SAFETY op in this vertical (choking/small-parts
    hazards, banned substances, suspected recall-affected stock,
    age-grading label mismatches).
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: sales-record logging only (approval-gated)
  - Phase 2: + staffing-operation scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (safety concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/toygameops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/toygameops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/toygameops/phase_test.clj` -- rollout phase logic
- `test/toygameops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/toygameops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `toygameops.store` -- SSoT (MemStore, String-keyed store/vendor
  directories, append-only ledger)
- `toygameops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `toygameops.governor` -- independent compliance layer
- `toygameops.phase` -- staged rollout (0→3)
- `toygameops.operation` -- langgraph-clj StateGraph
- `toygameops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4764`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Sales/inventory/return transaction logging (`:log-sales-record`) | Real POS/inventory-system integration |
| Floor-staff scheduling coordination (`:schedule-staffing-operation`) | Direct staff time-clock/payroll integration |
| Toy/game merchandise supply-order coordination with a registered, verified vendor, HARD-gated on vendor verification (`:coordinate-supply-order`) | Real supplier-ordering-system integration |
| Product-safety-concern flagging (choking/small-parts hazards, suspected recall-affected stock, age-grading label mismatches), ALWAYS human-gated (`:flag-safety-concern`) | Directly finalizing any recall-compliance or age-grading-safety decision -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Real regulatory recall-registry integration / CPSC-style reporting workflow |

Extending coverage is additive: add the next op (e.g. a return-
authorization or a shrinkage-escalation check) as its own governed op
with its own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any
real-world act" pattern this repo's flagship checks already establish.

## Maturity

`:implemented` -- `ToyGameRetailAdvisor` + `ToyGameRetailGovernor` run
as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own
child-product-safety scope-exclusion discipline.

## License

Code and implementation templates are AGPL-3.0-or-later.
