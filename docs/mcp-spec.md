# ZenWave Workspace Specification

Status: Draft
Version: 1.0.0
Scope: Workspace Runtime, semantic identity, architecture graph, Workspace Query API, MCP adapter, Web Explorer, LSP adapter

---

## How to read this document

This specification has two parts with different change rates.

**Part I is the constitution.** It defines invariants: what a concept is, what
identity means, what must never leak into what. These should be hard to change.
Changing one is a redesign, not a revision.

**Part II is the implementation RFC.** It defines concrete APIs, tool surfaces,
diagnostics and milestones. These are expected to change as Arcadia Editions
teaches you things.

When Part II conflicts with Part I, Part I wins and Part II is wrong.

**Companion adapter specifications.** An adapter with a surface large enough to
need its own document gets one. Those documents are Part II and are governed by
Part I exactly as sections 7 to 22 are.

```
mcp-spec.md      this document: Part I, and the MCP adapter in section 14
lsp-spec.md      the LSP adapter, and the thin IntelliJ and VS Code clients
diagnostics.md   the diagnostic code catalogue owned by section 13
```

An adapter specification may not introduce graph semantics, identity rules or
knowledge invariants. If one appears to, it is either restating Part I or it is
wrong. Section 6.2 is the general form of that rule.

---

# Part I. Constitution

## 1. What ZenWave Platform is

ZenWave Platform is a semantic architecture runtime.

It makes the architecture of a software system directly navigable by humans and
agents, without either party reconstructing that architecture from raw files.

What it is not:

- Not an IDE.
- Not a RAG system.
- Not a source-code search platform.
- Not a graph database product.
- Not a documentation generator.

Each of these is a plausible drift direction. Each would consume the project.

## 2. The problem

Architects, developers, domain experts and agents need to understand systems
whose knowledge is distributed across many repositories and artifacts.

Today those questions are answered by locating and opening files. That does not
scale, it is expensive for agents, and it is fragile under refactoring because
paths and repository structures change.

## 3. Concept, representation, location

This is the central distinction in ZenWave Platform. Everything else follows from it.

```
semantic identity  !=  representation  !=  source location
```

### 3.1 Concepts

A concept is an architectural thing that exists independently of how it is
written down. A domain. A service. A command. An event. A flow. A capability.

`OrderPlaced` is one concept.

### 3.2 Representations

A representation is a concrete expression of a concept in some formalism.

```
ZDL:      event OrderPlaced
AsyncAPI: #/components/messages/OrderPlaced
Java:     com.example.orders.events.OrderPlaced
Kafka:    sales.orders.order-placed
```

Four representations. One concept.

### 3.3 Locations

A location is where a representation physically lives. Repository, path,
pointer, symbol, line.

Locations are the most volatile layer. They change on every refactor.

### 3.4 The rule

Concepts carry canonical semantic identity.

Representations may be graph nodes when they are architecturally meaningful in
their own right, such as an API operation or a channel. When they are, they link
to their concept with a `REPRESENTS` edge. They are never siblings of the
concept they represent.

Locations are never nodes. They are provenance.

```
zw:sales/ordering/order-service/event/OrderPlaced        concept
        ^
        | REPRESENTS
        |
zw:sales/ordering/order-service/channel/order-created    representation
        |
        +-- provenance: asyncapi.yaml #/channels/order-created   location
```

This rule exists because a taxonomy that lists `event` and `message` as peer
kinds will produce a graph where nobody can tell which node is the thing and
which is a description of the thing. That confusion is not recoverable once
agents have been trained on the output.

## 4. Identity invariants

### 4.1 Identity is not location

A semantic ID must not depend on repository URL, branch, filesystem path,
document path, JSON or YAML pointer, source line number, or artifact version.

### 4.2 Identity survives physical change

A semantic ID does not change when a file moves, a repository is renamed, a Git
host changes, an artifact is split across files, line numbers change, an API
document is versioned, or generated source is regenerated.

### 4.3 Identity may change on a semantic move

Semantic IDs contain architectural hierarchy. That is deliberate: it makes them
readable, browsable, scopeable and meaningful in a log or a conversation.

The cost is that a genuine architectural move changes the canonical ID.

```
zw:sales/ordering/order-service/event/OrderPlaced
        becomes
zw:sales/fulfilment/fulfilment-service/event/OrderPlaced
```

This is accepted. Continuity is provided by aliases.

ZenWave Platform does not replace meaningful IDs with opaque UUIDs to make semantic moves
invisible. Readability is worth more than move-transparency, given that aliases
solve the continuity problem.

