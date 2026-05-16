package com.tvremote.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that powers the D-pad navigation.
 *
 * Navigation strategy:
 *  1. Find the currently focused node (input focus first, then a11y focus).
 *  2. Ask Android where the next focusable node is in the requested direction
 *     using [AccessibilityNodeInfo.focusSearch].
 *  3. Request focus on that node.
 *
 * This mirrors exactly what a hardware D-pad does, so it works correctly with
 * any Android TV app that uses standard focus-based navigation.
 *
 * All methods are exposed as static calls on the companion object so that
 * [OverlayService] can call them without needing a bound service connection.
 * Both services run in the same process, so the companion object reference
 * is always valid while the accessibility service is connected.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: RemoteAccessibilityService? = null

        /**
         * Move D-pad focus in [direction].
         * @param direction One of [View.FOCUS_UP], [View.FOCUS_DOWN],
         *                  [View.FOCUS_LEFT], [View.FOCUS_RIGHT].
         */
        fun navigate(direction: Int) {
            instance?.doNavigate(direction)
        }

        /** Click / select the currently focused element. */
        fun performClick() {
            instance?.doClick()
        }

        /** Press Back. */
        fun performBack() {
            instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        /** Press Home. */
        fun performHome() {
            instance?.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        /** Open Recents (used as Menu in the overlay). */
        fun performMenu() {
            instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        /** Returns true if the service is currently connected. */
        fun isConnected(): Boolean = instance != null
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        instance = this
        // Enable window content retrieval so focusSearch works across the whole screen
        serviceInfo = serviceInfo?.apply {
            flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events; we act on button presses from the overlay.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ─── Navigation logic ─────────────────────────────────────────────────────

    private fun doNavigate(direction: Int) {
        val root = rootInActiveWindow ?: return

        // Prefer input focus (the node that actually has keyboard focus in the TV app).
        // Fall back to accessibility focus if input focus is absent.
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focused != null) {
            val next = focused.focusSearch(direction)
            if (next != null) {
                // Set accessibility focus (highlights the element) AND input focus
                next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                next.recycle()
            } else {
                // Already at an edge; wrap around to the first focusable element
                val first = findFirstFocusable(root)
                first?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                first?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                first?.recycle()
            }
            focused.recycle()
        } else {
            // Nothing is focused yet — focus the first focusable element on screen
            val first = findFirstFocusable(root)
            first?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            first?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            first?.recycle()
        }

        root.recycle()
    }

    private fun doClick() {
        val root = rootInActiveWindow ?: return

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        focused?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            it.recycle()
        }

        root.recycle()
    }

    /**
     * Depth-first search for the first focusable node in the view tree.
     * Used when no element currently has focus.
     */
    private fun findFirstFocusable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocusable && node.isVisibleToUser && node.isEnabled) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstFocusable(child)
            if (result != null) {
                if (result != child) child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }
}
