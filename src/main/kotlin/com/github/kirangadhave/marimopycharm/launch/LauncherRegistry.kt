package com.github.kirangadhave.marimopycharm.launch

class LauncherRegistry(private val launchers: List<MarimoLauncher>) {
    fun resolve(request: LaunchRequest): MarimoLauncher =
        launchers.firstOrNull { it.canLaunch(request) }
            ?: throw NoApplicableLauncherException(request)
}