### 4.4 Aliases are part of identity, not an add-on

Semantic IDs escape into diagnostics, CI logs, bookmarks, Web Explorer URLs, MCP
conversations, documentation, IDE navigation and stored agent context.

An identity model without aliases is an identity model that breaks all of those
on the first rename. Aliases are v1 and are never deferred.

Aliases are append-only. Removing a published alias is a breaking change.

### 4.5 Resolution is deterministic and singular

There is exactly one semantic ID resolver in the KMP core. MCP, Web Explorer,
diagnostics, CLI and future LSP navigation all delegate to it.

Multiple resolvers are forbidden. They drift.

Resolution never falls back to fuzzy matching. Identity resolution and human
discovery are different operations with different failure modes. Mixing them
lets ambiguity contaminate identity semantics.

## 5. Knowledge invariants

### 5.1 Sources remain authoritative

The graph is derived. It is not the primary source of truth.

```
Manifest / APIs / DSLs / repositories / registries
                    |
                    v
            Workspace Runtime
                    |
                    v
           Architecture Graph
```

Indexes and caches may be persisted. They must remain rebuildable.

### 5.2 Provenance answers why, not just where

Provenance is not a file pointer. It is the justification for a piece of
knowledge.

The question it answers is: *why does ZenWave Platform believe this?*

This applies to relationships as much as nodes. `OrderService PRODUCES
OrderPlaced` may exist because AsyncAPI declares it, or because a Spring Cloud
Stream indexer inferred it. Those are different facts about the same edge, and
an agent reasoning about risk needs to tell them apart.

### 5.3 Confidence is what makes inference safe

Every relationship records how ZenWave Platform knows it.

```
DECLARED            stated by an architectural source
RESOLVED            deterministically dereferenced
INFERRED_EXACT      deterministic integration rule
INFERRED_HEURISTIC  convention, naming, probabilistic match
```

Heuristic inference must never silently become architectural truth.

Confidence is therefore both returned and queryable. Without a filter, one loose
resolver degrades every answer in the graph, and there is no way to ask what the
architecture actually states.

Every resolver declares the maximum confidence it may emit. A naming-convention
resolver may not produce `DECLARED`.

### 5.4 Structured knowledge before retrieval

Deterministic graph queries come before embedding-based retrieval. Semantic
search may complement the graph for fuzzy discovery. It is not the source of
architectural truth.

### 5.5 Progressive enrichment

ZenWave Platform must be useful without ZenWave-specific DSLs.

```
Level 1   domains, services, repositories, OpenAPI, AsyncAPI
Level 2   producers, consumers, topology, infrastructure
Level 3   aggregates, commands, events, policies
Level 4   business and event flows
Level 5   implementation traceability
```

No level is a prerequisite for the levels below it. ZDL, ZFL and Event Storming
are enrichment layers.

This is not a marketing position. It is a constraint on the graph model, and it
is validated by a mandatory test workspace (section 16.2).

## 6. Boundary invariants

### 6.1 The Workspace Query API exposes projections, not screens

```
getDetailPanel
getSidebarTree
getGraphView
```

None of these belong in the Workspace Query API. A projection that serves only
one screen or one adapter is not a projection.

### 6.2 Adapters add no graph semantics

```
MCP        Web Explorer        LSP
 |              |               |
 +--------------+---------------+
                |
                v
      Workspace Query API
                |
                v
       Architecture Graph
```

MCP adds protocol mapping, argument validation, result bounds and readable
summaries. It adds no query logic. Neither does the Web Explorer, and neither
does the LSP.

If an adapter needs graph semantics that do not exist in the Query API, either
the Query API is wrong or the adapter is overreaching. Resolve that question,
do not route around it.

Every adapter feature must be expressible as a Query API call. An adapter
specification that cannot write that mapping down has not found the boundary
yet.

### 6.3 The visualization model is not the architecture model

Architectural Vistas are projections of the graph. Layout, grouping and visual
affordances belong to the Web Explorer. They must not flow back into the graph.

### 6.4 The document model is not the architecture model

LSP concepts, URI, line, position, range, document symbol, must never become
foundational to the architecture graph. LSP is an adapter that projects the
graph into a document-centric view.

A document reference is a location, in the sense of section 3.3. It is never
called a semantic ID, never stored by a client as an identity, and never used
as a graph key. Two things named `SemanticId`, one of which is a URI plus a
document path, is section 4.5 already violated.

