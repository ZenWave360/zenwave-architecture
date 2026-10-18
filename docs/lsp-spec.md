# ZenWave LSP Adapter Specification

Status: Draft
Version: 1.0.0
Scope: LSP adapter, document/architecture boundary, editor transports, thin
IntelliJ and VS Code clients
Governed by: `mcp-spec.md`

---

## How to read this document

This is a Part II document. `mcp-spec.md` Part I governs it.

The LSP adapter defines no invariants of its own. Where this document appears to
add one, it is restating a Part I rule in editor terms. When it conflicts with
Part I, Part I wins and this document is wrong.

Everything here is expected to change as real editor usage teaches us things.
The one thing that is not open is section 2.

---

## 1. What the LSP adapter is

### 1.1 Repository ownership

The repository boundary is explicit:

| Repository | Owns |
|---|---|
| `zenwave-architecture` | `architecture-manifest`, `architecture-graph`, `workspace-runtime`, validation CLI and MCP adapter |
| `zenwave-lsp` | `lsp-core`, `lsp-jvm`, `lsp-js`, IntelliJ plugin and VS Code extension |

There is no `lsp-core` module in `zenwave-architecture`. The protocol-neutral
architecture contract already lives there as `WorkspaceQueryApi`. The existing
`zenwave-lsp/lsp-core` consumes that contract and owns the crossing between open
documents and Workspace concepts.

This keeps the dependency direction singular:

```
IntelliJ / VS Code
        |
        v
zenwave-lsp: lsp-core, lsp-jvm, lsp-js
        |
        v
zenwave-architecture: workspace-runtime -> architecture-graph -> architecture-manifest
```

No dependency points from `zenwave-architecture` back to `zenwave-lsp`.

Two products share the LSP wire protocol and must not be confused.

```
document language server        architecture adapter
grammar, positions, buffers     concepts, relationships, provenance
works on one file               works on a Workspace
zenwave-lsp today               mcp-spec.md section 18, step 14
```

ZenWave ships one server that serves both. Users get one extension, one process
and one navigation model; `OrderPlaced` in a `.zdl` buffer is ctrl-clickable to
the concept without a second tool.

They stay one server and two planes. Section 3 draws that line.

What the LSP adapter is not:

- Not a code intelligence tool. `mcp-spec.md` section 6.5 stands: classes,
  methods, call hierarchies and method bodies belong to Serena or an equivalent.
- Not an agent surface. Agents use MCP. A feature that exists only to serve an
  agent does not belong here.
- Not a second Workspace Query API. Section 5 makes that testable.
- Not an editing surface for architecture. Section 15.

## 2. The two reference systems

This is the section that is not open.

A document reference and a semantic ID are different things that have been
called by the same name. `lsp-core` currently declares:

```kotlin
typealias SemanticId = String                                  // contracts/Position.kt
internal fun zdlSemanticId(uri: String, path: String): String  // zdl/ZdlModelAccess.kt
```

That value is a document URI plus a document path. `mcp-spec.md` section 4.1
forbids exactly that as an identity, and section 4.5 forbids a second resolver
because resolvers drift. Two types named `SemanticId`, one of which is a
location, is that drift already in the codebase.

### 2.1 SemanticId

```
zw:sales/ordering/order-service/event/OrderPlaced
```

Owned by the KMP core. The LSP never constructs one by string concatenation. It
obtains one from the core, or it does not have one.

### 2.2 DocumentSymbolRef

```
file:///workspace/orders/model.zdl  #  events.OrderPlaced
```

Owned by the LSP. A URI plus an artifact-internal path, optionally a range. It
is a location in the sense of `mcp-spec.md` section 3.3: the most volatile
layer, correct only for the buffer it came from.

The type currently named `SemanticId` in `lsp-core` is renamed
`DocumentSymbolRef`. This is a rename, not a redesign: the value is already a
location and always was.

### 2.3 The crossing rule

There are exactly two crossings between the systems, and both go through the
core.

```
DocumentSymbolRef  --  conceptAt  -->  SemanticId
       ^                                    |
       |                                    |
       +-------------  sources  ------------+
```

- Document to architecture: `conceptAt(DocumentSymbolRef)` resolves through
  graph provenance to a `SemanticId`, or returns nothing. It never guesses and
  never falls back to name matching, per `mcp-spec.md` section 4.5.
- Architecture to document: `sources(SemanticId)` returns provenance, from which
  the adapter derives a URI and a range.

