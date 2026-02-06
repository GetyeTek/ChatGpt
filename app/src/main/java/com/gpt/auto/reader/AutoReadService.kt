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

    private val VOICE_KEYWORDS = arrayOf("read", "aloud", "speak", "listen", "voice", "audio", "playback")

    private fun findAllReadAloudNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val description = node.contentDescription?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        // Fuzzy Match: Check if the description contains ANY of our voice keywords
        val isVoiceMatch = VOICE_KEYWORDS.any { description.contains(it) }
        val isIdMatch = resId.contains("read") || resId.contains("audio") || resId.contains("speak")

        if ((isVoiceMatch || isIdMatch) && node.isClickable) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllReadAloudNodes(child, list)
        }
    }

    private fun findSiblingText(buttonNode: AccessibilityNodeInfo?): String {
        var current = buttonNode
        // Strategy: Move up the tree to find the common container for the message
        // Usually, the message text and buttons are within 3 levels of each other
        repeat(3) {
            current = current?.parent
            val text = findTextInHierarchy(current)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun findTextInHierarchy(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        
        // If this node has text, and it's not a button label, return it
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && node.classNamesWithoutPackage() != "android.widget.Button") {
            return text
        }

        for (i in 0 until node.childCount) {
            val found = findTextInHierarchy(node.getChild(i))
            if (found.isNotBlank()) return found
        }
        return ""
    }

    private fun AccessibilityNodeInfo.classNamesWithoutPackage(): String {
        return this.className?.toString()?.substringAfterLast('.') ?: ""
    }

    override fun onInterrupt() {}
}