`lsp-spec.md` sections 2 and 9 hold the adapter side of this boundary.

### 6.5 ZenWave Platform is not a code intelligence tool

```
              Agent
             /     \
    ZenWave MCP   Code MCP
         |             |
  architecture       code
```

ZenWave Platform answers questions about domains, services, repositories, APIs,
operations, events, messages, flows, contracts and architectural relationships.

A code intelligence tool such as Serena answers questions about classes,
methods, symbols, call hierarchies and method bodies.

They are peers. ZenWave Platform may know:

```
OrderPlaced IMPLEMENTED_BY repository X, symbol Y
```

without indexing the body of symbol Y. The agent takes those coordinates to a
code tool.

This boundary is what keeps the project finishable.

---

# Part II. Implementation RFC

## 7. Workspace descriptor

A Workspace is anchored by one Master YAML manifest and may span multiple
repositories and remote sources. It is not equivalent to a Git repository or a
local directory.

```yaml
workspace:
  id: arcadia-editions
  name: Arcadia Editions

domains:
  sales:
    subdomains:
      ordering:
        services:
          order-service:
            aliases:
              - orders-service
              - legacy-order-api
            repository: https://example.org/arcadia/orders.git
            artifacts:
              - type: asyncapi
                path: asyncapi.yaml
              - type: zdl
                path: model/orders.zdl
```

The existing manifest schema remains authoritative. This spec adds `aliases`.

## 8. Semantic ID grammar

```
zw:<domain>[/<subdomain>[/<service>[/<concept-type>/<concept-name>]]]
```

```
zw:sales
zw:sales/ordering
zw:sales/ordering/order-service
zw:sales/ordering/order-service/event/OrderPlaced
zw:sales/ordering/order-service/command/PlaceOrder
zw:sales/ordering/order-service/flow/PlaceOrder
zw:sales/ordering/order-service/api/orders
```

Artifacts get identity when the artifact is itself a first-class concept:

```
zw:sales/ordering/order-service/artifact/asyncapi/orders
```

Rules:

- A prefix of a valid ID is a valid ID where the hierarchy exists.
- Structural segments are lowercase kebab-case.
- Concept names preserve the casing of the authoritative source.
- Canonicalization is deterministic and implemented once in the KMP core.
- Artifact-internal pointers are never part of an ID.

Version is a resolution coordinate, not identity:

```
zw:sales/ordering/order-service/artifact/asyncapi/orders@1.4.0
```

Absent version means Workspace resolution order: workspace, Apicurio,
Artifactory, git.

## 9. Node and edge kinds

Concept kinds:

```
workspace | domain | subdomain | service
aggregate | entity | command | event | policy
flow | step
capability
```

Representation kinds:

```
artifact | api | operation | channel | message | schema
source-symbol | topic | queue | subscription
repository | infrastructure-resource
```

Representation nodes link to their concept with `REPRESENTS`. Where no concept
exists yet, a representation may stand alone. It does not become one by default.

Edge kinds:

```
owns | contains | references
represents
publishes | subscribes
calls | implements | implemented-by
emits | handles | triggers
uses-schema | transported-by
compensates | depends-on
```

Both sets are additive. Consumers tolerate unknown kinds.

## 10. Core shapes

Every element response carries `id`, `kind` and `name`. No exceptions. Agents
branch on `kind`. The UI picks an icon from it.

```json
{
  "id": "zw:sales/ordering/order-service",
  "kind": "service",
  "name": "Order Service",
  "identityOrigin": "explicit"
}
```

Provenance:

```json
{
  "id": "zw:sales/ordering/order-service/event/OrderPlaced",
  "kind": "event",
  "name": "OrderPlaced",
  "provenance": [
    { "kind": "zdl", "repository": "orders", "path": "model/orders.zdl", "symbol": "OrderPlaced" },
    { "kind": "asyncapi", "repository": "orders", "path": "asyncapi.yaml", "pointer": "/components/messages/OrderPlaced" },
    { "kind": "java", "repository": "orders", "path": "src/main/java/com/example/orders/events/OrderPlaced.java", "symbol": "com.example.orders.events.OrderPlaced" }
  ]
}
```

Relationship with provenance and confidence:

```json
{
  "from": "zw:sales/ordering/order-service",
  "to": "zw:sales/ordering/order-service/event/OrderPlaced",
  "kind": "publishes",
  "confidence": "DECLARED",
  "provenance": [
    { "kind": "asyncapi", "path": "asyncapi.yaml", "pointer": "/channels/order-created" }
  ]
}
```

