# Working TODO - FR-09 / FR-22 closure: addon + plan-change orders end to end (2026-07-20)

(Previous working TODO - the Sprint-14-style full E2E re-test - completed 2026-07-18; its results
live in `docs/tasks/sprint-14-testing-and-hardening/14.6-post-sprint21-e2e-retest.md`.)

Design decision: NEW_LINE keeps the paid saga; PLAN_CHANGE and ADDON orders are unpaid at order
time and bill on the next monthly invoice - this is what makes FR-22's addon/VAS invoice lines
correct rather than a double charge. Payment-service ignores non-NEW_LINE orders.

- [x] Avro: `subscription.tariff-changed.v1` + `subscription.addon-attached.v1` (avsc, pom subjects, event-catalog rows)
- [x] product-catalog-service: `GET /internal/addons/{code}` + public `GET /api/v1/addons/{code}`
- [x] order-service: `OrderType`, `subscription_id` on orders, `addon_code` on order_items (V8), DTO/command plumbing with legacy overloads, handler branches, order.created.v1 payload evolution
- [x] order-service: `SubscriptionProvisionedEventConsumer` fulfils on the two new subscription events
- [x] payment-service: OrderCreatedEventConsumer skips orderType != NEW_LINE
- [x] subscription-service: `OrderCreatedProvisioningConsumer`, `Subscription.changeTariff`, `subscription_addons` (V3), both events published via outbox
- [x] billing-service: both consumers, `addon_charges` (V3), ADDON/VAS line types, bill-run addon lines + billed flag (+ fixed the line-type-erasing invoice rebuild loop)
- [x] tests: all five touched module suites - zero assertion failures; only the documented Docker-gated Testcontainers classes error; SubscriptionEventSchemaCompatTest 6/6 covers the new payloads
- [x] docs: STATUS.md entry + artifact update
- Residual (not this pass): live Kafka round trip, dedicated unit tests for the new handlers (qa), usage-side quota grant for addons, web frontend support for the new order types
