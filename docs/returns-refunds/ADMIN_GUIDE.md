# Returns & Refunds — administrator guide

Open **Operations → Returns & Refunds** in the E-commerce Admin. You see only the screens your permissions allow; ask a tenant administrator for one of these roles: **Returns Manager** (everything), **Returns Agent** (review, approve/reject, pickup, receive, exchange), **Warehouse Inspector** (receive, inspect), **Refund Approver** (refunds above the threshold, audit, analytics), **Returns Analyst** (view, analytics, audit, export). Store access follows your token (or *view all stores*).

## The 20 screens

| Screen | Use it for |
|---|---|
| Returns Dashboard | live counts (pending, approved, rejected, awaiting pickup, in transit, awaiting inspection, refunds pending/completed), total refunded, return rate, average processing/refund time, top reasons, products returned most. Filter by date, store, vendor, type. |
| All Return Requests | search RMA number, order number, customer; filter by status, type, resolution, store, dates, “flagged for review”; export CSV (permission). |
| New Requests · Pending Approval · Approved · Rejected · Pickup Scheduled · In Transit · Received · Inspection Pending · Inspection Completed · Refund Pending · Refund Processing · Refund Completed | work queues: the same list limited to one stage. |
| Exchange Requests | proposed/approved/fulfilled exchanges and their replacement orders. |
| Return Exceptions | review items (duplicates, mismatches, failed pickups, lost parcels, refund failures…). |
| Return Policies | create, edit, submit, review, publish, roll back policies. |
| Refund Configuration | thresholds (high value, second-approver amount, repeat-return limits, pickup/lost limits, SLAs). |
| Return Analytics | breakdowns by reason, status, store, day; refunds; inspections; exchanges. |
| Return Audit History | who did what and when, across returns, refunds, policies and settings. |

## Handling a request (the return workspace)

Click an RMA number. The header shows the stage and any review flags; the buttons shown are the actions you may take *now*.

1. **Review** — the Overview tab shows the order, customer (masked unless you may see customer details), the reason, the policy decision recorded at request time and any required evidence. *Request information* asks the customer for more (e.g. a photo). If the policy requires evidence for the reason, **Approve is not offered until a file is attached** (Evidence tab).
2. **Approve** — set the quantity per item; fewer than requested is a *partial approval*. **Reject** needs a reason (the customer is told). Internal notes are never shown to customers.
3. **Pickup** (retail only) — *Schedule pickup* with a window; Delivery reports back and the stages *Picked up / In transit / Received* update by themselves. A failed pickup returns the case to *Pickup failed* — reschedule. Customer drop-off: use *Mark received*. Restaurant claims have no pickup.
4. **Inspection** — *Send to inspection*, then *Record inspection*: per item the received and accepted quantity, condition and whether it goes back to stock; packaging, accessories, missing items, damage, notes; the outcome. Only accepted units are refunded. Add inspection photos on the Evidence tab.
5. **Refund** — *Prepare/Calculate refund*: the amount is computed (items, discount share, tax, delivery fee, fees) and cannot be typed. On the **Refund** tab: *Approve refund* (a second person is required above the approval threshold), then *Send to Payment*. The refund stays **Processing** until Payment confirms; only then does it show **Completed** and the return **Refunded**. If Payment reports a failure the refund shows **Failed** — *Retry refund* creates a new attempt (no double refund is possible).
6. **Exchange** — for exchange requests: *Propose exchange* (replacement product, variant/size, quantity). Price and stock are checked; the price difference is shown (customer pays / is refunded). Approve it, wait until the goods are accepted, then *Create replacement*. The replacement is an order: if the customer owes a difference it cannot be processed until Payment confirms it; if it costs less, a price-difference refund is created. When the replacement ships the return becomes *Exchanged*.
7. **Close** — closes a finished or abandoned case (reason required).

What you cannot do by hand: mark a refund completed, mark a parcel picked up/in transit, or skip stages. These come only from Payment and Delivery.

## Policies

*Return Policies → New policy* starts a draft from the platform defaults. Edit the window, reasons (add/remove, evidence required, seller fault), thresholds and switches; **Save draft**; **Submit for review**. A *different* person approves (or sends back with a reason) and publishes. Published versions can be rolled back (as a new version). Existing cases keep the version they were decided under — see [RETURN_POLICY.md](RETURN_POLICY.md).

## Good practice

* Treat review flags as questions, not verdicts.
* Use the audit history to answer “who approved this refund?”.
* Keep the evidence volume (`return_evidence_data`) in your backups.
* Amounts have no currency symbol: orders do not store a currency code.