Line numbers are navigation hints. They are never part of identity.

## 11. Resolution

```kotlin
sealed interface ResolutionResult {

    data class Resolved(
        val node: Node,
        val redirectedFrom: SemanticId? = null
    ) : ResolutionResult

    data class NotFound(
        val candidates: List<NodeRef>
    ) : ResolutionResult
}
```

A nullable `Node?` cannot carry a redirect or candidates. Both are needed.

An alias hit is successful resolution:

```json
{
  "node": { "id": "zw:sales/ordering/order-service", "kind": "service", "name": "Order Service" },
  "redirectedFrom": "zw:sales/ordering/orders-service"
}
```

Clients update stored links when they choose. ZenWave Platform rewrites nothing on their
behalf.

An alias that collides with a live canonical ID is a diagnostic error.

## 12. Workspace Query API

```kotlin
interface WorkspaceQueryApi {

    suspend fun overview(): WorkspaceOverview

    suspend fun resolve(id: SemanticId): ResolutionResult

    suspend fun children(
        id: SemanticId,
        kinds: List<NodeKind> = emptyList(),
        page: Page? = null
    ): List<NodeRef>

    suspend fun search(
        query: String,
        kinds: List<NodeKind> = emptyList(),
        scope: SemanticId? = null,
        limit: Int = 20
    ): List<NodeRef>

    suspend fun related(
        id: SemanticId,
        direction: Direction = BOTH,
        edgeKinds: List<EdgeKind> = emptyList(),
        depth: Int = 1,
        confidence: List<Confidence> = Confidence.all,
        origin: List<IdentityOrigin> = IdentityOrigin.all,
        maxNodes: Int = 100
    ): Subgraph

    suspend fun sources(id: SemanticId): List<Provenance>

    suspend fun diagnostics(
        scope: SemanticId? = null,
        severity: Severity? = null
    ): List<Diagnostic>
}
```

Ordering from `children` is deterministic and follows manifest order where
available. The tree must not jump between refreshes.

### 12.1 Traversal contract

`related` is the operation that tests whether the abstraction is right.

```json
{
  "id": "zw:sales/ordering/order-service",
  "direction": "outgoing",
  "edgeKinds": ["publishes", "calls"],
  "confidence": ["DECLARED", "RESOLVED"],
  "depth": 2,
  "maxNodes": 100
}
```

A Vistas diagram and an agent asking about impact are the same call with
different arguments. If the Web Explorer needs a traversal operation purely
because it is graphical, reconsider the abstraction.

Response separates nodes from edges so clients do not de-duplicate a nested
tree:

```json
{
  "nodes": [
    { "id": "zw:sales/ordering/order-service/event/OrderPlaced", "kind": "event", "name": "OrderPlaced" }
  ],
  "edges": [
    {
      "from": "zw:sales/ordering/order-service",
      "to": "zw:sales/ordering/order-service/event/OrderPlaced",
      "kind": "publishes",
      "confidence": "DECLARED"
    }
  ],
  "truncated": false
}
```

`truncated` is present whenever bounds can apply. An agent must be able to tell
a complete answer from a bounded one.

### 12.2 Deferred operations

`impact`, `trace` and `findPath` are not in v1. They may be added if real usage
shows they are genuine domain-level queries rather than adapter conveniences.

`dependencies` and `dependents` are not operations. They are `related` with a
direction.

## 13. Diagnostics

Diagnostics belong to the Workspace Runtime, not to any adapter. The graph
builder emits them keyed by semantic ID.

```json
{
  "id": "zw:sales/ordering/order-service/command/PlaceOrder",
  "severity": "error",
  "code": "ZW1042",
  "message": "Command target service not found in workspace",
  "provenance": { "repository": "orders", "path": "model/orders.zfl", "line": 34 }
}
```

Severity is `error`, `warning` or `info`. Codes are stable and documented in
`diagnostics.md`. CI gates on `error`.

Adapters republish these diagnostics with their `code`, `severity` and
`semanticId` unchanged. An adapter that renames a code or downgrades a severity
has forked the catalogue.

Initial checks:

- Unresolvable reference from any artifact.
- Alias colliding with a live canonical ID.
- Duplicate derived ID.
- Declared artifact missing from its repository.
- Relationship pointing at an unknown node.
- Representation node with no `REPRESENTS` edge where a concept was expected.

`zenwave validate` exposes these before MCP or Web consumers exist. This is
deliberate. It tests the identity scheme against real data while changing it is
still cheap.

