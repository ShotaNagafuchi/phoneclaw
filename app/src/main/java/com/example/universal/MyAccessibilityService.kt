package com.example.universal

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.widget.ScrollView
import com.example.universal.edge.autonomous.AutonomousAgent
import com.example.universal.edge.inference.EmotionAction
import com.example.universal.edge.inference.EmotionOutput
import com.example.universal.edge.inference.EmotionType
import java.util.Locale
// Put this _inside_ your MyAccessibilityService companion object, or at top level
// so you only compute it once.
private val ACTION_IME_ENTER_COMPAT: Int by lazy {
    try {
        // API 33‑ext5+ has public AccessibilityNodeInfo.ACTION_IME_ENTER
        AccessibilityNodeInfo::class.java
            .getField("ACTION_IME_ENTER")
            .getInt(null)
    } catch (e: Throwable) {
        // Fallback literal (same bit flag Android uses internally)
        0x00002000
    }
}

/**
 * This AccessibilityService performs automation tasks like clicking nodes,
 * scrolling, or typing, without dealing with MediaProjection.
 * Any code for screen capture / MediaProjection has been removed.
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
            private set
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Observable state (for settings UI)
    // ─────────────────────────────────────────────────────────────────────────────
    var currentPackageName: String? = null
        private set
    var lastEmotionLabel: String? = null
        private set

    // ─────────────────────────────────────────────────────────────────────────────
    // Overlay eye
    // ─────────────────────────────────────────────────────────────────────────────
    private var overlayView: EmotionOrbView? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // ─────────────────────────────────────────────────────────────────────────────
    // Autonomous Agent
    // ─────────────────────────────────────────────────────────────────────────────
    private var autonomousAgent: AutonomousAgent? = null

    fun toggleAutonomousMode() {
        if (autonomousAgent?.isRunning == true) {
            autonomousAgent?.stop()
            autonomousAgent = null
            // オーブをCALMに戻す
            overlayView?.setEmotion(
                EmotionOutput(
                    selectedAction = EmotionAction(EmotionType.CALM, 0.5f),
                    confidence = 1f,
                    reasoning = "autonomous_off",
                    fallbackUsed = false
                )
            )
            Log.d("MyAccessibilityService", "Autonomous mode OFF")
        } else {
            autonomousAgent = AutonomousAgent(this)
            autonomousAgent?.start()
            // オーブをEXCITEMENTに変更（自律モード表示）
            overlayView?.setEmotion(
                EmotionOutput(
                    selectedAction = EmotionAction(EmotionType.EXCITEMENT, 0.8f),
                    confidence = 1f,
                    reasoning = "autonomous_on",
                    fallbackUsed = false
                )
            )
            Log.d("MyAccessibilityService", "Autonomous mode ON")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Lifecycle Methods
    // ─────────────────────────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setupOverlay()
        Log.w("MyAccessibilityService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let { currentPackageName = it }
    }

    override fun onInterrupt() {
        // Called when the accessibility service is interrupted
    }

    override fun onDestroy() {
        autonomousAgent?.stop()
        autonomousAgent = null
        removeOverlay()
        super.onDestroy()
        instance = null
        Log.w("MyAccessibilityService", "Accessibility Service Destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Overlay management
    // ─────────────────────────────────────────────────────────────────────────────
    private fun setupOverlay() {
        if (overlayView != null) return
        try {
            overlayWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val size = (80 * resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = resources.displayMetrics.widthPixels - size - 16
                y = 100
            }
            overlayParams = params

            val orb = EmotionOrbView(this)
            orb.onDragListener = { dx, dy ->
                overlayParams?.let { p ->
                    p.x += dx
                    p.y += dy
                    try {
                        overlayWindowManager?.updateViewLayout(orb, p)
                    } catch (_: Exception) {}
                }
            }
            orb.onTapListener = { toggleAutonomousMode() }

            overlayView = orb
            overlayWindowManager?.addView(orb, params)
            Log.d("MyAccessibilityService", "Overlay eye created")
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to create overlay: ${e.message}")
        }
    }

    fun updateEyeEmotion(output: EmotionOutput) {
        lastEmotionLabel = "${output.selectedAction.type.name} (${String.format("%.1f", output.selectedAction.intensity)})"
        overlayView?.setEmotion(output)
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { overlayWindowManager?.removeView(it) }
            overlayView = null
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error removing overlay: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Utility methods for interacting with UI nodes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Checks if any node on the screen contains the specified text in its content description or text.
     * @param searchText The text to search for (case-insensitive).
     * @return true if any node contains the text, false otherwise.
     */
    fun isTextPresentOnScreen(searchText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val lowerSearchText = searchText.lowercase(Locale.getDefault())

        return checkNodeForText(root, lowerSearchText)
    }

    // Add this to your MyAccessibilityService class
    fun simulateSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        try {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, 500) // 500ms duration
            gestureBuilder.addStroke(strokeDescription)

            val gesture = gestureBuilder.build()
            dispatchGesture(gesture, null, null)

            Log.d("MyAccessibilityService", "Simulated swipe from ($startX, $startY) to ($endX, $endY)")
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error simulating swipe: ${e.message}")
        }
    }

    // Add this to your MyAccessibilityService class
    fun getAllTextFromScreen(): String {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w("MyAccessibilityService", "Root node is null")
                return "No screen content available"
            }

            val textBuilder = StringBuilder()
            extractTextFromNode(rootNode, textBuilder)

            val result = textBuilder.toString().trim()
            Log.d("MyAccessibilityService", "Extracted text: ${result.take(100)}...")

            return if (result.isNotEmpty()) result else "No text found on screen"
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error getting all text from screen: ${e.message}")
            return "Error extracting screen text"
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, textBuilder: StringBuilder) {
        try {
            // Get text from this node
            val nodeText = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            // Add text if it exists
            if (!nodeText.isNullOrEmpty()) {
                textBuilder.append(nodeText).append(" ")
            }

            // Add content description if it exists and is different from text
            if (!contentDesc.isNullOrEmpty() && contentDesc != nodeText) {
                textBuilder.append(contentDesc).append(" ")
            }

            // Recursively extract text from child nodes
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    extractTextFromNode(child, textBuilder)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error extracting text from node: ${e.message}")
        }
    }
    /**
     * Find toggle elements near text containing specific keywords
     */
    fun findToggleNearText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null

        fun searchNodes(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // Check if this node contains the text
            val nodeText = node.text?.toString() ?: ""
            val nodeContentDesc = node.contentDescription?.toString() ?: ""

            if (nodeText.contains(text, ignoreCase = true) ||
                nodeContentDesc.contains(text, ignoreCase = true)) {

                // Look for sibling or nearby toggle elements
                val parent = node.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i)
                        if (child != null && isToggleElement(child)) {
                            return child
                        }
                    }
                }
            }

            // Recursively search child nodes
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = searchNodes(child)
                    if (result != null) return result
                }
            }

            return null
        }

        return searchNodes(rootNode)
    }

    /**
     * Check if a node is a toggle element (switch, checkbox, etc.)
     */
    private fun isToggleElement(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val toggleClasses = listOf(
            "android.widget.Switch",
            "android.widget.CheckBox",
            "android.widget.ToggleButton",
            "android.widget.CompoundButton"
        )

        return toggleClasses.any { className.contains(it) } || node.isCheckable
    }

    /**
     * Click any clickable element at approximate coordinates
     */
    fun clickAtApproximateCoordinates(x: Float, y: Float, tolerance: Float = 50f): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        fun findClickableNearPosition(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isClickable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val centerX = rect.centerX().toFloat()
                val centerY = rect.centerY().toFloat()

                val distance = kotlin.math.sqrt(
                    (centerX - x) * (centerX - x) + (centerY - y) * (centerY - y)
                )

                if (distance <= tolerance) {
                    return node
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findClickableNearPosition(child)
                    if (result != null) return result
                }
            }

            return null
        }

        val targetNode = findClickableNearPosition(rootNode)
        return if (targetNode != null) {
            performNodeClick(targetNode)
            true
        } else {
            false
        }
    }


    // Add this to your MyAccessibilityService class if not already present
    fun enterTextInField(text: String) {
        // Find input field and enter text
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateTypeByClass(): rootInActiveWindow is null.")
            return
        }
        val editTextNodes = findNodesWithClass(root,"android.widget.EditText")
        editTextNodes.forEach { node ->
            if (node.isEditable) {
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                return
            }
        }
    }
    /**
     * Tap the very first thumbnail in the TikTok gallery.
     *
     * ①  Try the known GridView IDs:
     *        • h4i  → current builds
     *        • h3g  → legacy builds
     *        • h0f  → **new “Select multiple” layout (2024)**
     * ②  If none of the IDs are present, fall back to the first <GridView> we
     *     encounter in a breadth‑first walk of the tree.
     * ③  Grab the GridView’s first child (a clickable `FrameLayout`)
     * ④  Click it semantically (`performNodeClick`), else bubble up to the nearest
     *     clickable parent, else fire a gesture‑tap at its centre.
     */
    fun clickFirstGalleryItem() {
        val root = rootInActiveWindow ?: return

        /* ── 1. Locate the GridView that holds the thumbnails ──────────────────── */
        val gridIds = arrayOf(
            "com.zhiliaoapp.musically:id/h4i", // current
            "com.zhiliaoapp.musically:id/h3g", // legacy
            "com.zhiliaoapp.musically:id/h0f"  // **new layout**
        )

        var grid: AccessibilityNodeInfo? = null
        for (id in gridIds) {
            grid = root.findAccessibilityNodeInfosByViewId(id).firstOrNull()
            if (grid != null) break
        }

        /* last‑chance: BFS for the first <GridView> in the tree */
        if (grid == null) {
            val q: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
            q.add(root)
            while (q.isNotEmpty()) {
                val n = q.removeFirst()
                if (n.className == "android.widget.GridView") {
                    grid = n
                    break
                }
                repeat(n.childCount) { i -> n.getChild(i)?.let(q::add) }
            }
        }
        if (grid == null || grid.childCount == 0) return

        /* ── 2. First gallery cell ─────────────────────────────────────────────── */
        val firstCell = grid.getChild(0) ?: return

        /* ── 3. Primary attempt: semantic click ────────────────────────────────── */
        performNodeClick(firstCell)

        /* ── 4. Secondary attempt: bubble‑up if needed ─────────────────────────── */
        var parent: AccessibilityNodeInfo? = firstCell.parent
        while (parent is AccessibilityNodeInfo && !parent.isClickable) {
            parent = parent.parent
        }
        if (parent is AccessibilityNodeInfo && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        /* ── 5. Last‑ditch fallback: gesture‑tap at the centre ─────────────────── */
        val bounds = Rect().also { firstCell.getBoundsInScreen(it) }
        simulateClick(bounds.exactCenterX(), bounds.exactCenterY())
    }

    /**
     * Clicks the profile menu button element using specific filters:
     * - No resource id
     * - No content description
     * - Y bounds less than 200
     * - Same area as bounds [629,91][706,161] (77x70 = 5390 pixels)
     */
    fun clickProfileMenuButton() {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickProfileMenuButton: root is null")
            return
        }

        val profileMenuNode = findFirstProfileMenuButton(root)
        if (profileMenuNode == null) {
            Log.w("MyAccessibilityService", "clickProfileMenuButton: No profile menu button found matching criteria")
            return
        }

        // Try to click the node or its clickable parent, then fallback to center tap
        if (clickNodeOrParent(profileMenuNode)) {
            Log.d("MyAccessibilityService", "clickProfileMenuButton: Successfully clicked profile menu button")
        } else {
            simulateNodeCenterTap(profileMenuNode)
            Log.d("MyAccessibilityService", "clickProfileMenuButton: Clicked center of bounds for profile menu button")
        }
    }

    /**
     * Recursively searches for the first node that matches the profile menu button criteria:
     * - No resource id (viewIdResourceName is null or empty)
     * - No content description (contentDescription is null or empty)
     * - Y bounds less than 200 (top of bounds < 200)
     * - Same area as bounds [629,91][706,161] (77x70 = 5390 pixels)
     */
    private fun findFirstProfileMenuButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // Get the bounds of the current node
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // Calculate the area of the current node
            val nodeWidth = bounds.width()
            val nodeHeight = bounds.height()
            val nodeArea = nodeWidth * nodeHeight

            // Target area from bounds [629,91][706,161]: 77x70 = 5390
            val targetArea = 5390

            // Check all four filter criteria
            val resourceId = node.viewIdResourceName
            val contentDesc = node.contentDescription?.toString()

            // Filter: no resource id, no content description, y bounds < 200, and matching area
            if (resourceId.isNullOrEmpty() &&
                contentDesc.isNullOrEmpty() &&
                bounds.top < 200 &&
                nodeArea == targetArea) {
                Log.d("MyAccessibilityService", "Found profile menu button candidate at bounds: $bounds (${nodeWidth}x${nodeHeight}, area: $nodeArea), class: ${node.className}")
                return node
            }

            // Recursively check children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = findFirstProfileMenuButton(child)
                    if (result != null) return result
                }
            }
        } catch (e: Exception) {
            Log.w("MyAccessibilityService", "Error processing node in findFirstProfileMenuButton: $e")
        }

        return null
    }
    /**
     * Finds and taps the “video upload” button next to the camera capture control.
     * View‑ID: com.zhiliaoapp.musically:id/cqf
     *//**
     * Taps the upload‑from‑gallery icon (to the right of the record button).
     * View‑ID: com.zhiliaoapp.musically:id/c0t
     */
    fun clickElementByArea(targetArea: Int) {
        val root = rootInActiveWindow ?: return

        // Helper – try to click the node itself, then its clickable parent, then its centre
        fun tryClick(node: AccessibilityNodeInfo?): Boolean =
            node != null && (clickNodeOrParent(node) || simulateNodeCenterTap(node))

        // Find all clickable elements with the specified area
        fun findClickableElementsByArea(node: AccessibilityNodeInfo?, area: Int): List<AccessibilityNodeInfo> {
            val results = mutableListOf<AccessibilityNodeInfo>()

            fun traverse(currentNode: AccessibilityNodeInfo?) {
                if (currentNode == null) return

                try {
                    // Get the bounds of the current node
                    val bounds = android.graphics.Rect()
                    currentNode.getBoundsInScreen(bounds)

                    val nodeWidth = bounds.width()
                    val nodeHeight = bounds.height()
                    val nodeArea = nodeWidth * nodeHeight

                    // Check if this node is clickable and has the target area
                    if ((currentNode.isClickable || currentNode.actionList.any {
                            it.id == AccessibilityNodeInfo.ACTION_CLICK
                        }) && nodeArea == area) {
                        results.add(currentNode)
                    }

                    // Recursively check children
                    for (i in 0 until currentNode.childCount) {
                        traverse(currentNode.getChild(i))
                    }
                } catch (e: Exception) {
                    // Continue traversal even if one node fails
                    Log.w("clickElementByArea", "Error processing node: $e")
                }
            }

            traverse(node)
            return results
        }

        // Find all clickable elements with the target area
        val matchingElements = findClickableElementsByArea(root, targetArea)

        if (matchingElements.isNotEmpty()) {
            Log.d("clickElementByArea", "Found ${matchingElements.size} clickable elements with area $targetArea")

            // Try to click the first matching element
            val elementToClick = matchingElements.first()
            if (tryClick(elementToClick)) {
                Log.d("clickElementByArea", "Successfully clicked element with area $targetArea")
                return
            }
        }

        Log.d("clickElementByArea", "No clickable elements found with area $targetArea")
    }

    // Overloaded version that accepts width and height separately
    fun clickElementByDimensions(width: Int, height: Int) {
        val targetArea = width * height
        clickElementByArea(targetArea)
    }

    // Version that finds elements within an area range
    fun clickElementByAreaRange(minArea: Int, maxArea: Int) {
        val root = rootInActiveWindow ?: return

        fun tryClick(node: AccessibilityNodeInfo?): Boolean =
            node != null && (clickNodeOrParent(node) || simulateNodeCenterTap(node))

        fun findClickableElementsByAreaRange(node: AccessibilityNodeInfo?, min: Int, max: Int): List<AccessibilityNodeInfo> {
            val results = mutableListOf<AccessibilityNodeInfo>()

            fun traverse(currentNode: AccessibilityNodeInfo?) {
                if (currentNode == null) return

                try {
                    val bounds = android.graphics.Rect()
                    currentNode.getBoundsInScreen(bounds)

                    val nodeArea = bounds.width() * bounds.height()

                    if ((currentNode.isClickable || currentNode.actionList.any {
                            it.id == AccessibilityNodeInfo.ACTION_CLICK
                        }) && nodeArea in min..max) {
                        results.add(currentNode)
                    }

                    for (i in 0 until currentNode.childCount) {
                        traverse(currentNode.getChild(i))
                    }
                } catch (e: Exception) {
                    Log.w("clickElementByAreaRange", "Error processing node: $e")
                }
            }

            traverse(node)
            return results
        }

        val matchingElements = findClickableElementsByAreaRange(root, minArea, maxArea)

        if (matchingElements.isNotEmpty()) {
            Log.d("clickElementByAreaRange", "Found ${matchingElements.size} clickable elements with area between $minArea and $maxArea")

            // Try to click the first matching element
            if (tryClick(matchingElements.first())) {
                Log.d("clickElementByAreaRange", "Successfully clicked element in area range")
                return
            }
        }

        Log.d("clickElementByAreaRange", "No clickable elements found in area range $minArea-$maxArea")
    }

    // Version that clicks all elements with the target area (useful for debugging)
    fun clickAllElementsByArea(targetArea: Int) {
        val root = rootInActiveWindow ?: return

        fun tryClick(node: AccessibilityNodeInfo?): Boolean =
            node != null && (clickNodeOrParent(node) || simulateNodeCenterTap(node))

        fun findClickableElementsByArea(node: AccessibilityNodeInfo?, area: Int): List<AccessibilityNodeInfo> {
            val results = mutableListOf<AccessibilityNodeInfo>()

            fun traverse(currentNode: AccessibilityNodeInfo?) {
                if (currentNode == null) return

                try {
                    val bounds = android.graphics.Rect()
                    currentNode.getBoundsInScreen(bounds)

                    val nodeArea = bounds.width() * bounds.height()

                    if ((currentNode.isClickable || currentNode.actionList.any {
                            it.id == AccessibilityNodeInfo.ACTION_CLICK
                        }) && nodeArea == area) {
                        results.add(currentNode)
                    }

                    for (i in 0 until currentNode.childCount) {
                        traverse(currentNode.getChild(i))
                    }
                } catch (e: Exception) {
                    Log.w("clickAllElementsByArea", "Error processing node: $e")
                }
            }

            traverse(node)
            return results
        }

        val matchingElements = findClickableElementsByArea(root, targetArea)

        Log.d("clickAllElementsByArea", "Found ${matchingElements.size} clickable elements with area $targetArea")

        matchingElements.forEachIndexed { index, element ->
            if (tryClick(element)) {
                Log.d("clickAllElementsByArea", "Successfully clicked element #${index + 1} with area $targetArea")
            } else {
                Log.w("clickAllElementsByArea", "Failed to click element #${index + 1} with area $targetArea")
            }

            // Add small delay between clicks to avoid issues
            Thread.sleep(500)
        }
    }
    fun clickAddSound() {
        val root = rootInActiveWindow ?: return

        // 1) Grab the gallery‑upload node by its real ID
        val uploads = root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/v4v")
        if (uploads.isNullOrEmpty()) return
        val uploadNode = uploads[0]

        // 2) If it's directly clickable, tap it
        if (uploadNode.isClickable) {
            uploadNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // 3) Otherwise bubble up to the nearest clickable parent
        var parent = uploadNode.parent
        while (parent != null && !parent.isClickable) {
            parent = parent.parent
        }
        if (parent != null) {
            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // 4) Fallback: gesture‑tap its center
        val bounds = Rect().also { uploadNode.getBoundsInScreen(it) }
        simulateClick(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private val uPLOAD = 98          // 632‑534 or 1314‑1216
    private val uPLOAD_ALT = 63      // Alternative size for upload button
    fun clickElementByViewId(viewId: String): Boolean = clickFirstThreeByViewId(viewId)

    fun clickFirstThreeByViewId(
        viewId: String,
        maxClicks: Int = 3,
        delayMs: Long = 120L,           // small pause between taps
        dedupeByBounds: Boolean = true  // avoid double-tapping the same rect
    ): Boolean {
        val root = rootInActiveWindow ?: return false

        return try {
            val candidates = root.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()

            if (candidates.isEmpty()) {
                Log.w("clickFirstThreeByViewId", "No elements found with viewId: $viewId")
                return false
            }

            val seenRects = mutableSetOf<String>() // "left,top,right,bottom"
            var clicks = 0

            for (node in candidates) {
                if (clicks >= maxClicks) break
                if (node == null) continue

                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"

                if (dedupeByBounds && !seenRects.add(key)) {
                    // already clicked a node at these bounds
                    continue
                }

                val centerX = bounds.centerX()
                val centerY = bounds.centerY()

                Log.d(
                    "clickFirstThreeByViewId",
                    "[$clicks/${maxClicks}] Found node id=$viewId bounds=$bounds, clicking center=($centerX,$centerY)"
                )

                val tapped = simulateNodeCenterTap(node)
                if (tapped) {
                    clicks++
                    if (delayMs > 0) android.os.SystemClock.sleep(delayMs)
                } else {
                    Log.w("clickFirstThreeByViewId", "Tap failed for bounds=$bounds")
                }
            }

            Log.i("clickFirstThreeByViewId", "Clicked $clicks node(s) for viewId=$viewId")
            clicks > 0
        } catch (e: Exception) {
            Log.e("clickFirstThreeByViewId", "Error clicking nodes with viewId: $viewId", e)
            false
        }
    }


    // Version that tries multiple view IDs until one succeeds
    fun clickElementByViewIds(vararg viewIds: String): Boolean {
        for (viewId in viewIds) {
            if (clickElementByViewId(viewId)) {
                Log.d("clickElementByViewIds", "Successfully clicked element with viewId: $viewId")
                return true
            }
        }
        Log.w("clickElementByViewIds", "Failed to click any element with viewIds: ${viewIds.joinToString(", ")}")
        return false
    }

    // Version that clicks all elements with the given view ID
    fun clickAllElementsByViewId(viewId: String): Int {
        val root = rootInActiveWindow ?: return 0
        var clickedCount = 0

        try {
            // Find all nodes with the specified view ID
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)

            Log.d("clickAllElementsByViewId", "Found ${nodes.size} elements with viewId: $viewId")

            nodes.forEachIndexed { index, node ->
                try {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)

                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()

                    Log.d("clickAllElementsByViewId", "Clicking element #${index + 1} at center: ($centerX, $centerY)")

                    if (simulateNodeCenterTap(node)) {
                        clickedCount++
                        Log.d("clickAllElementsByViewId", "Successfully clicked element #${index + 1}")
                    } else {
                        Log.w("clickAllElementsByViewId", "Failed to click element #${index + 1}")
                    }

                    // Add small delay between clicks
                    Thread.sleep(300)

                } catch (e: Exception) {
                    Log.w("clickAllElementsByViewId", "Error clicking element #${index + 1}: $e")
                }
            }

        } catch (e: Exception) {
            Log.e("clickAllElementsByViewId", "Error finding elements with viewId: $viewId", e)
        }

        Log.d("clickAllElementsByViewId", "Successfully clicked $clickedCount out of total elements")
        return clickedCount
    }

    // Version that gets bounds info without clicking (useful for debugging)
    fun getElementBoundsByViewId(viewId: String): android.graphics.Rect? {
        val root = rootInActiveWindow ?: return null

        try {
            val node = root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()

            if (node != null) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)

                Log.d("getElementBoundsByViewId", "Element with viewId: $viewId has bounds: $bounds")
                Log.d("getElementBoundsByViewId", "Center would be at: (${bounds.centerX()}, ${bounds.centerY()})")
                Log.d("getElementBoundsByViewId", "Dimensions: ${bounds.width()} x ${bounds.height()}")

                return bounds
            } else {
                Log.w("getElementBoundsByViewId", "No element found with viewId: $viewId")
                return null
            }
        } catch (e: Exception) {
            Log.e("getElementBoundsByViewId", "Error getting bounds for viewId: $viewId", e)
            return null
        }
    }

    // Version that clicks with fallback to tryClick helper (like original function)
    fun clickElementByViewIdWithFallback(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Helper – try to click the node itself, then its clickable parent, then its centre
        fun tryClick(node: AccessibilityNodeInfo?): Boolean =
            node != null && (clickNodeOrParent(node) || simulateNodeCenterTap(node))

        try {
            val node = root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()

            if (tryClick(node)) {
                Log.d("clickElementByViewIdWithFallback", "Successfully clicked element with viewId: $viewId")
                return true
            } else {
                Log.w("clickElementByViewIdWithFallback", "Failed to click element with viewId: $viewId")
                return false
            }
        } catch (e: Exception) {
            Log.e("clickElementByViewIdWithFallback", "Error clicking element with viewId: $viewId", e)
            return false
        }
    }
    fun clickVideoUploadButton() {
        val root = rootInActiveWindow ?: return

        // Helper – try to click the node itself, then its clickable parent, then its centre
        fun tryClick(node: AccessibilityNodeInfo?): Boolean =
            node != null && (clickNodeOrParent(node) || simulateNodeCenterTap(node))

        // ── 1. All the quick, ID‑based attempts ─────────────────────────────────────
        if (tryClick(root.findAccessibilityNodeInfosByViewId(
                "com.zhiliaoapp.musically:id/i98").firstOrNull())) return
        if (tryClick(root.findAccessibilityNodeInfosByViewId(
                "com.zhiliaoapp.musically:id/c0t").firstOrNull())) return
        if (tryClick(root.findAccessibilityNodeInfosByViewId(
                "com.zhiliaoapp.musically:id/vgc").firstOrNull())) return
        if (tryClick(root.findAccessibilityNodeInfosByViewId(
                "com.zhiliaoapp.musically:id/j_m").firstOrNull())) return
        if (tryClick(root.findAccessibilityNodeInfosByViewId(
                "com.zhiliaoapp.musically:id/je3").firstOrNull())) return

        // ── 2. Fallback: look for a 98 × 98 px ImageView anywhere on screen ─────────
        val match98 = findSquareImage(root, uPLOAD)
        if (match98 != null) {
            simulateNodeCenterTap(match98)
            return
        }

        // ── 3. 2nd Fallback: look for a 63 × 63 px ImageView anywhere on screen ─────
        val match63 = findSquareImage(root, uPLOAD_ALT)
        match63?.let { simulateNodeCenterTap(it) }
    }
    /**
     * Depth‑first search for an ImageView whose bounds match a perfect square of the
     * given size (98 × 98 px here).
     */
    private fun findSquareImage(
        node: AccessibilityNodeInfo,
        size: Int
    ): AccessibilityNodeInfo? {
        val bounds = Rect()

        fun recurse(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            n.getBoundsInScreen(bounds)
            if (n.className == "android.widget.ImageView" &&
                bounds.width() == size && bounds.height() == size
            ) return n

            for (i in 0 until n.childCount) {
                recurse(n.getChild(i))?.let { return it }
            }
            return null
        }
        return recurse(node)
    }

    /** Click the node if clickable, otherwise its nearest clickable parent. */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(ACTION_CLICK)
        var p = node.parent
        while (p != null && !p.isClickable) p = p.parent
        return p?.performAction(ACTION_CLICK) ?: false
    }

    /** Last‑chance: gesture tap the node’s center. */
    private fun simulateNodeCenterTap(node: AccessibilityNodeInfo): Boolean {
        val r = Rect().also { node.getBoundsInScreen(it) }
        simulateClick(r.exactCenterX(), r.exactCenterY())
        return true
    }
    fun clickFirstSong() {
        // 1) find the first RecyclerView
        val root = rootInActiveWindow ?: return

        val recycler = findFirstRecyclerView(root)
        if (recycler == null || recycler.childCount < 1) return

        // 2) get the first child
        val firstItem = recycler.getChild(0) ?: return

        // 3) get the bounds and click at center
        val bounds = Rect()
        firstItem.getBoundsInScreen(bounds)

        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        Log.d("MyAccessibilityService", "Clicking first song at center: ($centerX, $centerY)")

        // 4) click at the center coordinates
        simulateClick(centerX, centerY)
    }

    private fun findFirstRecyclerView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check if current node is a RecyclerView
        val className = node.className?.toString()
        if (className == "androidx.recyclerview.widget.RecyclerView" ||
            className == "android.support.v7.widget.RecyclerView") {
            return node
        }

        // Recursively check children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findFirstRecyclerView(child)?.let { return it }
            }
        }

        return null
    }
    fun clickButtonWithLabel(label: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.e("MyAccessibilityService", "rootInActiveWindow is null!")
            return
        }

        // 1) search the entire tree for a node whose text matches label
        val hit = findNodeByText(root, label)
        if (hit == null) {
            Log.e("MyAccessibilityService", "No node with text “$label” found in tree")
            return
        }

        // 2) get its on‐screen bounds
        val bounds = Rect()
        hit.getBoundsInScreen(bounds)
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        Log.d("MyAccessibilityService", "Tapping “$label” at [${x},${y}]")

        // 3) perform the tap
        simulateClick(x, y)
    }

    /** Depth‐first search for a node whose text exactly equals [label]. */
    private fun findNodeByText(
        node: AccessibilityNodeInfo?,
        label: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        val txt = node.text?.toString()?.trim()
        if (txt == label) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByText(child, label)
            if (result != null) return result
        }
        return null
    }

    // extension to find the nearest scrollable ancestor
    private fun AccessibilityNodeInfo.parentScrollView(): AccessibilityNodeInfo? {
        var p = this.parent
        while (p != null) {
            if (p.className == ScrollView::class.java.name || p.isScrollable) {
                return p
            }
            p = p.parent
        }
        return null
    }

    /**
     * Finds the first editable text field on screen and sets its text to [inputText].
     */

    /**
     * Recursively walks the view hierarchy looking for a clickable node
     * whose contentDescription contains the given text, and clicks it.
     */
    fun clickByDesc(description: String) {
        val root = rootInActiveWindow ?: return
        findAndClick(root, description)
    }

    private fun findAndClick(node: AccessibilityNodeInfo, desc: String): Boolean {
        // match on contentDescription
        if (node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
            && node.isClickable
        ) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        // otherwise recurse
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndClick(child, desc)) return true
            }
        }
        return false
    }



    fun pressEnterKey() {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "pressEnterKey: no active window")
            return
        }

        // 1) Try to find a visible “Enter/Done/Send/Go/Search/Next” button and click it.
        val keywords = listOf("enter", "return", "done", "send", "go", "next", "search")
        fun matchesEnter(node: AccessibilityNodeInfo): Boolean {
            val t = node.text?.toString()?.lowercase() ?: ""
            val d = node.contentDescription?.toString()?.lowercase() ?: ""
            return keywords.any { t == it || d == it }
        }

        val q: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        q += root
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isClickable && matchesEnter(n)) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(q::add)
        }

        // 2) Ask the focused field to do ACTION_IME_ENTER (real constant).
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val ok = focused.performAction(ACTION_IME_ENTER_COMPAT)
            if (ok) {
                Log.d("MyAccessibilityService", "pressEnterKey: ACTION_IME_ENTER succeeded")
                return
            }
        }

        // 3) Fallback: tap where the Enter key usually is (bottom-right).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dm = resources.displayMetrics
            val x = dm.widthPixels * 0.5f
            val y = dm.heightPixels * 0.6f
            Log.d("MyAccessibilityService", "pressEnterKey: fallback tap at [$x,$y]")
            simulateClick(x, y)
        } else {
            Log.w("MyAccessibilityService", "pressEnterKey: gesture fallback needs API 24+")
        }
    }
    // Put this _inside_ your MyAccessibilityService companion object, or at top level
