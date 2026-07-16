# Contributing

`cloud-itonami-isic-4764` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real customer, employee, supplier or product-safety-
  incident data.
- Keep sales-record logging, staffing-operation scheduling, supply-order
  coordination and safety-concern flagging behind the
  ToyGameRetailGovernor.
- Treat toy/game-store operations workflows as high-risk: add tests for
  store/vendor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never add an op to the closed allowlist that directly finalizes a
  recall-compliance or age-grading-safety decision. `:flag-safety-
  concern` may only surface a concern for a human; it must always
  escalate and must never be added to any phase's `:auto` set.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "recall", "choking hazard", "age grade") -- phrase it as the
  finalization/execution ACTION (e.g. "finalize the recall", "certify
  the age grade"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-safety-concern` happy path -- see
  `toygameops.governor/scope-excluded-terms`'s docstring.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
