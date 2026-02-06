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

    private fun findAndTriggerReadAloud(node: AccessibilityNodeInfo) {
        // ChatGPT uses Content Description for its icons.
        // We look for the one labeled "Read Aloud".
        val description = node.contentDescription?.toString() ?: ""
        
        if (description.contains("Read Aloud", ignoreCase = true)) {
            // To avoid repeating the same message, we look at the message text near the button
            val parent = node.parent
            val messageText = findSiblingText(parent)

            if (messageText != lastProcessedText && messageText.isNotBlank()) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    lastProcessedText = messageText
                    lastClickTime = System.currentTimeMillis()
                    Log.d("AutoReader", "Triggered Read Aloud for: ${messageText.take(20)}...")
                }
            }
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAndTriggerReadAloud(child)
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