// so you only compute it once.
    private val ACTION_IME_ENTER_COMPAT: Int by lazy {
        try {
            // API 33‑ext5+ has public AccessibilityNodeInfo.ACTION_IME_ENTER
            AccessibilityNodeInfo::class.java
                .getField("ACTION_IME_ENTER")
                .getInt(null)
        } catch (e: Throwable) {
            // Fallback literal (same bit flag Android uses internally)
            0x00002000
        }
    }

    /**
     * Breadth‑first search through the current window and type **inputText**
     * into the **second** editable field that supports `ACTION_SET_TEXT`.
     *
     * ⚠️  “Second” here means the second <android.widget.EditText> encountered
     *     in _visual / traversal_ order (same order you’d get if you kept tapping
     *     `TAB` on a hardware keyboard).
     */
    fun simulateTypeInSecondEditableField(inputText: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w("MyAccessibilityService", "no active window")
            return
        }

        var editableSeen = 0                                       // ← how many we’ve hit so far
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val supportsSetText = (node.actions and
                    AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
            val isEditable = node.className == "android.widget.EditText" &&
                    node.isEnabled && supportsSetText

            if (isEditable) {
                editableSeen++

                /* ── #2 ⇒ focus, set text, bail out ─────────────────────────── */
                if (editableSeen == 2) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            inputText
                        )
                    }
                    val ok = node.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        args
                    )
                    Log.d("MyAccessibilityService",
                        "typed into 2nd field, success=$ok")
                    return
                }
            }

            /* enqueue children for BFS traversal */
            repeat(node.childCount) { idx ->
                node.getChild(idx)?.let(queue::add)
            }
        }

        Log.w("MyAccessibilityService",
            "couldn’t find a 2nd editable field")
    }

    fun simulateTypeInThirdEditableField(inputText: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w("MyAccessibilityService", "no active window")
            return
        }

        var editableSeen = 0                                       // ← how many we’ve hit so far
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)

        while (queue.isNotEmpty()) {
            var node = queue.removeFirst()

            val supportsSetText = (node.actions and
                    AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
            val isEditable = node.className == "android.widget.EditText" &&
                    node.isEnabled && supportsSetText

            if (isEditable) {
                editableSeen++

                /* ── #2 ⇒ focus, set text, bail out ─────────────────────────── */
                if (editableSeen == 3) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            inputText
                        )
                    }
                    val ok = node.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        args
                    )
                    Log.d("MyAccessibilityService",
                        "typed into 2nd field, success=$ok")
                    return
                }
            }

            /* enqueue children for BFS traversal */
            repeat(node.childCount) { idx ->
                node.getChild(idx)?.let(queue::add)
            }
        }

        Log.w("MyAccessibilityService",
            "couldn’t find a 2nd editable field")
    }
    fun simulateTypeInFirstEditableField(inputText: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w("MyAccessibilityService", "simulateTypeInFirstEditableField: no active window")
            return
        }

        // breadth‑first search for the first node that supports ACTION_SET_TEXT
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // is it an EditText, enabled, and does it advertise ACTION_SET_TEXT?
            if (node.className == "android.widget.EditText"
                && node.isEnabled
                && (node.actions and AccessibilityNodeInfo.ACTION_SET_TEXT) != 0
            ) {
                // focus it
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                // build the argument bundle
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        inputText
                    )
                }
                // set the text
                val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Log.d(
                    "MyAccessibilityService",
                    "simulateTypeInFirstEditableField: set text success=$success"
                )
                return
            }

            // otherwise enqueue its children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        Log.w(
            "MyAccessibilityService",
            "simulateTypeInFirstEditableField: no editable field found"
        )
    }

    /**
     * Recursively checks if the node or any of its children contain the specified text.
     * @param node The node to check.
     * @param lowerSearchText The lowercase text to search for.
     * @return true if the text is found, false otherwise.
     */
    private fun checkNodeForText(node: AccessibilityNodeInfo?, lowerSearchText: String): Boolean {
        if (node == null) return false

        // Check current node's text
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        if (text.contains(lowerSearchText)) {
            Log.d("TextDetection", "Found text \"$lowerSearchText\" in node text: \"$text\"")
            return true
        }

        // Check current node's content description
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        if (contentDesc.contains(lowerSearchText)) {
            Log.d("TextDetection", "Found text \"$lowerSearchText\" in content description: \"$contentDesc\"")
            return true
        }

        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (checkNodeForText(child, lowerSearchText)) {
                return true
            }
        }

        return false
    }

    /**
     * Search for a node by its className and return the [index]-th match in the accessibility tree.
     */
    /**
     * Recursively finds a node by class name, child index, and (optionally) containing text.
     * @param root The root node to start searching from.
     * @param className The fully qualified class name to match (e.g. "android.widget.RelativeLayout").
     * @param targetIndex The target index among siblings (relative position in parent). Use -1 to ignore index.
     * @param substring Optional text/contentDescription that the node should contain (null to ignore).
     * @return The AccessibilityNodeInfo of the first matching node, or null if not found.
     */
    fun findNodeByClassNameAndIndexAndString(
        root: AccessibilityNodeInfo?= rootInActiveWindow , className: String, targetIndex: Int, substring: String?
    ): AccessibilityNodeInfo? {


        var base_root = root
        if(base_root == null){
            base_root = rootInActiveWindow
        }
        // Check current node
        val nodeClass = base_root?.className
        if (nodeClass != null && nodeClass.toString() == className) {
            var indexMatches = false
            if (targetIndex < 0) {
                indexMatches = true // no index filtering
            } else {
                val parent = base_root?.parent
                if (parent != null && parent.childCount > targetIndex) {
                    val childAtIndex = parent.getChild(targetIndex)
                    if (childAtIndex != null && childAtIndex == base_root) {
                        indexMatches = true
                    }
                }
            }
            var stringMatches = false
            if (substring == null || substring.isEmpty()) {
                stringMatches = true // no string filtering
            } else {
                // Check text and content-description for the substring (case-insensitive match for safety)
                val text = base_root?.text
                val desc = base_root?.contentDescription
                val strLower = substring.lowercase(Locale.getDefault())
                if ((text != null && text.toString().lowercase(Locale.getDefault())
                        .contains(strLower))
                    || (desc != null && desc.toString().lowercase(Locale.getDefault())
                        .contains(strLower))
                ) {
                    stringMatches = true
                }
            }
            if (indexMatches && stringMatches) {
                if (base_root != null) {
                    if (base_root != null) {
                        Log.d(
                            "MyService", ("Found node: class=" + className + ", index=" + targetIndex
                                    + ", text=" + base_root.text + ", contentDesc=" + base_root.contentDescription)
                        )
                    }
                }
                return base_root
            }
        }
        // Traverse children
        if (base_root != null) {
            for (i in 0 until base_root.childCount) {
                val found = findNodeByClassNameAndIndexAndString(
                    base_root?.getChild(i),
                    className,
                    targetIndex,
                    substring
                )
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    fun findNodeByClassNameAndIndex(className: String, index: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = ArrayList<AccessibilityNodeInfo>()
        preOrderCollect(root, className, matches)
        return if (index in matches.indices) matches[index] else null
    }

    private fun preOrderCollect(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (
            node.className?.toString() == className &&
            node.contentDescription?.toString() ?: "" != "Close" &&
            node.contentDescription?.toString() ?: "" != "Add Account"
        ) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            preOrderCollect(child, className, result)
        }
    }


    private fun preOrderCollectS(
        node: AccessibilityNodeInfo,
        className: String,
        result: MutableList<AccessibilityNodeInfo>,
        text: String
    ) {
        if (
            node.className?.toString() == className &&
            node.contentDescription?.toString() ?: "" != "Close" &&
            node.contentDescription?.toString() ?: "" != "Add Account"
            && node.contentDescription.contains(text)
        ) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            preOrderCollect(child, className, result)
        }
    }
    /**
     * Perform a click by traversing up the node's parents until it finds a clickable node.
     */
    fun performNodeClick(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            current = current.parent
        }


    }

    /**
     * Simulate a tap at (x, y) coordinates using a GestureDescription.
     * Requires API 24 or higher.
     */
    fun simulateClick(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("MyAccessibilityService", "simulateClick requires API 24 or higher.")
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = StrokeDescription(path, 0, 50)
        gestureBuilder.addStroke(stroke)
        dispatchGesture(gestureBuilder.build(), null, null)
    }
    fun clickFirstElementWithAtSymbol() {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickFirstElementWithAtSymbol: root is null")
            return
        }

        val nodeWithAt = findFirstNodeWithAtSymbol(root)
        if (nodeWithAt == null) {
            Log.w("MyAccessibilityService", "clickFirstElementWithAtSymbol: No element found containing '@'")
            return
        }

        // Get the bounds of the node
        val bounds = Rect()
        nodeWithAt.getBoundsInScreen(bounds)

        // Calculate center coordinates
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        Log.d("MyAccessibilityService", "clickFirstElementWithAtSymbol: Clicking at center [$centerX, $centerY] of element with text: '${nodeWithAt.text}' and contentDesc: '${nodeWithAt.contentDescription}'")

        // Click at the center
        simulateClick(centerX, centerY)
    }

    /**
     * Recursively searches for the first node whose text or content description contains "@".
     * @param node The current node to check
     * @return The first AccessibilityNodeInfo containing "@", or null if not found
     */
    private fun findFirstNodeWithAtSymbol(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check current node's text and content description
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""

        if (nodeText.contains("@") || nodeDesc.contains("@")) {
            return node
        }

        // Recursively check children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findFirstNodeWithAtSymbol(child)
                if (result != null) return result
            }
        }

        return null
    }

    /**
     * Simulate a swipe to scroll from near bottom to near top.
     * Requires API 24 or higher.
     */



    fun simulateScrollToBottomX(X: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("MyAccessibilityService", "simulateScrollToBottom requires API 24+.")
            return
        }
        val path = Path().apply {
            moveTo(X.toFloat(), 700f)   // start near bottom
            lineTo(X.toFloat(), 900f)    // swipe upward
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = StrokeDescription(path, 0, 700)
        gestureBuilder.addStroke(stroke)
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun simulateScrollToBottom() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("MyAccessibilityService", "simulateScrollToBottom requires API 24+.")
            return
        }
        val path = Path().apply {
            moveTo(300f, 1200f)   // start near bottom
            lineTo(300f, 300f)    // swipe upward
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = StrokeDescription(path, 0, 700)
        gestureBuilder.addStroke(stroke)
        dispatchGesture(gestureBuilder.build(), null, null)
    }
    fun simulateScrollToTop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("MyAccessibilityService", "simulateScrollToBottom requires API 24+.")
            return
        }
        val path = Path().apply {
            moveTo(550f, 1100f)   // start near bottom
            lineTo(550f, 1400f)    // swipe upward
        }
        val gestureBuilder = GestureDescription.Builder()
        val stroke = StrokeDescription(path, 0, 700)
        gestureBuilder.addStroke(stroke)
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Simulate typing text into a node identified by resourceId.
     */fun simulateTypeString(nodeClass: String, searchText: String, inputText: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateType(): rootInActiveWindow is null.")
            return
        }

        // Find nodes that contain the specified search text
        val matchedNodes = root.findAccessibilityNodeInfosByText(searchText)
        if (matchedNodes.isEmpty()) {
            Log.w("MyAccessibilityService", "simulateType(): No nodes contain text=\"$searchText\"")
            return
        }

        // Among those, pick the first whose class name matches nodeClass
        val node = matchedNodes.firstOrNull { it.className == nodeClass }
        if (node == null) {
            Log.w("MyAccessibilityService", "simulateType(): No node of class=\"$nodeClass\" contains text=\"$searchText\"")
            return
        }

        // Request focus
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Bundle the new text we want to set
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
        }

        // One-shot text set action
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d("MyAccessibilityService", "simulateType() success=$success for \"$inputText\"")
    }

    /**
     * Find the first node matching `resourceId` and tap its center.
     */
    fun clickByViewId(resourceId: String) {
        val root = rootInActiveWindow ?: return
        // findAccessibilityNodeInfosByViewId was added in API 18+
        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        if (nodes.isNullOrEmpty()) return

        val node = nodes[0]
        // getBoundsInScreen will fill a Rect with [left,top][right,bottom]
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // compute exact center
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()

        // dispatch the tap
        simulateClick(x, y)
    }

    /**
     * Simulate typing text into all nodes with a specific class name.
     */
    fun simulateTypeByClass(nodeClass: String, inputText: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateTypeByClass(): rootInActiveWindow is null.")
            return
        }

        // Find all nodes that match the specified class
        val matchingNodes = findNodesWithClass(root, nodeClass)

        if (matchingNodes.isEmpty()) {
            Log.w("MyAccessibilityService", "simulateTypeByClass(): No nodes with class=\"$nodeClass\" found")
            return
        }

        var successCount = 0

        // Set text in all matching nodes
        for (node in matchingNodes) {
            // Request focus
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Bundle the new text we want to set
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
            }

            // Perform the text setting action
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                successCount++
            }
        }

        Log.d("MyAccessibilityService", "simulateTypeByClass() successfully typed in $successCount/${matchingNodes.size} nodes of class=\"$nodeClass\"")
    }

    /**
     * Searches for a node whose text or content description contains the provided [searchString]
     * (case-insensitive) and returns the full content description of that node.
     * @param searchString The substring to search for.
     * @return The full content description of the found node, or null if no matching node is found.
     */
    fun getContentDescriptionForNodeContaining(searchString: String): String? {
        val root = rootInActiveWindow ?: return null
        val node = searchNodeRecursive(root, searchString)
        return node?.contentDescription?.toString()
    }

    /**
     * Recursively searches for a node whose text or content description contains [searchString].
     * @param node The current node in the traversal.
     * @param searchString The substring to search for.
     * @return The matching AccessibilityNodeInfo if found, or null.
     */
    private fun searchNodeRecursive(node: AccessibilityNodeInfo, searchString: String): AccessibilityNodeInfo? {
        val lowerSearch = searchString.lowercase(Locale.getDefault())
        val text = node.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.getDefault()) ?: ""
        if (text.contains(lowerSearch) || desc.contains(lowerSearch)) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = searchNodeRecursive(child, searchString)
                if (result != null) return result
            }
        }
        return null
    }
    /**
     * Helper function to recursively find nodes with a specific class name.
     */
    fun simulateType(resourceId: String, text: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateType(): rootInActiveWindow is null.")
            return
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        if (nodes.isEmpty()) {
            Log.w("MyAccessibilityService", "simulateType(): No node found for resourceId=$resourceId")
            return
        }
        val node = nodes[0]
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d("MyAccessibilityService", "simulateType() success=$success for \"$text\"")
    }
    /**
     * Simulate deleting text from all nodes with a specific class name.
     */
    fun simulateDeleteByClass(nodeClass: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateDeleteByClass(): rootInActiveWindow is null.")
            return
        }

        // Find all nodes that match the specified class
        val matchingNodes = findNodesWithClass(root, nodeClass)

        if (matchingNodes.isEmpty()) {
            Log.w("MyAccessibilityService", "simulateDeleteByClass(): No nodes with class=\"$nodeClass\" found")
            return
        }

        var successCount = 0

        // Clear text in all matching nodes
        for (node in matchingNodes) {
            // Request focus
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

            // Bundle with empty string to clear the text
            val args = Bundle().apply {
                // Setting an empty string will clear any existing text in the node
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }

            // Perform the text clearing action
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                successCount++
            }
        }

        Log.d("MyAccessibilityService", "simulateDeleteByClass() successfully cleared text in $successCount/${matchingNodes.size} nodes of class=\"$nodeClass\"")
    }

    /**
     * Helper function to recursively find nodes with a specific class name.
     * (Include this if you haven't already defined it for the typing function)
     */
    private fun findNodesWithClass(node: AccessibilityNodeInfo, targetClass: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        // Check if this node matches the target class
        if (node.className?.toString() == targetClass) {
            result.add(node)
        }

        // Recursively check child nodes
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { childNode ->
                result.addAll(findNodesWithClass(childNode, targetClass))
            }
        }

        return result
    }
    fun simulateDelete(resourceId: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "simulateDelete(): rootInActiveWindow is null.")
            return
        }
        val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
        if (nodes.isEmpty()) {
            Log.w("MyAccessibilityService", "simulateDelete(): No node found for resourceId=$resourceId")
            return
        }
        val node = nodes[0]
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            // Setting an empty string will clear any existing text in the node
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d("MyAccessibilityService", "simulateDelete() success=$success for resourceId=$resourceId")
    }

    /**
     * Find a node whose text contains the "@" symbol, returning the [index]-th match.
     */
    fun findNodeWithAtSymbol(index: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        preOrderCollectNodesWithAtSymbol(root, matches)
        return if (index in matches.indices) matches[index] else null
    }

    private fun preOrderCollectNodesWithAtSymbol(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains("@")) {
            result.add(node)
        }
        if (nodeDesc.contains("@")) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                preOrderCollectNodesWithAtSymbol(child, result)
            }
        }
    }
    /**
     * Finds and clicks nodes with the specified contentDescription.
     * First tries to click the node directly or find a clickable parent.
     * If that fails, falls back to clicking the center of the bounds.
     */
    fun clickNodesByContentDescription(targetContentDesc: String) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickNodesByContentDescription: root is null.")
            return
        }
        val matches = mutableListOf<AccessibilityNodeInfo>()
        preOrderCollectByContentDescription(root, targetContentDesc, matches)
        if (matches.isEmpty()) {
            Log.w("MyAccessibilityService", "No nodes found with content description: \"$targetContentDesc\"")
            return
        }

        val node = matches[0]

        // Try to click the node directly or find a clickable parent
        if (!clickNodeOrParent(node)) {
            // If no clickable element was found, fall back to clicking the center of the bounds
            simulateNodeCenterTap(node)
            Log.d("MyAccessibilityService", "Clicked center of bounds for non-clickable element")
        }
    }
    /**
     * Clicks the Bio button element by finding the first node whose content description starts with "Bio".
     */
    fun clickBioButton() {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickBioButton: root is null")
            return
        }

        val bioButtonNode = findFirstBioButton(root)
        if (bioButtonNode == null) {
            Log.w("MyAccessibilityService", "clickBioButton: No Bio button found")
            return
        }

        // Try to click the node or its clickable parent, then fallback to center tap
        if (clickNodeOrParent(bioButtonNode)) {
            Log.d("MyAccessibilityService", "clickBioButton: Successfully clicked Bio button")
        } else {
            simulateNodeCenterTap(bioButtonNode)
            Log.d("MyAccessibilityService", "clickBioButton: Clicked center of bounds for Bio button")
        }
    }

    /**
     * Recursively searches for the first node whose content description starts with "Bio".
     * @param node The current node in the traversal
     * @return The first AccessibilityNodeInfo with content description starting with "Bio", or null if not found
     */
    private fun findFirstBioButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            // Check if current node's content description starts with "Bio"
            val contentDesc = node.contentDescription?.toString()

            if (!contentDesc.isNullOrEmpty() && contentDesc.startsWith("Bio", ignoreCase = true)) {
                // Get the bounds for logging
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                Log.d("MyAccessibilityService", "Found Bio button candidate at bounds: $bounds, class: ${node.className}, contentDesc: \"$contentDesc\"")
                return node
            }

            // Recursively check children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = findFirstBioButton(child)
                    if (result != null) return result
                }
            }
        } catch (e: Exception) {
            Log.w("MyAccessibilityService", "Error processing node in findFirstBioButton: $e")
        }

        return null
    }

    /**
     * Alternative version that finds Bio buttons by both content description and text content.
     * This version checks both contentDescription and text for "Bio" at the beginning.
     */
    fun clickBioButtonExtended() {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickBioButtonExtended: root is null")
            return
        }

        val bioButtonNode = findFirstBioButtonExtended(root)
        if (bioButtonNode == null) {
            Log.w("MyAccessibilityService", "clickBioButtonExtended: No Bio button found")
            return
        }

        // Try to click the node or its clickable parent, then fallback to center tap
        if (clickNodeOrParent(bioButtonNode)) {
            Log.d("MyAccessibilityService", "clickBioButtonExtended: Successfully clicked Bio button")
        } else {
            simulateNodeCenterTap(bioButtonNode)
            Log.d("MyAccessibilityService", "clickBioButtonExtended: Clicked center of bounds for Bio button")
        }
    }

    /**
     * Extended version that searches for nodes with "Bio" at the start of either text or content description.
     */
    private fun findFirstBioButtonExtended(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val nodeText = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()

            // Check if either text or content description starts with "Bio"
            val textStartsWithBio = !nodeText.isNullOrEmpty() && nodeText.startsWith("Bio", ignoreCase = true)
            val descStartsWithBio = !contentDesc.isNullOrEmpty() && contentDesc.startsWith("Bio", ignoreCase = true)

            if (textStartsWithBio || descStartsWithBio) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                Log.d("MyAccessibilityService", "Found Bio button candidate at bounds: $bounds, class: ${node.className}, text: \"$nodeText\", contentDesc: \"$contentDesc\"")
                return node
            }

            // Recursively check children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = findFirstBioButtonExtended(child)
                    if (result != null) return result
                }
            }
        } catch (e: Exception) {
            Log.w("MyAccessibilityService", "Error processing node in findFirstBioButtonExtended: $e")
        }

        return null
    }

    /**
     * Version that finds all Bio buttons and clicks a specific one by index.
     * Useful if there are multiple Bio-related buttons on screen.
     */
    fun clickBioButtonByIndex(index: Int = 0) {
        val root = rootInActiveWindow ?: run {
            Log.w("MyAccessibilityService", "clickBioButtonByIndex: root is null")
            return
        }

        val bioButtons = findAllBioButtons(root)
        if (bioButtons.isEmpty()) {
            Log.w("MyAccessibilityService", "clickBioButtonByIndex: No Bio buttons found")
            return
        }

        if (index < 0 || index >= bioButtons.size) {
            Log.w("MyAccessibilityService", "clickBioButtonByIndex: Invalid index $index. Found ${bioButtons.size} Bio buttons")
            return
        }

        val targetButton = bioButtons[index]

        // Try to click the node or its clickable parent, then fallback to center tap
        if (clickNodeOrParent(targetButton)) {
            Log.d("MyAccessibilityService", "clickBioButtonByIndex: Successfully clicked Bio button at index $index")
        } else {
            simulateNodeCenterTap(targetButton)
            Log.d("MyAccessibilityService", "clickBioButtonByIndex: Clicked center of bounds for Bio button at index $index")
        }
    }

    /**
     * Helper function to find all nodes whose content description or text starts with "Bio".
     */
    private fun findAllBioButtons(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val bioButtons = mutableListOf<AccessibilityNodeInfo>()

        fun searchRecursively(currentNode: AccessibilityNodeInfo) {
            try {
                val nodeText = currentNode.text?.toString()
                val contentDesc = currentNode.contentDescription?.toString()

                val textStartsWithBio = !nodeText.isNullOrEmpty() && nodeText.startsWith("Bio", ignoreCase = true)
                val descStartsWithBio = !contentDesc.isNullOrEmpty() && contentDesc.startsWith("Bio", ignoreCase = true)

                if (textStartsWithBio || descStartsWithBio) {
                    bioButtons.add(currentNode)
                }

                for (i in 0 until currentNode.childCount) {
                    currentNode.getChild(i)?.let { child ->
                        searchRecursively(child)
                    }
                }
            } catch (e: Exception) {
                Log.w("MyAccessibilityService", "Error processing node in findAllBioButtons: $e")
            }
        }

        searchRecursively(node)
        return bioButtons
    }
    private fun preOrderCollectByContentDescription(
        node: AccessibilityNodeInfo,
        targetContentDesc: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString() ?: ""
        val nodeContentDesc = node.contentDescription?.toString() ?: ""

        // Remove the clickable requirement - now finds all matching nodes regardless of clickability
        if (nodeText== targetContentDesc || nodeContentDesc == targetContentDesc) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                preOrderCollectByContentDescription(it, targetContentDesc, result)
            }
        }
    }


}