No other path is permitted. In particular, no LSP code parses a `zw:` ID into
segments to find a file, and no LSP code builds a `zw:` ID from a path.

**`conceptAt` has no Query API backing today, and that is the first real test of
`mcp-spec.md` section 6.2.** Every other architecture feature in section 5 maps
onto an operation that already exists. This one needs a reverse provenance
lookup: given a URI and an artifact-internal path, which element claims that
location as evidence, and which concept does it represent. Two honest
resolutions:

- Add a Query API operation, `at(location): List<NodeRef>`, and let MCP and the
  Web Explorer have it too. Reverse provenance is a domain-level question, not
  an editor convenience, and the Web Explorer will want it the moment it shows a
  file.
- Or keep the reverse index in the adapter, built from `sources()` over the
  graph, and accept that it is a cache rather than a second source of truth.

The first is more likely correct. What is not permitted is the third option:
having the adapter reach past the Query API into `ArchitectureGraph` to walk
provenance itself. That is routing around the question.

A `DocumentSymbolRef` must never be persisted by a client as an identity, never
appear in a bookmark, a task link, a saved query or agent context, and never
cross a process boundary as though it were stable. Only `zw:` IDs may, and
`mcp-spec.md` section 4.4 is why.

## 3. Two planes

### 3.1 The document plane

Parsing, syntax and parser-semantic diagnostics, hover on local symbols,
definition within a document or into a directly referenced document, document
symbols and hierarchy, formatting, completion, `organizeZflServices`.

Inputs: the open buffer and the documents it directly references.

### 3.2 The architecture plane

Go to concept, list the representations of a concept, references across
artifacts, workspace symbols, architecture diagnostics, publishes and subscribes
navigation, provenance inspection.

Inputs: a resolved Workspace and the architecture graph, through
`WorkspaceQueryApi`.

### 3.3 Degradation is a requirement, in both directions

**The document plane must never require the architecture plane.** Opening a lone
`.zdl` file outside any Workspace yields full editing support and zero
architecture features. Not an error, not a warning banner, not a degraded
parser. A grammar does not need a manifest.

**The architecture plane must never require ZDL or ZFL.** In a Workspace holding
only a manifest, repositories, OpenAPI and AsyncAPI, concepts exist and every
architecture feature in section 5 works. This is `mcp-spec.md` section 5.5
restated for editors, and section 16.2 there is the test that keeps it honest.

An editor is where the second rule breaks first, because the DSL files are the
ones a ZenWave user has open. If architecture navigation only lights up in
`.zdl` buffers, the world model has become DSL-gated and nobody will notice from
inside the editor.

## 4. There is no second index

`CrossReferenceIndex` currently holds cross-artifact relationships keyed by
`(uri, semanticId)`. Those are the same facts the architecture graph holds, in a
second store, under a second identity scheme.

```
before                          after

CrossReferenceIndex             CrossReferenceIndex
  intra-document refs             intra-document refs only
  cross-artifact refs           WorkspaceQueryApi.related
  cross-language refs             cross-artifact and cross-language
```

Intra-document references stay local: they are position work, they must survive
with no Workspace, and the graph does not model them.

Every cross-artifact reference query delegates to `related`. `mcp-spec.md`
section 4.5 forbids the alternative, and section 6.2 says what to do when
delegation does not fit: fix the Query API or stop overreaching, do not route
around it.

## 5. Feature surface

Every architecture feature maps one-to-one onto a Workspace Query API
operation. That mapping is the section 6.2 test, written down.

### 5.1 Standard LSP methods

| Method | Plane | Backed by |
|---|---|---|
| `textDocument/didOpen`, `didChange`, `didClose` | document | session store |
| `textDocument/publishDiagnostics` | both | parser, plus `diagnostics(scope)` |
| `textDocument/hover` | both | module hover, enriched by `resolve` |
| `textDocument/definition` | document | module definition |
| `textDocument/typeDefinition` | architecture | `conceptAt` then `resolve` |
| `textDocument/implementation` | architecture | `related(edgeKinds=[represents], direction=INCOMING)` |
| `textDocument/references` | architecture | `related(direction=BOTH)` then `sources` |
| `textDocument/documentSymbol` | document | module hierarchy |
| `workspace/symbol` | architecture | `search(query, kinds, scope, limit)` |
| `textDocument/formatting` | document | module format |
| `textDocument/completion` | document | module completion |

