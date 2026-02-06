package com.gpt.auto.reader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class AutoReadService : AccessibilityService() {

    private var lastProcessedText: String = ""
    private var lastClickTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only scan when the UI changes within ChatGPT
        val rootNode = rootInActiveWindow ?: return

        // Throttling: Don't scan more than once every 1.5 seconds to save battery
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1500) return

        findAndTriggerReadAloud(rootNode)
    }

    private fun findAndTriggerReadAloud(rootNode: AccessibilityNodeInfo) {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // 1. Collect all potential "Read Aloud" buttons on screen
        findAllReadAloudNodes(rootNode, clickableNodes)

        // 2. We want the NEWEST message, which is at the bottom (highest Y coordinate)
        val targetNode = clickableNodes.maxByOrNull { 
            val rect = android.graphics.Rect()
            it.getBoundsInScreen(rect)
            rect.bottom 
        } ?: return

        // 3. Prevent Duplicate Clicks
        val parent = targetNode.parent
        val messageText = findSiblingText(parent)

        if (messageText != lastProcessedText && messageText.isNotBlank()) {
            if (targetNode.isClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                lastProcessedText = messageText
                lastClickTime = System.currentTimeMillis()
                Log.d("AutoReader", "Triggered latest button for text: ${messageText.take(20)}...")
            }
        }
    }

    private fun findAllReadAloudNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val description = node.contentDescription?.toString() ?: ""
        val resId = node.viewIdResourceName ?: ""

        // Match by label OR by specific Resource ID if description fails
        if (description.contains("Read Aloud", ignoreCase = true) || 
            resId.contains("read_aloud_button", ignoreCase = true)) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllReadAloudNodes(child, list)
        }
    }

    private fun findSiblingText(parent: AccessibilityNodeInfo?): String {
        if (parent == null) return ""
        // Simple logic: the message text is usually a sibling of the read aloud button
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            val text = child.text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return ""
    }

    override fun onInterrupt() {}
}