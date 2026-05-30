package com.github.kirangadhave.marimopycharm.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.kirangadhave.marimopycharm.MyBundle
import com.github.kirangadhave.marimopycharm.services.MyProjectService
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JButton


class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow(private val toolWindow: ToolWindow) {
        private val service = toolWindow.project.service<MyProjectService>()

        fun getContent() = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val label = JBLabel(MyBundle["randomLabel", "?"])

            val controls = JBPanel<JBPanel<*>>().apply {
                add(label)
                add(JButton(MyBundle["shuffle"]).apply {
                    addActionListener {
                        label.text = MyBundle["randomLabel", service.getRandomNumber()]
                    }
                })
                add(JButton("Open Google").apply{
                    addActionListener {
                        openBrowser(this@apply.parent.parent as JBPanel<*>)
                    }
                })
            }

            add(controls, BorderLayout.NORTH)
        }

        private fun openBrowser(container: JBPanel<*>) {
            if (!JBCefApp.isSupported()) {
                thisLogger().warn("JCEF is not supported in this runtime!")
                return
            }

            val browser = JBCefBrowser("https://google.com")
            Disposer.register(toolWindow.disposable,browser)
            container.add(browser.component, BorderLayout.CENTER)
            container.revalidate()
            container.repaint()
        }
    }
}
