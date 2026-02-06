package com.gpt.auto.reader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class AutoReadService : AccessibilityService() {

    private val processedFingerprints = LinkedHashSet<Int>()
    private var lastClickTime: Long = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Check if ChatGPT is still "typing" (Stop button is visible)
        if (isStillGenerating(rootNode)) return

        // 2. Throttling
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 2000) return

        findAndTriggerReadAloud(rootNode)
    }

    private fun isStillGenerating(root: AccessibilityNodeInfo): Boolean {
        // Look for the "Stop" button description
        val stopNodes = root.findAccessibilityNodeInfosByViewId("com.openai.chatgpt:id/stop_generating_button")
        if (stopNodes.isNotEmpty()) return true
        
        // Fallback fuzzy search for "Stop"
        return findNodeByDescription(root, "Stop")
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo, query: String): Boolean {
        if (node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            if (findNodeByDescription(node.getChild(i) ?: continue, query)) return true
        }
        return false
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

        val fingerprint = messageText.hashCode()
        if (!processedFingerprints.contains(fingerprint) && messageText.isNotBlank()) {
            if (targetNode.isClickable) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                
                // Add to history and keep only last 10 entries
                processedFingerprints.add(fingerprint)
                if (processedFingerprints.size > 10) {
                    val first = processedFingerprints.iterator().next()
                    processedFingerprints.remove(first)
                }

                lastClickTime = System.currentTimeMillis()
                Log.d("AutoReader", "Triggered click for new message hash: $fingerprint")
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