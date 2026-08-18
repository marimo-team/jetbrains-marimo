/* Copyright 2026 Marimo. All rights reserved. */

package io.marimo.notebook.launch

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File

/** Starts a test helper in a new JVM without expanding the long test classpath into argv. */
fun javaProcess(vararg args: String): GeneralCommandLine {
    val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
    val classpath = System.getProperty("java.class.path")
    val argFile = File.createTempFile("marimo-process-cp", ".txt")
    argFile.deleteOnExit()
    argFile.writeText("-cp \"${classpath.replace("\\", "\\\\")}\"")
    return GeneralCommandLine(javaBin).withParameters("@${argFile.absolutePath}", *args)
}
