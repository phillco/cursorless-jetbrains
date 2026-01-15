package com.github.asoee.cursorlessjetbrains.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction

/**
 * Actions for reordering editor tabs within the current splitter.
 *
 * Inspired by TabMover plugin (https://github.com/mikinw/TabMover),
 * modernized and integrated into the cursorless plugin.
 */

private val LOG = logger<MoveTabAction>()

sealed class MoveTabDirection {
    object Left : MoveTabDirection()
    object Right : MoveTabDirection()
    object First : MoveTabDirection()
    object Last : MoveTabDirection()
}

abstract class MoveTabAction(private val direction: MoveTabDirection) : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("MoveTabAction.actionPerformed called with direction: $direction")

        val editorWindow = EditorWindow.DATA_KEY.getData(e.dataContext)
        if (editorWindow == null) {
            LOG.warn("EditorWindow is null - action invoked outside of editor context")
            return
        }
        LOG.info("Got EditorWindow: $editorWindow")

        val tabbedPane = editorWindow.tabbedPane
        if (tabbedPane == null) {
            LOG.warn("TabbedPane is null")
            return
        }
        LOG.info("Got TabbedPane: $tabbedPane")

        val tabs = tabbedPane.tabs
        LOG.info("Got tabs: $tabs")

        val selectedInfo = tabs.selectedInfo
        if (selectedInfo == null) {
            LOG.warn("No tab selected")
            return
        }
        LOG.info("Selected tab: ${selectedInfo.text}")

        val currentIndex = tabs.getIndexOf(selectedInfo)
        val tabCount = tabbedPane.tabCount

        val newIndex = when (direction) {
            MoveTabDirection.Left -> {
                if (currentIndex <= 0) tabCount - 1 else currentIndex - 1  // Wrap to end
            }
            MoveTabDirection.Right -> {
                if (currentIndex >= tabCount - 1) 0 else currentIndex + 1  // Wrap to start
            }
            MoveTabDirection.First -> {
                if (currentIndex == 0) return
                0
            }
            MoveTabDirection.Last -> {
                if (currentIndex >= tabCount - 1) return
                -1  // -1 signals "append at end"
            }
        }

        LOG.info("Moving tab from index $currentIndex to $newIndex (tabCount: $tabCount)")
        tabs.removeTab(selectedInfo)
        if (newIndex == -1) {
            tabs.addTab(selectedInfo)
        } else {
            tabs.addTab(selectedInfo, newIndex)
        }
        tabs.select(selectedInfo, true)
        LOG.info("Tab move completed")
    }

    override fun update(e: AnActionEvent) {
        val editorWindow = EditorWindow.DATA_KEY.getData(e.dataContext)
        e.presentation.isEnabled = editorWindow?.tabbedPane?.tabs?.selectedInfo != null
    }
}

class MoveTabLeftAction : MoveTabAction(MoveTabDirection.Left)
class MoveTabRightAction : MoveTabAction(MoveTabDirection.Right)
class MoveTabToFirstAction : MoveTabAction(MoveTabDirection.First)
class MoveTabToLastAction : MoveTabAction(MoveTabDirection.Last)
