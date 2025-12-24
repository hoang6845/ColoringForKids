package com.example.coloringbook

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory


data class SVGElement(
    val id: String,
    val type: String,
    val path: Path,
    val region: Region,
    val bounds: RectF,
    val fillRule: String,
    var fillColor: String,
    val colorIndex: Int,
    val elementType: ElementType = ElementType.COLORING,
    val stepId: String? = null,
    var isCompleted: Boolean = false
)

enum class ElementType {
    STEP,
    COLORING,
    DASH_STEP
}

data class DashSamplePoint(
    val x: Float,
    val y: Float,
    var hit: Boolean = false
)

data class StepProgress(
    val stepId: String,
    val dashStepId: String,
    var isCompleted: Boolean = false,
    val drawnPath: MutableMap<String, Path> = mutableMapOf(),
    var clippedDrawnPath: MutableMap<String, Path> = mutableMapOf(),
    var progress: Float = 0f,
    var dashPoints: MutableList<DashSamplePoint> = mutableListOf()
)


data class ColoringProgress(
    val coloringId: String,
    val stepId: String,
    var drawnPaths: MutableMap<String, Path> = mutableMapOf(),
    var isCompleted: Boolean = false
)

@SuppressLint("ViewConstructor")
class SVGColoringView @JvmOverloads constructor(
    context: Context,
    val updateColorAdapter: (colorPalette: List<Int>) -> Unit,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private var svg: SVG? = null

    private val fillableElements = mutableListOf<SVGElement>()
    private val mapStepElements = mutableMapOf<String, SVGElement>()
    private val stepElements = mutableListOf<SVGElement>()
    private val coloringElements = mutableListOf<SVGElement>()
    private val dashStepElements = mutableListOf<SVGElement>()
    private val pathParser = SVGPathParser()
    private val stepProgressMap = mutableMapOf<String, StepProgress>()
    private val coloringProgressMap = mutableMapOf<String, ColoringProgress>()
    private var currentStepIndex = 0
    private var scaleBegin = 1f
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var currentMode = DrawingMode.STEP

    private var stepStrokeWidth = 20f

    private val stepFillPaint = Paint().apply {
        color = Color.parseColor("#B3E5FC")
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }

    private val dashedPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }

    private val animatedDashedPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private var dashPhase = 0f
    private val dashAnimator = ValueAnimator.ofFloat(0f, 20f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            dashPhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val userDrawPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var currentDrawPath = Path()
    private var isDrawing = false
    private var currentColoringElement: SVGElement? = null

    enum class DrawingMode {
        STEP,
        COLORING
    }

    private fun getSVGAsString(svg: SVG): String {
        return getSVGStringFromRaw(context, R.raw.b)
    }

    private fun getSVGStringFromRaw(context: Context, resId: Int): String {
        return context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }

    private fun calculateScale() {
        Log.d("scale begin", "calculateScale: asdas $svg")
        svg?.let { svgInstance ->
            val viewBox = svgInstance.documentViewBox
            val svgWidth = viewBox.width()
            val svgHeight = viewBox.height()
            if (svgWidth > 0 && svgHeight > 0 && width > 0 && height > 0) {
                val scaleX = width / svgWidth
                val scaleY = height / svgHeight
                scaleBegin = kotlin.comparisons.minOf(scaleX, scaleY)
                Log.d("scale begin", "calculateScale: $scaleBegin")
                val scaledWidth = svgWidth * scaleBegin
                val scaledHeight = svgHeight * scaleBegin

                offsetX = (width - scaledWidth) / 2f
                offsetY = (height - scaledHeight) / 2f

                scaleFactor = 1f
//                checkerBoardPaint = null
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScale()
        invalidate()
    }

    fun setSVG(svg: SVG) {
        this.svg = svg
        calculateScale()
        parseSVGElements()
        invalidate()
    }

    private fun parseSVGElements() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
//                val svgString = getSVGStringFromRemote(pattern.svgPath, AppRemoteConfig.getAccessToken())
                svg?.let { svgInstance ->
                    val svgString = getSVGAsString(svgInstance)
                    svgString?.let { content ->
                        val factory = DocumentBuilderFactory.newInstance()
                        val builder = factory.newDocumentBuilder()
                        val doc = builder.parse(ByteArrayInputStream(svgString.toByteArray()))

                        fillableElements.clear()
                        stepElements.clear()
                        coloringElements.clear()
                        dashStepElements.clear()
                        stepProgressMap.clear()
                        coloringProgressMap.clear()
                        // Parse toàn bộ SVG (chỉ coloring)
                        parseElementsRecursively(doc.documentElement)

                        withContext(Dispatchers.Main) {
                            autoGenerateStepsFromColoring()

                            currentStepIndex = 0
//                            startDashAnimation()
                            invalidate()
                        }
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sampleDashPath(
        dashPath: Path,
        step: Float = 12f
    ): MutableList<DashSamplePoint> {

        val points = mutableListOf<DashSamplePoint>()
        val measure = PathMeasure(dashPath, false)
        val pos = FloatArray(2)

        var distance = 0f
        val length = measure.length

        while (distance <= length) {
            if (measure.getPosTan(distance, pos, null)) {
                points.add(DashSamplePoint(pos[0], pos[1]))
            }
            distance += step
        }

        return points
    }

    private fun parseElementsRecursively(element: Element) {
        val tagName = element.tagName.lowercase()

        val fillRule = element.getAttribute("fill-rule").takeIf { it.isNotEmpty() } ?: "nonzero"
        val fillColor = element.getAttribute("fill").takeIf { it.isNotEmpty() } ?: "#FFFFFF"
        val id = element.getAttribute("id").takeIf { it.isNotEmpty() }
            ?: "${tagName}_${UUID.randomUUID()}"

        when (tagName) {
            "path" -> {
                val pathData = element.getAttribute("d")
                if (pathData.isNotEmpty()) {
                    createPathElement(pathData, fillRule, fillColor, ElementType.COLORING, id)
                }
            }

            "circle" -> createCircleElement(element, fillRule, fillColor, ElementType.COLORING, id)
            "rect" -> createRectElement(element, fillRule, fillColor, ElementType.COLORING, id)
            "ellipse" -> createEllipseElement(
                element,
                fillRule,
                fillColor,
                ElementType.COLORING,
                id
            )

            "polygon" -> createPolygonElement(
                element,
                fillRule,
                fillColor,
                ElementType.COLORING,
                id
            )

            "polyline" -> createPolylineElement(
                element,
                fillRule,
                fillColor,
                ElementType.COLORING,
                id
            )
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                parseElementsRecursively(child)
            }
        }
    }

    private fun createPathElement(
        pathData: String,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        try {
            val path = pathParser.parsePath(pathData)
            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                else -> Path.FillType.WINDING
            }

            val bounds = RectF()
            path.computeBounds(bounds, true)

            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )

            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createCircleElement(
        element: Element,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        val cx = element.getAttribute("cx").toFloatOrNull() ?: 0f
        val cy = element.getAttribute("cy").toFloatOrNull() ?: 0f
        val r = element.getAttribute("r").toFloatOrNull() ?: 0f

        if (r > 0) {
            val path = Path()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                "nonzero" -> Path.FillType.WINDING
                else -> Path.FillType.WINDING
            }
            val bounds = RectF(cx - r, cy - r, cx + r, cy + r)
            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )


            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }
        }
    }

    private fun createRectElement(
        element: Element,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        val x = element.getAttribute("x").toFloatOrNull() ?: 0f
        val y = element.getAttribute("y").toFloatOrNull() ?: 0f
        val width = element.getAttribute("width").toFloatOrNull() ?: 0f
        val height = element.getAttribute("height").toFloatOrNull() ?: 0f
        val rx = element.getAttribute("rx").toFloatOrNull() ?: 0f
        val ry = element.getAttribute("ry").toFloatOrNull() ?: 0f

        if (width > 0 && height > 0) {
            val path = Path()
            val bounds = RectF(x, y, x + width, y + height)

            if (rx > 0 || ry > 0) {
                path.addRoundRect(bounds, rx, ry, Path.Direction.CW)
            } else {
                path.addRect(bounds, Path.Direction.CW)
            }

            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                "nonzero" -> Path.FillType.WINDING
                else -> Path.FillType.WINDING
            }

            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )

            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }
        }
    }

    private fun createEllipseElement(
        element: Element,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        val cx = element.getAttribute("cx").toFloatOrNull() ?: 0f
        val cy = element.getAttribute("cy").toFloatOrNull() ?: 0f
        val rx = element.getAttribute("rx").toFloatOrNull() ?: 0f
        val ry = element.getAttribute("ry").toFloatOrNull() ?: 0f

        if (rx > 0 && ry > 0) {
            val path = Path()
            val bounds = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
            path.addOval(bounds, Path.Direction.CW)
            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                "nonzero" -> Path.FillType.WINDING
                else -> Path.FillType.WINDING
            }
            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )

            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }
        }
    }

    private fun createPolygonElement(
        element: Element,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        val points = element.getAttribute("points")
        if (points.isNotEmpty()) {
            val path = createPathFromPoints(points, true)
            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                "nonzero" -> Path.FillType.WINDING
                else -> Path.FillType.WINDING
            }
            val bounds = RectF()
            path.computeBounds(bounds, true)

            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )

            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }
        }
    }

    private fun createPolylineElement(
        element: Element,
        fillRule: String,
        fillColor: String,
        elementType: ElementType,
        id: String
    ) {
        val points = element.getAttribute("points")
        if (points.isNotEmpty()) {
            val path = createPathFromPoints(points, false)
            path.fillType = when (fillRule.lowercase()) {
                "evenodd" -> Path.FillType.EVEN_ODD
                "nonzero" -> Path.FillType.WINDING
                else -> Path.FillType.WINDING
            }
            val bounds = RectF()
            path.computeBounds(bounds, true)

            val region = Region()
            region.setPath(
                path, Region(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt()
                )
            )

            val svgElement = SVGElement(
                id = id,
                type = "path",
                path = path,
                region = region,
                bounds = bounds,
                fillRule = fillRule,
                fillColor = fillColor,
                colorIndex = 0,
                elementType = elementType
            )

            fillableElements.add(svgElement)
            if (elementType == ElementType.COLORING) {
                coloringElements.add(svgElement)
            }
        }
    }

    private fun createPathFromPoints(pointsStr: String, closePath: Boolean): Path {
        val path = Path()
        val coords = pointsStr.trim().split(Regex("\\s+|,")).mapNotNull { it.toFloatOrNull() }

        if (coords.size >= 2) {
            path.moveTo(coords[0], coords[1])

            for (i in 2 until coords.size step 2) {
                if (i + 1 < coords.size) {
                    path.lineTo(coords[i], coords[i + 1])
                }
            }

            if (closePath) {
                path.close()
            }
        }

        return path
    }


    // ============ TỰ ĐỘNG TẠO STEP & DASH_STEP ============

    private fun autoGenerateStepsFromColoring() {
        coloringElements.forEachIndexed { index, coloringElement ->
            val dashStepPath = createOutlinePath(coloringElement.path)
            val dashStepId = "dashstep_${coloringElement.id}"

            val dashStepBounds = RectF()
            dashStepPath.computeBounds(dashStepBounds, true)

            val dashStepRegion = Region()
            dashStepRegion.setPath(
                dashStepPath,
                Region(
                    dashStepBounds.left.toInt(),
                    dashStepBounds.top.toInt(),
                    dashStepBounds.right.toInt(),
                    dashStepBounds.bottom.toInt()
                )
            )

            val dashStepElement = SVGElement(
                id = dashStepId,
                type = "path",
                path = dashStepPath,
                region = dashStepRegion,
                bounds = dashStepBounds,
                fillRule = coloringElement.fillRule,
                fillColor = "#000000",
                colorIndex = 0,
                elementType = ElementType.DASH_STEP,
                stepId = coloringElement.id
            )

            dashStepElements.add(dashStepElement)

            val stepPath = createStepRegionPath(dashStepPath, stepStrokeWidth)
            val stepId = "step_${coloringElement.id}"

            val stepBounds = RectF()
            stepPath.computeBounds(stepBounds, true)

            val stepRegion = Region()
            stepRegion.setPath(
                stepPath,
                Region(
                    stepBounds.left.toInt(),
                    stepBounds.top.toInt(),
                    stepBounds.right.toInt(),
                    stepBounds.bottom.toInt()
                )
            )

            val stepElement = SVGElement(
                id = stepId,
                type = "path",
                path = stepPath,
                region = stepRegion,
                bounds = stepBounds,
                fillRule = coloringElement.fillRule,
                fillColor = "#B3E5FC",
                colorIndex = 0,
                elementType = ElementType.STEP,
                stepId = coloringElement.id
            )

            stepElements.add(stepElement)
            val dashPoints = sampleDashPath(dashStepPath)

            coloringProgressMap[coloringElement.id] = ColoringProgress(
                coloringId = coloringElement.id,
                stepId = stepId,
            )

            stepProgressMap[stepId] = StepProgress(
                stepId = stepId,
                dashStepId = dashStepId,
                dashPoints = dashPoints,
                isCompleted = false
            )
//            stepProgressMap[stepId] = StepProgress(
//                stepId = stepId,
//                dashStepId = dashStepId,
//                isCompleted = false
//            )
        }
    }

    /**
     * Tạo đường viền outline của path
     * Đây là đường viền ngoài cùng (nét đứt đen)
     */
    private fun createOutlinePath(originalPath: Path): Path {
        val outlinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f // Đường viền mỏng
            isAntiAlias = true
        }

        val outlinePath = Path()
        outlinePaint.getFillPath(originalPath, outlinePath)

        return if (outlinePath.isEmpty) {
            // Nếu getFillPath không hoạt động, dùng path gốc
            Path(originalPath)
        } else {
            outlinePath
        }
    }

    private fun createStepRegionPath(dashPath: Path, strokeWidth: Float): Path {
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val stepPath = Path()
        strokePaint.getFillPath(dashPath, stepPath)

        return stepPath
    }

    // ============ DRAWING ============

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        svg?.let {
            canvas.withTranslation(offsetX, offsetY) {

                scale(scaleFactor, scaleFactor)
                scale(scaleBegin, scaleBegin)

                when (currentMode) {
                    DrawingMode.STEP -> drawStepMode(canvas)
                    DrawingMode.COLORING -> drawColoringMode(canvas)
                }
            }
        }

    }

    private fun drawStepMode(canvas: Canvas) {
        coloringElements.forEach { element ->
            fillPaint.color = Color.WHITE
//            canvas.drawPath(element.path, fillPaint)
        }

        stepElements.forEachIndexed { index, stepElement ->
            if (index < currentStepIndex) {
                stepFillPaint.alpha = 100
                canvas.drawPath(stepElement.path, stepFillPaint)

                val completedProgress = stepProgressMap[stepElement.id]
                if (completedProgress != null && !completedProgress.clippedDrawnPath.isEmpty()) {
                    canvas.save()
                    canvas.clipPath(stepElement.path)
                    Log.d("check", "drawStepMode: ${completedProgress.clippedDrawnPath}")
                    for (i in completedProgress.clippedDrawnPath) {
                        userDrawPaint.color = i.key.toColorInt()
                        canvas.drawPath(i.value, userDrawPaint)
                    }
                    canvas.restore()
                }
            }
        }

        if (currentStepIndex < stepElements.size) {
            val currentStepElement = stepElements[currentStepIndex]
            val progress = stepProgressMap[currentStepElement.id]

            if (progress != null) {
                stepFillPaint.alpha = 180
                canvas.drawPath(currentStepElement.path, stepFillPaint)

                val dashStepElement = dashStepElements.find { it.id == progress.dashStepId }
                if (dashStepElement != null) {
                    canvas.save()

                    canvas.clipPath(currentStepElement.path)

                    animatedDashedPaint.pathEffect = DashPathEffect(
                        floatArrayOf(10f, 10f),
                        dashPhase
                    )
                    canvas.drawPath(dashStepElement.path, animatedDashedPaint)

                    canvas.restore()
                }

                if (!progress.drawnPath.isEmpty()) {
                    canvas.save()
                    canvas.clipPath(currentStepElement.path)
                    for (i in progress.drawnPath) {
                        userDrawPaint.color = i.key.toColorInt()
                        canvas.drawPath(i.value, userDrawPaint)
                    }

                    canvas.restore()
                }
            }
        }
    }

    private fun clipDrawnPathToStepRegion(stepElement: SVGElement, progress: StepProgress) {
        progress.clippedDrawnPath[currentSelectedColor] =
            Path(progress.drawnPath[currentSelectedColor])
    }

    private fun drawColoringMode(canvas: Canvas) {
        coloringElements.forEach { element ->

            val coloringElement = mapStepElements[element.id]
            if (coloringElement == null) {
                Log.d("check draw", "drawColoringMode: null")
                return@forEach
            }
            val progressColoring = coloringProgressMap[coloringElement.id]
            if (progressColoring != null && !progressColoring.drawnPaths.isEmpty()) {
                canvas.save()
                canvas.clipPath(coloringElement.path)
                progressColoring.drawnPaths.forEach { (color, path) ->
                    userDrawPaint.color = color.toColorInt()
                    canvas.drawPath(path, userDrawPaint)
                }

                canvas.restore()
            } else {
                fillPaint.color = "#ffffff".toColorInt()
                canvas.drawPath(element.path, fillPaint)
            }


            val stepElement = mapStepElements["step_${element.id}"]
            if (stepElement == null) return@forEach
            val progress = stepProgressMap[stepElement.id]
            if (progress != null && !progress.clippedDrawnPath.isEmpty()) {
                canvas.save()
                canvas.clipPath(stepElement.path)
                progress.clippedDrawnPath.forEach { (color, path) ->
                    userDrawPaint.color = color.toColorInt()
                    canvas.drawPath(path, userDrawPaint)
                }

                canvas.restore()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (currentMode) {
            DrawingMode.STEP -> return handleStepTouch(event)
            DrawingMode.COLORING -> return handleColoringTouch(event)
        }
    }

    private fun handleStepTouch(event: MotionEvent): Boolean {
        if (currentStepIndex >= stepElements.size) {
            return false
        }

        val currentStep = stepElements[currentStepIndex]
        val progress = stepProgressMap[currentStep.id] ?: return false
        val transformedX = (event.x - offsetX) / (scaleFactor * scaleBegin)
        val transformedY = (event.y - offsetY) / (scaleFactor * scaleBegin)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                val path = progress.drawnPath.getOrPut(currentSelectedColor) {
                    Path()
                }
                path.moveTo(transformedX, transformedY)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    progress.drawnPath[currentSelectedColor]?.let { path ->
                        path.lineTo(transformedX, transformedY)
                    }
                    val dashElement = dashStepElements.find { it.id == progress.dashStepId }
                    if (dashElement != null) {
                        updateDashPointHit(progress, transformedX, transformedY, 16f)
                    }

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDrawing = false
                Log.d("check progress", "handleStepTouch: ${progress.progress}")
                clipDrawnPathToStepRegion(currentStep, progress)
                if (progress.progress >= 0.95f) {
                    completeCurrentStep()
                }

                invalidate()
                return true
            }
        }

        return false
    }

    private fun handleColoringTouch(event: MotionEvent): Boolean {
        val transformedX = (event.x - offsetX) / (scaleFactor * scaleBegin)
        val transformedY = (event.y - offsetY) / (scaleFactor * scaleBegin)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                coloringElements.asReversed().forEach { element ->
                    if (element.region.contains(transformedX.toInt(), transformedY.toInt())) {
                        currentColoringElement = element
                        isDrawing = true

                        val coloringProgress = coloringProgressMap[element.id]
                        if (coloringProgress != null) {
                            val colorPath =
                                coloringProgress.drawnPaths.getOrPut(currentSelectedColor) {
                                    Path()
                                }
                            colorPath.moveTo(transformedX, transformedY)
                            colorPath.addCircle(
                                transformedX,
                                transformedY,
                                userDrawPaint.strokeWidth / 2f,
                                Path.Direction.CW
                            )
                        }
                        return true
                    }
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDrawing && currentColoringElement != null) {
                    val element = currentColoringElement!!
                    val coloringProgress = coloringProgressMap[element.id]

                    if (coloringProgress != null) {
                        val colorPath = coloringProgress.drawnPaths[currentSelectedColor]
                        if (colorPath != null) {
                            colorPath.lineTo(transformedX, transformedY)
                            invalidate()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                isDrawing = false
                currentColoringElement = null
                invalidate()
                return true
            }
        }

        return false
    }

//    private fun handleColoringTouch(event: MotionEvent): Boolean {
//        if (event.action == MotionEvent.ACTION_DOWN) {
//            val transformedX = (event.x - offsetX) / (scaleFactor * scaleBegin)
//            val transformedY = (event.y - offsetY) / (scaleFactor * scaleBegin)
//            coloringElements.asReversed().forEach { element ->
//                if (element.region.contains(transformedX.toInt(), transformedY.toInt())) {
//                    element.fillColor = currentSelectedColor
//                    invalidate()
//                    return true
//                }
//            }
//        }
//
//        return false
//    }

    private fun updateDashPointHit(
        progress: StepProgress,
        x: Float,
        y: Float,
        radius: Float = 10f
    ) {
        var hitCount = 0

        for (point in progress.dashPoints) {
            if (point.hit) {
                hitCount++
                continue
            }

            val dx = point.x - x
            val dy = point.y - y

            if (dx * dx + dy * dy <= radius * radius) {
                point.hit = true
                hitCount++
            }
        }

        progress.progress =
            hitCount.toFloat() / progress.dashPoints.size.coerceAtLeast(1)
    }

    private fun calculateDrawingProgress(drawnPath: Path, targetPath: Path): Float {
        val pathMeasure = PathMeasure(targetPath, false)
        val totalLength = pathMeasure.length

        val drawnMeasure = PathMeasure(drawnPath, false)
        val drawnLength = drawnMeasure.length

        return (drawnLength / totalLength).coerceIn(0f, 1f)
    }

    private fun completeCurrentStep() {
        if (currentStepIndex < stepElements.size) {
            val currentStep = stepElements[currentStepIndex]
            currentStep.isCompleted = true

            val progress = stepProgressMap[currentStep.id]
            progress?.isCompleted = true

            currentStepIndex++

            if (currentStepIndex >= stepElements.size) {
                createMapElement()
                switchToColoringMode()
            } else {
                invalidate()
            }
        }
    }

    fun createMapElement() {
        mapStepElements.clear()
        stepElements.forEach { it ->
            mapStepElements[it.id] = it
        }
        coloringElements.forEach { it ->
            mapStepElements[it.id] = it
        }
    }

    private fun switchToColoringMode() {
        dashAnimator.cancel()
        currentMode = DrawingMode.COLORING
        invalidate()
    }

    private fun startDashAnimation() {
        dashAnimator.start()
    }

//    fun resetDrawing() {
//        currentStepIndex = 0
//        currentMode = DrawingMode.STEP
//        stepProgressMap.values.forEach {
//            it.isCompleted = false
//            it.drawnPath = Path()
//            it.progress = 0f
//        }
//        stepElements.forEach { it.isCompleted = false }
//        coloringElements.forEach { it.fillColor = "#FFFFFF" }
//        startDashAnimation()
//        invalidate()
//    }

    private var currentSelectedColor = "#FF0000"

    fun setFillColor(color: String) {
        currentSelectedColor = color
    }

    fun setStepStrokeWidth(width: Float) {
        stepStrokeWidth = width
        // Regenerate steps nếu cần
        autoGenerateStepsFromColoring()
        invalidate()
    }

}

// Helper class nếu chưa có
class PathParser {
    fun parsePath(pathData: String): Path {
        val path = Path()
        // Implementation parse SVG path data
        // Có thể dùng thư viện androidx.core.graphics.PathParser
        return path
    }
}