`textDocument/references` on a concept returns every representation and every
related concept the filters admit. It is one `related` call with arguments, not
a bespoke traversal. `mcp-spec.md` section 12.2 already refused `dependencies`
and `dependents` as operations for the same reason.

### 5.2 `zenwave/*` extensions

Standard LSP has no notion of an architecture concept. These requests carry it.

| Request | Plane | Backed by |
|---|---|---|
| `zenwave/overview` | architecture | `overview()` |
| `zenwave/resolve` | architecture | `resolve(id)` |
| `zenwave/browse` | architecture | `children(id, kinds, page)` |
| `zenwave/search` | architecture | `search(query, kinds, scope, limit)` |
| `zenwave/related` | architecture | `related(...)` |
| `zenwave/sources` | architecture | `sources(id)` |
| `zenwave/diagnostics` | architecture | `diagnostics(scope, severity)` |
| `zenwave/conceptAt` | crossing | reverse provenance lookup; see section 2.3 |
| `zenwave/workspaceStatus` | lifecycle | section 8 |
| `zenwave/hierarchy` | document | module hierarchy |
| `zenwave/organizeZflServices` | document | ZFL module |

The architecture rows are pass-through, with the single exception noted in
section 2.3. They add protocol mapping, argument validation, result bounds and
presentation hints, and nothing else. If a client needs an architecture answer
that is not one of these calls, that is a Query API question, not an LSP
feature.

Keeping this table honest is the point of having it. A row whose "Backed by"
column has to describe a procedure rather than name a call is a finding.

Results carry structured `zw:` IDs, never prose an editor must parse.
`mcp-spec.md` section 14.1 applies here unchanged.

## 6. Diagnostics

Two streams, distinguishable by the client.

| Stream | Origin | Positioned by | `source` |
|---|---|---|---|
| Document | `LanguageModule.parse` | the parser | `zenwave` |
| Workspace | `ArchitectureGraphResult` | provenance, via `sources` | `zenwave-architecture` |

Rules:

- The LSP invents no diagnostic codes and rewrites no severities. Workspace
  diagnostics are republished with the graph's `code` and `severity` verbatim.
  The catalogue is `diagnostics.md`; `mcp-spec.md` section 13 owns it.
- Severity maps `ERROR` to Error, `WARNING` to Warning, `INFO` to Information.
  LSP `Hint` is document-plane only; the graph has no hint severity.
- A workspace diagnostic keyed to a concept with no provenance in any open
  document is still published: on the manifest document, or through a
  workspace-wide diagnostic sink where the client supports one. Architecture
  errors that only appear once you happen to open the right file are
  architecture errors nobody sees.
- The diagnostic's `data` carries its `semanticId` so a client can offer "reveal
  in architecture view" without re-resolving.

## 7. Staleness

The graph is derived from artifacts as resolved from disk and remote sources.
The editor holds unsaved buffers. These disagree constantly.

Three positions are available. Rebuilding on every keystroke is not viable with
remote resolution. Ignoring buffers silently is worse: the server answers
confidently from a world that no longer exists.

The rule: **the architecture plane answers from the last successful Workspace
build, and says so when that build is behind the buffers.**

- Editing a buffer that participates in the graph marks the graph stale.
- Stale is reported by `zenwave/workspaceStatus` and on every architecture
  response.
- Architecture answers continue to be served while stale. Degraded is better
  than absent.
- A rebuild is triggered on save, on manifest change, and on explicit request.

This is `truncated` from `mcp-spec.md` section 12.1 restated in time rather than
in size: a consumer must be able to tell a complete answer from a bounded one,
and a fresh answer from an aged one. Never serve a stale answer that is
indistinguishable from a fresh one.

## 8. Workspace lifecycle

- Discovery: from `initialize.rootUri` by convention, overridden by a
  `zenwave.workspace.manifest` setting.
- A document belongs to at most one Workspace. Multiple manifests in one window
  produce multiple graphs; a document is never resolved against two.
- No manifest found is not an error. `workspaceStatus` reports `none` and the
  document plane serves normally.
- A failed graph build is not an error either. `workspaceStatus` reports
  `failed` with the diagnostics, and the document plane is unaffected.
- Remote source failure degrades available knowledge and nothing else, per
  `mcp-spec.md` section 15. Local editing must not depend on Apicurio,
  Artifactory or a Git host being reachable.

`zenwave/workspaceStatus` returns at least: `state` (`none`, `building`,
`ready`, `stale`, `failed`), the manifest URI, the build timestamp, and
diagnostic counts by severity.

