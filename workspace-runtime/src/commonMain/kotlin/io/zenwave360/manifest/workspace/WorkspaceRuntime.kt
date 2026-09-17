package io.zenwave360.manifest.workspace

import io.zenwave360.manifest.ZenWaveManifest
import io.zenwave360.manifest.ZenWaveManifestLoader
import io.zenwave360.manifest.graph.ArchitectureGraphBuildOptions
import io.zenwave360.manifest.graph.ArchitectureGraphBuilder
import kotlin.jvm.JvmOverloads

/** Entry point for adapters that need a Workspace query facade without reaching into the graph. */
public class WorkspaceRuntime @JvmOverloads constructor(
    private val loader: ZenWaveManifestLoader = ZenWaveManifestLoader(),
    private val graphBuilder: ArchitectureGraphBuilder = ArchitectureGraphBuilder(loader),
) {
    @JvmOverloads
    public suspend fun open(
        manifestUri: String,
        options: ArchitectureGraphBuildOptions = ArchitectureGraphBuildOptions(),
    ): WorkspaceQueryApi = build(loader.load(manifestUri), options)

    @JvmOverloads
    public suspend fun build(
        manifest: ZenWaveManifest,
        options: ArchitectureGraphBuildOptions = ArchitectureGraphBuildOptions(),
    ): WorkspaceQueryApi = DefaultWorkspaceQueryApi(graphBuilder.build(manifest, options))
}
