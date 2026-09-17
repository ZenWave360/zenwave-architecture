# Architecture operation identity and bindings

`ZDL_METHOD` is the canonical identity of a logical operation. A `ZFL_OPERATION` is a flow-local
declaration that resolves to that method through `RESOLVES_TO`; each execution is a separate
`ZFL_STEP` with `role=operation-occurrence` and an `OCCURRENCE_OF` edge.

A ZFL `do` declaration is not automatically a transported command. An operation is exposed only
when its resolved method has a validated `BINDS_TO` edge with `role=invocation`. Trigger, emission,
and response bindings never expose the operation.

Binding attributes use `ArchitectureBindingAttributes` and `ArchitectureBindingValues` as their
wire contract. Consumers should decode them through `ArchitectureOperationBinding.from(edge)`.
Every binding declares `role`, `transport`, `messageKind`, and `direction`; OpenAPI bindings also
carry operation ID, method, and path, while AsyncAPI bindings carry channel key, address, and the
operation ID when one exists.

Only these relationships are binding evidence:

- an OpenAPI GET/HEAD method received by the owning service is a query invocation;
- an OpenAPI POST/PUT/PATCH/DELETE method received by the owning service is a command invocation;
- an AsyncAPI command received by the owning service is a command invocation;
- a received AsyncAPI event is a trigger;
- sent events and outbound commands are emissions;
- explicit response channels are responses.

Graph IDs are constructed exclusively by `ArchitectureGraphIds`. Modeled catalog resource IDs are
derived from the logical method rather than any one transport, so adding another compatible
binding does not change operation identity.