## 9. Module architecture

There is no new module. The architecture plane is a package inside `lsp-core`
behind one facade type, because a plane that cannot see the document session is
unable to answer `conceptAt`, and a separate module would only relocate the
seam.

```
lsp-core (KMP)
  document plane      contracts, parser modules, session store, xref
  architecture plane  facade over WorkspaceQueryApi
        |
        v
  workspace-runtime  ->  architecture-graph  ->  architecture-manifest
        |
        +--------------------------+
        |                          |
   lsp-jvm (LSP4J, stdio)     lsp-js (vscode-languageserver, node)
        |                          |
   IntelliJ plugin           VS Code extension
```

Rules:

- The dependency is one-way and stays one-way. No type from `io.zenwave360.lsp`
  may appear in any `io.zenwave360.architecture` signature. `mcp-spec.md` section
  6.4 is the invariant; a Gradle module boundary is the enforcement.
- `lsp-core` depends on the `WorkspaceQueryApi` interface, not on
  `DefaultWorkspaceQueryApi`, so the architecture plane is testable against a
  fixture graph with no manifest loading.
- `workspace-runtime` and `architecture-graph` target `jvm()` and `js(IR)/nodejs()`,
  matching `lsp-jvm` and `lsp-js`. Both transports get the same plane.
- `settings.gradle.kts` in `zenwave-lsp` already substitutes `architecture-manifest`;
  it gains `architecture-graph` and `workspace-runtime`.
- IntelliJ and VS Code packaging stays outside `lsp-core`. It may use dedicated
  modules or directories in `zenwave-lsp`, but it may depend only on the
  corresponding server transport and shared wire DTOs.

## 10. Thin clients

`mcp-spec.md` section 18, step 15 says thin. This is what thin means.

A client may: launch or attach to the server, own the transport, render results,
forward `zenwave/*` requests, own presentation such as tree icons, diagram panes
and deep-link handling, and own editor-native affordances.

A client may not: parse ZDL, ZFL, OpenAPI or AsyncAPI; construct, parse or split
`zw:` IDs beyond treating them as opaque strings; hold its own graph, index or
cache of relationships; re-implement navigation or reference resolution.

The test of thinness: the client's sources contain no ZenWave grammar, no `zw:`
string building, and no relationship logic. Deleting the client should cost
rendering and nothing else.

IntelliJ: a dedicated plugin module in `zenwave-lsp`, using LSP4IJ or the
platform LSP API over `lsp-jvm`.

VS Code: a dedicated extension directory in `zenwave-lsp`, extending the
existing `zdl-vscode` behavior over `lsp-js`.

Both clients live in the `zenwave-lsp` repository so protocol changes, server
changes and client compatibility can be reviewed together. They retain
independent packaging and marketplace release tasks. Neither client source set
is compiled into `lsp-core`, `lsp-jvm` or `lsp-js`.

## 11. Deep links and cross-tool continuity

A `zw:` ID is the shared address across MCP conversations, Web Explorer URLs,
CI logs, documentation and editors. `mcp-spec.md` section 4.4 lists those escape
routes as the reason aliases are v1.

- Clients register a handler that turns a `zw:` link into `resolve`, then
  `sources`, then open at range.
- "Copy semantic ID" is a first-class command on any architecture element. This
  is the main way IDs get out of the editor and into the other tools.
- A link that resolves through an alias opens the canonical target and reports
  `redirectedFrom`. The client may offer to update the stored link. It never
  rewrites one silently; `mcp-spec.md` section 11 says ZenWave rewrites nothing
  on a client's behalf.

Selecting an element in an editor architecture view and selecting it in the Web
Explorer produce the same ID. That is what makes them one tool rather than two.

## 12. Error semantics

| Condition | Behaviour |
|---|---|
| Unknown ID in `zenwave/resolve` | Not-found with closest candidates |
| Position with no concept | Empty result, not an error |
| Ambiguous `workspace/symbol` | Candidates, never a guess |
| Alias hit | Canonical element plus `redirectedFrom` |
| No Workspace | Document plane serves; `workspaceStatus: none` |
| Graph build failed | Document plane unaffected; `workspaceStatus: failed` |
| Stale graph | Answer served, marked stale |
| Unresolvable remote source | Keep the element, report unresolved provenance |
| Traversal bound exceeded | Partial subgraph with `truncated: true` |
| Unknown `zenwave/*` method | Standard LSP MethodNotFound |

