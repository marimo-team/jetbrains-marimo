package com.github.kirangadhave.marimopycharm.server

import com.github.kirangadhave.marimopycharm.launch.LaunchRequest
import com.github.kirangadhave.marimopycharm.launch.LauncherRegistry
import com.github.kirangadhave.marimopycharm.launch.MarimoServerHandle
import com.github.kirangadhave.marimopycharm.launch.SdkLauncher
import com.github.kirangadhave.marimopycharm.launch.UvLauncher
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.net.NetUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MarimoServerService(private val project: Project) : Disposable {

    private val registry = LauncherRegistry(listOf(SdkLauncher(), UvLauncher()))
    private val handles = ConcurrentHashMap<String, MarimoServerHandle>()

    fun urlFor(file: VirtualFile): CompletableFuture<String> {
        val existing = handles[file.url]
        if (existing != null) return existing.awaitReady()

        val request = LaunchRequest(
            project = project,
            notebook = file,
            port = NetUtils.findAvailableSocketPort(),
        )
        val launcher = registry.resolve(request)
        val handle = launcher.launch(request)
        handles[file.url] = handle
        Disposer.register(this, handle)
        return handle.awaitReady()
    }

    fun release(file: VirtualFile) {
        handles.remove(file.url)?.let(Disposer::dispose)
    }

    override fun dispose() {
        handles.values.forEach(Disposer::dispose)
        handles.clear()
    }
}