## 14. MCP surface

The adapter adds protocol mapping, argument validation, result bounds and
readable summaries. Nothing else.

| Tool | Purpose |
|---|---|
| `zenwave_workspace` | Bounded Workspace overview. First call in an unknown architecture. |
| `zenwave_resolve` | Deterministic ID resolution. Follows aliases. No fuzzy fallback. |
| `zenwave_search` | Human-query discovery. Ambiguity returns candidates. |
| `zenwave_browse` | Direct children of a node. Orientation, not analysis. |
| `zenwave_related` | Bounded traversal with confidence and origin filters. |
| `zenwave_sources` | Provenance and source locations. Handoff to code tools. |
| `zenwave_diagnostics` | Architecture problems, scoped by ID and severity. |

`zenwave_related` replaces separate tools for dependencies, dependents, impact
and references.

Raw artifact reading is not a primary architectural operation. A bounded
`zenwave_read` may be added later as an escape hatch. It must not duplicate
filesystem access that coding agents already have.

### 14.1 Result convention

Structured semantic IDs are the contract. Prose is convenience.

```json
{
  "summary": "Order Service publishes two events.",
  "elements": [
    { "id": "zw:sales/ordering/order-service/event/OrderPlaced", "kind": "event", "name": "OrderPlaced" }
  ]
}
```

An agent must be able to chain a call without extracting identifiers from a
sentence.

## 15. Error semantics

| Condition | Behaviour |
|---|---|
| Unknown semantic ID | Not-found with closest candidates |
| Ambiguous search | Return candidates, never guess |
| Alias hit | Canonical node plus `redirectedFrom` |
| Unresolvable remote source | Keep the node, report unresolved provenance |
| Traversal bound exceeded | Partial subgraph with `truncated: true` |
| Unknown kind | Consumers tolerate additive values |

A remote source failure degrades available knowledge. It does not fail unrelated
queries. Local architectural work must not depend on Apicurio, Artifactory or a
Git host being reachable.

## 16. Validation

### 16.1 Arcadia Editions

Primary rich Workspace. A new agent session should answer:

- What domains and services exist?
- Explain how ordering works.
- What produces OrderPlaced? What consumes it?
- Which API exposes the ordering capability?
- Where is OrderPlaced represented?
- What depends on the Orders API?
- Which of those relationships are declared, and which are inferred?

The last question is the confidence model earning its place.

### 16.2 API-only Workspace

Mandatory, not optional.

Contains only Master YAML, Git repositories, OpenAPI and AsyncAPI. No ZDL. No
ZFL. No Event Storming.

This is the test that stops ZenWave-native DSLs from becoming accidental
prerequisites for the world model. Section 5.5 is a claim until this passes.

### 16.3 Rename scenario

Move a service between domains, add the alias, and confirm every previously
stored ID still resolves with `redirectedFrom`.

An alias mechanism nobody has exercised is not shipped.

## 17. Extensibility

New sources enrich the graph without MCP changes.

```kotlin
interface ArtifactIndexer {
    fun supports(artifact: Artifact): Boolean
    suspend fun index(workspace: Workspace, artifact: Artifact): GraphFragment
}

interface RelationshipResolver {
    val maxConfidence: Confidence
    suspend fun resolve(workspace: Workspace, graph: ArchitectureGraph): List<ArchitectureRelationship>
}
```

Indexers: Manifest, OpenApi, AsyncApi, Zdl, Zfl, JavaMetadata, Terraform,
ArchiMate.

Resolvers: SpringMvc, SpringCloudStream, KafkaTopic, ZenWaveGeneratedCode,
NamingConvention.

```
SpringCloudStreamResolver   -> INFERRED_EXACT
NamingConventionResolver    -> INFERRED_HEURISTIC
```

`maxConfidence` is declared, not chosen per edge. One loose resolver must not be
able to claim `DECLARED`.

## 18. Build order

```
 1  Semantic ID model, concept vs representation
 2  Aliases and canonical resolve in the KMP core
 3  Provenance model
 4  Confidence model
 5  Diagnostics keyed by semantic ID
 6  zenwave validate
 7  Workspace Query API
 8  Minimal MCP adapter
 9  Validate against Arcadia Editions
10  Validate against the API-only Workspace
11  Rename scenario
12  Web Explorer over the same Query API
13  Richer traversal, only if usage demands it
14  LSP adapter                                     lsp-spec.md section 14
15  Thin IntelliJ and VS Code integrations          lsp-spec.md section 10
```

