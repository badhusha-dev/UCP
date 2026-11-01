# Performance Report

**No load or performance test was run in this session.** Tooling exists under `devops/load-testing/` but no results were
produced or reviewed here, so no throughput/latency figure is claimed.

Evidence-based observations
- Concurrency correctness (not throughput) is covered by earlier module tests: 25-way coupon redemption, 8-way order
  hold, 10-way allocation, 5-way bulk execute (see module VERIFICATION_REPORTs).
- **Blocking Kafka sends**: cart- and loyalty-service publish events synchronously with the
  default `max.block.ms` (60 s). With the broker down, every request that publishes an event waits up to 60 s
  (observed: the cart test run stalled for over 10 minutes and loyalty stalled the same way). Tests now use 500 ms and the docker profile 5 s (config-repo, not redeployed); see KNOWN_ISSUES K5.
- Inventory reservation was read-modify-write without locking (sibling notes) — oversell risk under load; sibling session
  is replacing it with a conditional UPDATE.

Next step (Phase 15): run the k6/JMeter scripts in `devops/load-testing` against the compose stack and record p95/p99.
