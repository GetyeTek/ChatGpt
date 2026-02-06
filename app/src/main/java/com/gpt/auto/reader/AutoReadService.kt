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

        // Throttling for logs - don't spam dump every millisecond
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1000) return

        DebugLogger.log("SCAN", "--- NEW SCAN START ---")
        
        // DEBUG: Dump EVERYTHING seen on screen
        val dumpSb = StringBuilder()
        recursiveDump(rootNode, dumpSb, 0)
        DebugLogger.log("UI DUMP", dumpSb.toString())

        if (isStillGenerating(rootNode)) {
            DebugLogger.log("DECISION", "Aborting: Stop button detected (Still Generating)")
            return
        }

        findAndTriggerReadAloud(rootNode)
    }

    private fun recursiveDump(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val desc = node.contentDescription?.toString() ?: "null"
        val text = node.text?.toString() ?: "null"
        val id = node.viewIdResourceName ?: "null"
        val className = node.className?.toString()?.substringAfterLast('.') ?: "unknown"
        val clickable = if (node.isClickable) "CLK: true" else "CLK: false"
        
        sb.append("$indent[$className] $clickable | ID: $id | TXT: $text | DESC: $desc\n")

        for (i in 0 until node.childCount) {
            recursiveDump(node.getChild(i), sb, depth + 1)
        }
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
                DebugLogger.log("ACTION", "SUCCESS: Tapped Read Aloud. Hash: $fingerprint")
            } else {
                DebugLogger.log("DECISION", "Target node found but NOT clickable.")
            }
        } else if (messageText.isBlank()) {
            DebugLogger.log("DECISION", "Skip: Found button but could not extract sibling text.")
        } else {
            DebugLogger.log("DECISION", "Skip: Message hash $fingerprint already processed.")
        }
    }

    private val VOICE_KEYWORDS = arrayOf("read", "aloud", "speak", "listen", "voice", "audio", "playback")

    private fun findAllReadAloudNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val description = node.contentDescription?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        val isVoiceMatch = VOICE_KEYWORDS.any { description.contains(it) }
        val isIdMatch = resId.contains("read") || resId.contains("audio") || resId.contains("speak")

        if (isVoiceMatch || isIdMatch) {
            if (node.isClickable) {
                list.add(node)
            } else {
                // Logic for nested buttons: If the container has the description, check children for the button
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isClickable || child.className?.contains("Button") == true) {
                        list.add(child)
                    }
                }
            }
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
        val sb = StringBuilder()
        collectAllText(node, sb)
        return sb.toString().trim()
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && node.className?.contains("Button") == false) {
            sb.append(text).append(" ")
        }

        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), sb)
        }
    }

    private fun AccessibilityNodeInfo.classNamesWithoutPackage(): String {
        return this.className?.toString()?.substringAfterLast('.') ?: ""
    }

    override fun onInterrupt() {}
}