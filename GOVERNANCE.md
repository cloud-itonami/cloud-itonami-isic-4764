# Governance

`cloud-itonami-isic-4764` is an OSS open-business blueprint for
specialized games/toys retail operations coordination (ISIC Rev.5 4764 --
retail sale of games and toys in specialized stores).

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a proposal for an unverified/unregistered store, or a supply order
  naming an unverified/unregistered vendor, can never commit.
- the ToyGameRetailGovernor remains independent of the advisor.
- hard policy violations (non-`:propose` effect, recall-compliance or
  age-grading-safety finalization content, an op outside the closed
  allowlist) cannot be overridden by human approval.
- no op in the closed allowlist ever directly finalizes a recall-
  compliance or age-grading-safety decision -- `:flag-safety-concern`
  may only surface a concern for a human, never adjudicate one.
- every sales-record log, staffing-operation schedule, supply-order
  coordination and safety-concern flag is auditable.
- customer, employee and supplier data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or
license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, audit and data-flow
review.

Certified operators can lose certification for:
- bypassing sale-record, staffing, supply-order or safety-concern policy
  checks
- mishandling customer, employee or supplier data
- misrepresenting certification status
- failing to respond to a product-safety, recall or security incident
