# Return policies, eligibility and refund calculation

Nothing about a tenant's rules is hard-coded. A **return policy** is validated *data*; the engine that applies it is code. If a tenant has published no policy, the platform defaults below apply — they are defaults, not tenant rules, and any tenant can override them per store.

## Policy document

Stored as JSON in `return_policy_versions.config`. Unknown fields are dropped and every value is range-checked when a draft is saved (no expression language, no free-form code).

| Field | Meaning | Default |
|---|---|---|
| `returnWindowDays` | days from delivery (from order date if no delivery time) | 30 |
| `requireDelivered` | order must be delivered before a return | true |
| `allowPartial` | return some units of a line / some lines | true |
| `maxQuantityPercent` | cap on units returned as % of purchased | 100 |
| `nonReturnableProducts` / `nonReturnableCategories` / `finalSaleProducts` | id lists that can never be returned | empty |
| `categoryWindowDays` / `productWindowDays` | per-category / per-product window overrides | empty |
| `usedReturnable`, `damagedReturnable` | whether goods reported USED / DAMAGED can be returned | false / false |
| `reasons[]` | `{code, label, evidenceRequired, sellerFault}` — the only reasons accepted | retail: DEFECTIVE, WRONG_ITEM, DAMAGED_ON_ARRIVAL (evidence + seller fault), NOT_AS_DESCRIBED, NO_LONGER_NEEDED, SIZE_FIT, OTHER · claims: ORDER_REJECTED, RESTAURANT_UNAVAILABLE, INCORRECT_ORDER, MISSING_ITEMS, DAMAGED_FOOD, LATE_DELIVERY, PAYMENT_NOT_ACCEPTED |
| `inspectionRequired` | goods must be inspected before a refund | true |
| `autoApproveMaxAmount` | requests at or below this are approved automatically (never when evidence is required or a manual-review threshold applies); blank = never | blank |
| `manualApprovalMinAmount` | requests at or above this always need a person | blank |
| `deliveryFeeTreatment` | `NEVER`, `FULL_RETURN_ONLY`, `ALWAYS` | FULL_RETURN_ONLY |
| `restockingFeePercent` | 0–50, waived for seller-fault reasons | 0 |
| `storeCreditAllowed`, `exchangeAllowed`, `exchangePriceDifferenceAllowed` | resolutions available | false / true / true |
| `claimWindowHours` | restaurant claims: hours after delivery | 48 |

Scope: a policy belongs to (tenant, store or `*`, kind). The store's policy wins over the tenant-wide `*` policy, which wins over the defaults.

## Eligibility (computed on the server, `EligibilityEngine`)

For each requested line it checks: the item belongs to the order; quantity ≤ purchased − already requested (cancelled/rejected requests free their units); product/category not non-returnable or final sale; within the (most specific) window; condition rules; `maxQuantityPercent`; `allowPartial`; the reason exists in the policy. It returns the blockers (HTTP 422 with the reasons), whether evidence is required, whether the reason is seller fault, an estimated amount, and whether the request can be auto-approved. The same function serves customers and operators; the UI never decides eligibility.

## Refund calculation (`RefundCalculator`)

`itemsAmount` = Σ unit price × accepted quantity. Then, proportionally to the returned share of the order's items:

* **discount allocation** is subtracted (a coupon is not refunded twice);
* **tax adjustment** is added back proportionally;
* **delivery fee** is added once per order per `deliveryFeeTreatment`: `ALWAYS` refunds it; `FULL_RETURN_ONLY` refunds it when every unit is returned *or* the reason is seller fault (restaurant claims count as seller fault); `NEVER` never does;
* **restocking fee** is subtracted unless the reason is seller fault.

`finalAmount` is capped at *order total − money already refunded or committed to refund* on that order, so combined refunds can never exceed what the customer paid. Rounding is half-up to 2 decimals; allocations are exact (no lost cents). Refunds can go to the original payment method or store credit (if allowed).

## Lifecycle of a policy

`DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED`.

* Only a draft is editable; saving after publication starts a new draft version.
* **Second-person review**: whoever submitted a version cannot approve it (`403`); sending it back needs a reason.
* Publishing archives the previous version. **Rollback** republishes an archived version as a *new* version.
* **Published/archived content is immutable and cases keep the version they were decided under.** Each return stores `policy_version_id` and the decision text at request time; approval, partial approval, inspection and refund of that case use that version. A policy change therefore never silently changes an approved case.
* Every step is in the audit history.

## Risk and exception flags

Informational only — a flag never blocks, rejects or labels anyone. Raised for: another open request on the same item (`DUPLICATE_ATTEMPT`), repeated requests in a window (`REPEAT_RETURNER`), high value, inspection mismatch / missing items, failed pickups beyond the limit, lost shipments, refund failures, a refund confirmation whose amount differs, refunds at/above the high-value threshold. Thresholds are per tenant in *Refund Configuration*. Each signal appears once while open (idempotent) and is worked in *Return Exceptions* (assign, escalate, resolve/dismiss with a note).
