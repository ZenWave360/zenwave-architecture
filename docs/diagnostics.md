# Workspace diagnostics

Workspace diagnostics carry a stable `code`, a `severity`, an optional canonical `semanticId`, and
source evidence. Consumers should gate CI on `ERROR` severity and use the code, rather than matching
message text. Codes are additive; changing or removing a published code is a breaking API change.

## Manifest and catalog

| Code | Severity | Meaning |
|---|---|---|
| `unresolved-static-variable` | error | Static manifest interpolation could not resolve a variable. |
| `unresolved-consumer-reference` | error | A declared consumer cannot be resolved. |
| `consumer-index-invalid-manifest` | warning | Consumer indexing received an invalid manifest. |
| `artifact-catalog-failed` | error | The artifact catalog could not be constructed. |
| `artifact-load-failed` | error or warning | A missing local artifact is an error; an unavailable remote artifact is a warning so the workspace remains queryable. |
| `artifact-analysis-failed` | warning | An artifact parser or indexer failed. |
| `manifest-diagnostic` | varies | A manifest diagnostic did not provide a more specific code. |
| `api-consumption-diagnostic` | varies | An API consumption diagnostic did not provide a more specific code. |

## Identity and graph integrity

| Code | Severity | Meaning |
|---|---|---|
| `semantic-alias-canonical-collision` | error | An alias collides with a live canonical ID. |
| `semantic-alias-duplicate` | error | The same alias redirects to multiple canonical IDs. |
| `duplicate-graph-node` | error | Contributors produced conflicting nodes with one ID. |
| `duplicate-graph-edge` | error | Contributors produced conflicting relationships with one ID. |
| `dangling-graph-edge` | error | A relationship endpoint does not exist. |
| `invalid-binding-edge` | warning | An operation binding violates its transport contract. |
| `resolver-confidence-exceeded` | error | A resolver emitted evidence above its declared confidence ceiling. |
| `missing-represents-edge` | error | A representation expected to contribute an architecture concept has no `REPRESENTS` edge. |
| `reserved-semantic-segment` | error | A structural key uses a segment reserved for a semantic node kind. |

## Artifact references and bindings

| Code | Severity | Meaning |
|---|---|---|
| `ambiguous-artifact-reference` | warning | An artifact reference has several candidates. |
| `unresolved-openapi-operation-binding` | warning | A ZDL REST declaration has no matching OpenAPI operation. |
| `ambiguous-openapi-operation-binding` | warning | A ZDL REST declaration matches several OpenAPI operations. |
| `unresolved-api-reference` | error | A ZDL API reference has no target artifact. |
| `unresolved-channel-reference` | error | A declared AsyncAPI channel cannot be resolved. |
| `unresolved-zdl-method-reference` | error | A ZFL operation cannot resolve its ZDL method. |
| `unsupported-asyncapi-binding-direction` | warning | A channel does not provide one usable operation direction. |
| `zdl-problem` | warning | The ZDL parser reported a source problem. |
| `zfl-problem` | warning | The ZFL parser reported a source problem. |
| `zfl-semantic` | varies | The ZFL semantic analyzer reported a problem. |
| `unresolved-zfl-operation` | error | A flow calls an unknown operation or cannot resolve its declared target. |
| `unresolved-zfl-event` | error | A flow event cannot resolve its declared ZDL event. |
| `unresolved-zfl-zdl-reference` | warning | A flow system has no matching ZDL artifact. |
| `ambiguous-zfl-zdl-reference` | warning | A flow system matches several ZDL artifacts. |
| `unresolved-zfl-service` | warning | A flow refers to an unknown service. |
| `zfl-operation-missing-service` | warning | A flow operation has no service declaration. |
| `zfl-service-missing-zdl` | warning | A declared system has no ZDL artifact. |
| `conflicting-zfl-service-declaration` | warning | A flow occurrence conflicts with its operation service. |
| `unknown-compensation-target` | warning | A compensation target does not exist. |

API consumption parsers may add transport-specific codes. Those diagnostics preserve their original
code when projected into the architecture graph.