MCP comes before the Web Explorer on purpose. An agent consumer forces the Query
API to become a real architecture contract instead of whatever one screen
happened to need. The LSP adapter follows both for the same reason: an editor
screen would shape the Query API into whatever that screen needed.

Steps 14 and 15 have their own build order in `lsp-spec.md`, because the LSP
adapter is a convergence with an existing document language server rather than a
greenfield surface.

## 19. Success criterion

An agent connects to a ZenWave Platform Workspace and meaningfully navigates and explains
the system through semantic architecture queries, without reconstructing the
architecture from raw repository files.

Desirable:

```
workspace -> search("ordering") -> resolve(order-service)
  -> related(order-service) -> related(OrderPlaced)
  -> sources(relevant concepts)
  -> read implementation only where necessary
  -> answer
```

Undesirable:

```
clone everything -> grep everything -> read hundreds of files
  -> infer architecture probabilistically
```

## 20. Web Explorer

Read-only in v1. Editing goes through Git.

Focus: find, browse, inspect, navigate relationships, inspect provenance,
inspect diagnostics.

Deep-linkable URLs mirror semantic IDs. Selecting a node in a Vista selects it
in the tree and updates the URL. That single behaviour is what makes it one tool
rather than two views.

## 21. LSP and IDE adapters

Specified in `lsp-spec.md`. Three points belong here because they are Part I
consequences rather than adapter detail.

**One server, two planes.** The document language server and the architecture
adapter share a process and a protocol. They do not share an identity model. The
document plane never requires a Workspace, and the architecture plane never
requires ZDL or ZFL. That second half is section 5.5 in editor form, and it is
where progressive enrichment is most likely to quietly fail: the DSL files are
the ones a ZenWave user has open.

**No second index.** Cross-artifact navigation delegates to `related`. An editor
holding its own relationship index is section 4.5 violated, whatever it calls
the index.

**Thin means thin.** A client launches the server, renders results and handles
deep links. It does not parse ZenWave formats, build semantic IDs, or hold a
graph. The LSP core and both editor clients live in the `zenwave-lsp`
repository. The clients keep independent packaging and marketplace release
tasks.

**Repository ownership.** `zenwave-architecture` owns the Architecture Graph,
`WorkspaceQueryApi`, validation CLI, MCP adapter and Web Explorer. It contains
no LSP protocol or editor SDK types. `zenwave-lsp` owns its existing `lsp-core`,
the JVM and JavaScript transports, and the IntelliJ and VS Code clients.
`lsp-core` depends one-way on `workspace-runtime`; no dependency points back
from `zenwave-architecture` to `zenwave-lsp`.

## 22. Open questions

- Exact artifact semantic-ID grammar.
- Namespace rules for colliding concept names within one service.
- Alias interaction with version coordinates.
- Cross-Workspace semantic references.
- Whether Workspace IDs require global uniqueness.
- Persistent graph and index storage.
- Server-side maximum traversal depth and node count.
- Whether documentation artifacts participate in traversal or stay leaf
  provenance.
- Semantic and vector search implementation.
- Mutation and editing APIs.
- Source linkage for non-ZenWave-generated applications.
- Remote Workspace hosting and authorization.
- Architecture-drift diagnostics beyond structural validation.
- Whether a shared Workspace daemon serves MCP, the Web Explorer and the LSP
  from one graph, or each adapter builds its own.
- Whether reverse provenance, "which element claims this file and position",
  is a Query API operation. Raised by `lsp-spec.md` section 2.3; the Web
  Explorer will ask the same question.

Aliases, diagnostics, confidence filtering and the concept/representation
distinction are not open. They are Part I.

Adapter-local open questions live in the adapter specification, not here.

## 23. Summary

```
                   Master YAML
                       |
             +---------+---------+
             |                   |
       repositories           artifacts
             |                   |
             +---------+---------+
                       |
                       v
              Architecture Graph
              /       |        \
         identity  provenance  confidence
              \       |        /
               Workspace Query API
                  /     |     \
                MCP    Web    LSP
```

Semantic IDs identify architectural concepts, not files and not representations.

Aliases preserve continuity across semantic renames and moves.

Provenance records why ZenWave believes each fact.

Confidence records how it knows, and makes heuristic enrichment safe.

The Workspace Query API provides a small set of reusable projections. Every
adapter is thin.

The objective is not to build another IDE, RAG system, graph database or code
search engine. It is to make the architecture ZenWave already understands
directly navigable by humans and agents.
