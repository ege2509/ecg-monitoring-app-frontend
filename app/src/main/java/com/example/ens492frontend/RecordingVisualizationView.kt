package com.example.ens492frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class RecordingVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Constants for grid display
    companion object {
        const val PIXELS_PER_GRID = 50 // Size of major grid squares in dp
        const val DEFAULT_MINOR_GRID_DIVISIONS = 5 // How many minor divisions per major grid
        const val DEFAULT_LEAD_INDEX = 1 // Default to Lead II (index 1)
        const val NUM_LEADS = 12 // Total number of leads
    }

    private val ecgPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#fa87af")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        alpha = 100 // Make the grid less intrusive
    }

    private val majorGridPaint = Paint().apply {
        color = Color.parseColor("#ed5187")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        alpha = 180 // More visible than minor grid lines
    }

    private val path = Path()

    // Map of lead index to data points
    private var ecgData: Map<Int, List<Float>> = emptyMap()

    // Current lead being displayed
    private var currentLeadIndex = DEFAULT_LEAD_INDEX

    // View dimensions
    private var viewWidth = 0
    private var viewHeight = 0

    // Data display parameters
    private var xStep = 1f
    private var yScale = 1f
    private var yOffset = 0f

    // Grid parameters
    private var minorGridDivisions = DEFAULT_MINOR_GRID_DIVISIONS
    private var scrollOffset = 0f // For scrolling effect if needed

    fun setEcgData(data: Map<Int, List<Float>>) {
        ecgData = data
        calculateDisplayParameters()
        invalidate()
    }

    fun setScrollOffset(offset: Float) {
        scrollOffset = offset
        invalidate()
    }

    fun setMinorGridDivisions(divisions: Int) {
        minorGridDivisions = divisions
        invalidate()
    }

    fun setLeadToDisplay(leadIndex: Int) {
        if (leadIndex in 0 until NUM_LEADS && leadIndex != currentLeadIndex) {
            currentLeadIndex = leadIndex
            calculateDisplayParameters()
            invalidate()
        }
    }

    private fun calculateDisplayParameters() {
        val currentData = ecgData[currentLeadIndex] ?: return

        if (currentData.isEmpty()) return

        // Calculate x step based on data size
        xStep = viewWidth.toFloat() / (currentData.size - 1).coerceAtLeast(1)

        // Find min and max values for y scaling
        val minValue = currentData.minOrNull() ?: 0f
        val maxValue = currentData.maxOrNull() ?: 0f
        val range = maxValue - minValue

        // Calculate y scale and offset
        yScale = if (range > 0) viewHeight / range * 0.8f else 1f
        yOffset = viewHeight / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        calculateDisplayParameters()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw grid
        drawGrid(canvas)

        // Draw ECG data
        drawEcgData(canvas)
    }

    /**
     * Draw the ECG grid background with proper scaling and divisions
     */
    private fun drawGrid(canvas: Canvas) {
        // Fill background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Get display density
        val density = context.resources.displayMetrics.density
        val majorGridSpacing = (PIXELS_PER_GRID * density).toFloat()

        // Use the minorGridDivisions variable
        val minorGridSpacing = majorGridSpacing / minorGridDivisions

        // Increase visibility of minor grid lines
        val minorGridPaint = Paint(gridPaint).apply {
            alpha = 150  // Slightly more visible
            strokeWidth = 0.7f * density  // Scale with device density
        }

        // Calculate grid offset for scrolling effect
        val gridOffsetX = (scrollOffset % majorGridSpacing)

        // Draw minor grid lines first
        // Minor vertical lines
        var minorX = -gridOffsetX
        while (minorX < width) {
            canvas.drawLine(minorX, 0f, minorX, height.toFloat(), minorGridPaint)
            minorX += minorGridSpacing
        }

        // Minor horizontal lines
        var minorY = 0f
        while (minorY < height) {
            canvas.drawLine(0f, minorY, width.toFloat(), minorY, minorGridPaint)
            minorY += minorGridSpacing
        }

        // Draw major grid lines
        // Vertical
        var x = -gridOffsetX
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), majorGridPaint)
            x += majorGridSpacing
        }

        // Horizontal
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, majorGridPaint)
            y += majorGridSpacing
        }
    }

    private fun drawEcgData(canvas: Canvas) {
        val currentData = ecgData[currentLeadIndex] ?: return

        if (currentData.isEmpty()) return

        path.reset()

        // Start path at first point
        val firstY = yOffset - (currentData[0] * yScale)
        path.moveTo(0f, firstY)

        // Add points to path
        for (i in 1 until currentData.size) {
            val x = i * xStep
            val y = yOffset - (currentData[i] * yScale)
            path.lineTo(x, y)
        }

        // Draw the path
        canvas.drawPath(path, ecgPaint)
    }
}