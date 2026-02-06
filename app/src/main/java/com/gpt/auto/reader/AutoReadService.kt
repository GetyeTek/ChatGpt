package com.gpt.auto.reader

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.graphics.Rect

class AutoReadService : AccessibilityService() {

    private val processedSignatures = LinkedHashSet<String>()
    private var lastClickTime: Long = 0
    private var lastScrollTime: Long = 0
    private var lastScrollHash: Int = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        // LOCKDOWN: Only process if the active window belongs to ChatGPT
        if (rootNode.packageName != "com.openai.chatgpt") return

        val now = System.currentTimeMillis()
        if (now - lastClickTime < 1500) return

        val dumpSb = StringBuilder()
        recursiveDump(rootNode, dumpSb, 0)
        DebugLogger.log("UI DUMP", dumpSb.toString())

        if (isStillGenerating(rootNode)) {
            DebugLogger.log("DECISION", "Aborting: Stop button detected (Still Generating)")
            return
        }

        val found = findAndTriggerReadAloud(rootNode)
        if (!found) {
            attemptScroll(rootNode)
        }
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

    private fun findAndTriggerReadAloud(rootNode: AccessibilityNodeInfo): Boolean {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        findAllReadAloudNodes(rootNode, clickableNodes)

        if (clickableNodes.isEmpty()) return false

        // Get the bottom-most button to ensure it's the latest message
        val targetNode = clickableNodes.maxByOrNull { 
            val rect = Rect()
            it.getBoundsInScreen(rect)
            rect.bottom 
        } ?: return false

        val messageText = findSiblingText(targetNode)
        if (messageText.isBlank()) return false

        // Create a robust signature: [Length] + [Start] + [End]
        val signature = "${messageText.length}_${messageText.take(60)}_${messageText.takeLast(60)}"

        // COORDINATE FILTER: Only accept buttons in the lower half of the screen
        val rect = Rect()
        targetNode.getBoundsInScreen(rect)
        val screenHeight = resources.displayMetrics.heightPixels
        val isLowerHalf = rect.bottom > (screenHeight * 0.6) // Must be in bottom 40%

        if (!isLowerHalf) {
            DebugLogger.log("DECISION", "Skip: Button too high on screen (History node)")
            return false
        }

        if (processedSignatures.contains(signature)) {
            return false
        }

        // Execute Click
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (success) {
            processedSignatures.add(signature)
            if (processedSignatures.size > 20) {
                val first = processedSignatures.iterator().next()
                processedSignatures.remove(first)
            }
            lastClickTime = System.currentTimeMillis()
            DebugLogger.log("ACTION", "SUCCESS: Tapped Read Aloud. Sig: ${messageText.take(20)}...")
            return true
        } else {
            DebugLogger.log("ACTION", "FAILURE: System REJECTED click for Sig: ${messageText.take(20)}")
            return false
        }
    }

    private fun attemptScroll(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < 1200) return

        val currentUIHash = collectAllTextFromNode(root).hashCode()
        if (currentUIHash == lastScrollHash) {
            // We haven't moved since last scroll, likely reached the end
            return
        }

        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            val success = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (success) {
                lastScrollTime = now
                lastScrollHash = currentUIHash
                DebugLogger.log("SCROLL", "Button not visible. Scrolling down to reveal...")
            }
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private val VOICE_KEYWORDS = arrayOf("read aloud", "speak", "listen", "audio", "playback")
    private val BLACKLIST_KEYWORDS = arrayOf("voice conversation", "dictation", "headphones", "keyboard")

    private fun findAllReadAloudNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val description = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        val isBlacklisted = BLACKLIST_KEYWORDS.any { description.contains(it) || text.contains(it) }
        if (isBlacklisted) return

        val isVoiceMatch = VOICE_KEYWORDS.any { description.contains(it) || text.contains(it) }
        val isIdMatch = resId.contains("read") || resId.contains("audio") || resId.contains("speak")

        if (isVoiceMatch || isIdMatch) {
            // Find the nearest clickable ancestor or the node itself
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            
            if (target != null && target.isClickable && !list.contains(target)) {
                list.add(target)
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
