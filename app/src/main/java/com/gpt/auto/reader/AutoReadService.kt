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

        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1500) return

        val dumpSb = StringBuilder()
        recursiveDump(rootNode, dumpSb, 0)
        DebugLogger.log("UI DUMP", dumpSb.toString())

        if (isStillGenerating(rootNode)) {
            DebugLogger.log("DECISION", "Aborting: Stop button detected (Still Generating)")
            return
        }

        findAndTriggerReadAloud(rootNode)
    }

    private fun isStillGenerating(root: AccessibilityNodeInfo): Boolean {
        val stopNodes = root.findAccessibilityNodeInfosByViewId("com.openai.chatgpt:id/stop_generating_button")
        if (stopNodes.isNotEmpty()) return true
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
        findAllReadAloudNodes(rootNode, clickableNodes)

        if (clickableNodes.isEmpty()) return

        // Get the bottom-most button
        val targetNode = clickableNodes.maxByOrNull { 
            val rect = android.graphics.Rect()
            it.getBoundsInScreen(rect)
            rect.bottom 
        } ?: return

        val messageText = findSiblingText(targetNode)
        val fingerprint = messageText.hashCode()

        if (messageText.isBlank()) {
            DebugLogger.log("TRACE", "Found button but message text is blank. Skipping.")
            return
        }

        DebugLogger.log("TRACE", "Testing Message: [${messageText.take(20)}...] Hash: $fingerprint")

        if (processedFingerprints.contains(fingerprint)) {
            DebugLogger.log("DECISION", "Skip: Hash $fingerprint already processed.")
            return
        }

        // Execute Click
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (success) {
            processedFingerprints.add(fingerprint)
            if (processedFingerprints.size > 15) {
                val first = processedFingerprints.iterator().next()
                processedFingerprints.remove(first)
            }
            lastClickTime = System.currentTimeMillis()
            DebugLogger.log("ACTION", "SUCCESS: Tapped Read Aloud for Hash: $fingerprint")
        } else {
            DebugLogger.log("ACTION", "FAILURE: System REJECTED click for Hash: $fingerprint")
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
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isClickable) list.add(child)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllReadAloudNodes(child, list)
        }
    }

    private fun findSiblingText(buttonNode: AccessibilityNodeInfo): String {
        var current: AccessibilityNodeInfo? = buttonNode
        repeat(4) {
            current = current?.parent
            val text = collectAllTextFromNode(current)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun collectAllTextFromNode(node: AccessibilityNodeInfo?): String {
        val sb = StringBuilder()
        internalCollectText(node, sb)
        return sb.toString().trim()
    }

    private fun internalCollectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && node.className?.contains("Button") == false) {
            sb.append(text).append(" ")
        }
        for (i in 0 until node.childCount) {
            internalCollectText(node.getChild(i), sb)
        }
    }

    private fun recursiveDump(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null) return
        val indent = "  ".repeat(depth)
        val desc = node.contentDescription?.toString() ?: "null"
        val text = node.text?.toString() ?: "null"
        val id = node.viewIdResourceName ?: "null"
        val className = node.className?.toString()?.substringAfterLast('.') ?: "unknown"
        val clickable = if (node.isClickable) "C" else "_"
        val visible = if (node.isVisibleToUser) "V" else "_"
        
        sb.append("$indent[$className] [$clickable$visible] ID: $id | TXT: $text | DESC: $desc\n")

        for (i in 0 until node.childCount) {
            recursiveDump(node.getChild(i), sb, depth + 1)
        }
    }

    override fun onInterrupt() {}
}