# Production-readiness checklist

The design checklist for a 45-minute exercise. It is deliberately short: one pass takes two minutes and
every line is something you can point at in the code or name as a next step. The last column shows the
AWS Well-Architected pillar each line descends from, so you can cite the framework when asked "how would
this go to production", without reciting it.

| # | Check | What I do in the exercise | Pillar |
|---|---|---|---|
| 1 | Correct on the stated cases | Tests from the statement before the implementation; run them, read them | Reliability |
| 2 | Robust at the boundary | Null, empty, invalid type or format, duplicates, out-of-order, boundary values; reject with a reason or fail fast with a clear exception | Reliability |
| 3 | Sensitive data handled | No patient identifiers or contact details in logs or exceptions; minimum fields kept; mention encryption and access control for storage | Security |
| 4 | Observable | One summary log line with counts and timings; where a metric and a correlation id would go | Operational excellence |
| 5 | Performs at scale | Name the complexity; sort or index instead of nested loops; stream large inputs; say what breaks at ten million records | Performance efficiency |
| 6 | Tested in layers | Unit tests here; integration and contract tests for the API; adversarial inputs; coverage as a signal, not a goal | Reliability |
| 7 | Operable | Runs from the command line and from CI; configuration (thresholds, limits) injected, not hard-coded | Operational excellence |
| 8 | Evolvable | Small public surface, immutable records, clear names, Javadoc on the public method | Operational excellence |
| 9 | Cost aware | Nothing retained that is not needed; batch or stream instead of loading everything when input is unbounded | Cost optimization |
| 10 | Ships safely | CI stages: build, unit tests, static analysis and SonarQube quality gate, dependency scan, then deploy behind a flag with rollback | Operational excellence |

## Two-minute closing walk-through

"Correctness is covered by the tests you saw. Robustness: nulls, empties and bad formats are rejected with
reasons, not exceptions, except a null collection which fails fast. Sensitive data: nothing identifying is
logged. Observability: one summary line now, a counter and a histogram in production. Scale: O(n log n),
streaming input, and I would move the percentile to a histogram library at volume. Testing: unit now,
contract and integration tests at the API boundary next. Shipping: build, tests, Sonar quality gate,
dependency scan, deploy behind a feature flag."