A malformed `zw:` string arriving from a client is a not-found with candidates,
not an exception. Clients pass strings; the boundary validates them.

## 13. Validation scenarios

1. **Lone file.** Open a `.zdl` outside any Workspace. Full parsing, hover,
   definition, formatting. Zero architecture features. No error, no banner.
2. **Arcadia Editions.** Ctrl-click `OrderPlaced` in a `.zfl` and land on
   `zw:sales/ordering/order-service/event/OrderPlaced`. Find references lists the
   ZDL event, the ZFL event, the AsyncAPI channel and the subscribing service.
   `workspace/symbol "OrderPlaced"` returns the concept first, representations
   after.
3. **API-only Workspace.** Repeat scenario 2 in a Workspace with no ZDL and no
   ZFL. Everything that is not DSL-specific still works. Mandatory, for the
   reason in `mcp-spec.md` section 16.2.
4. **Rename.** A `zw:` link stored before a service moved between domains still
   opens the right file, and the redirect is visible.
5. **Staleness.** Edit a participating buffer without saving. Architecture
   answers keep working and are marked stale. Save. The mark clears.
6. **Thinness.** Point a generic LSP client at `lsp-js` with no ZenWave
   extension installed. Navigation, diagnostics and symbols work.

Scenario 3 is the one that will be skipped, and it is the one that matters most.

## 14. Build order

```
 1  Rename lsp-core SemanticId to DocumentSymbolRef
 2  lsp-core depends on WorkspaceQueryApi; architecture plane facade
 3  Workspace discovery, workspaceStatus, degradation
 4  Workspace diagnostics stream, codes republished verbatim
 5  conceptAt and sources: the two crossings
 6  references and workspace/symbol delegate to related and search;
    cross-artifact entries leave CrossReferenceIndex
 7  Staleness model
 8  Deep links and copy-semantic-ID
 9  Validate scenarios 1 to 3
10  Thin VS Code extension in zenwave-lsp over lsp-js
11  Thin IntelliJ plugin in zenwave-lsp over lsp-jvm
12  Validate scenarios 4 to 6
```

Step 1 comes first because every later step either reinforces the naming
collision or removes it, and the collision is cheap to fix now and expensive
once two clients depend on it.

The adapter as a whole follows the MCP adapter, for the reason `mcp-spec.md`
section 18 gives: an agent consumer forces the Query API to become a real
contract, where one editor screen would shape it into whatever that screen
needed.

## 15. Deferred and non-goals

- No architecture editing through the LSP. Editing goes through Git, as in
  `mcp-spec.md` section 20 for the Web Explorer.
- No rename refactoring that rewrites semantic IDs across a Workspace. Aliases
  are the continuity mechanism.
- No call hierarchy or type hierarchy over implementation code. Section 6.5.
- No semantic tokens driven by the architecture graph.
- No embedding or vector search. Section 5.4.
- No agent-facing tooling. Agents use MCP.
- `textDocument/codeAction` over architecture diagnostics may come later; it is
  not v1 and must not become a mutation API by accident.

## 16. Open questions

- Whether the architecture plane runs in-process or against a shared Workspace
  daemon also serving MCP and the Web Explorer.
- Incremental graph rebuild granularity, and whether it is worth having.
- Whether unsaved buffers should ever feed the graph, and under what bound.
- Multi-root windows with several manifests, and how a client picks scope.
- How `zenwave/related` renders in a generic LSP client with no custom UI.
- Whether diagnostics for referenced but unopened artifacts are published.
- Credentials for remote sources inside an editor process.
- Whether `conceptAt` should return several concepts when a position genuinely
  represents more than one.
- Whether reverse provenance becomes a Query API operation, per section 2.3.
  This one is not adapter-local and may belong in `mcp-spec.md` section 22.

## 17. Summary

```
                     buffers                     Workspace
                        |                            |
                        v                            v
                 document plane            architecture plane
                        |                            |
              DocumentSymbolRef                 SemanticId
                        |                            |
                        +---- conceptAt / sources ---+
                                     |
                              ZenwaveLanguageServer
                                     |
                        +------------+------------+
                        |                         |
                     lsp-jvm                   lsp-js
                        |                         |
                    IntelliJ                   VS Code
```

One server, two planes, one identity model.

A document reference is a location and is never called a semantic ID.

Architecture features delegate to the Workspace Query API and hold no index of
their own.

The document plane never needs a Workspace, and the architecture plane never
needs a ZenWave DSL.

Clients render. They do not parse, resolve or remember.
