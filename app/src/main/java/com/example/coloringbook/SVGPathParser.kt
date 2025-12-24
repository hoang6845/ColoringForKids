package com.example.coloringbook

import android.graphics.Path
import android.graphics.PointF
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class SVGPathParser {

    companion object {
        private val COMMAND_PATTERN = Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])")
        private val NUMBER_PATTERN = Pattern.compile("-?\\d*\\.?\\d+([eE][+-]?\\d+)?")
    }

    fun parsePath(pathData: String): Path {
        val path = Path()
        if (pathData.isEmpty()) return path

        val commands = tokenizePathData(pathData)
        val currentPoint = PointF(0f, 0f)
        val lastControlPoint = PointF(0f, 0f)
        val pathStartPoint = PointF(0f, 0f)
        var lastCommand = 'M'

        var i = 0
        while (i < commands.size) {
            val command = commands[i]

            if (command.length == 1 && command[0].isLetter()) {
                // Đây là command
                val cmd = command[0]
                val isRelative = cmd.isLowerCase()
                val upperCmd = cmd.uppercaseChar()

                when (upperCmd) {
                    'M' -> { // Move to
                        i++
                        if (i < commands.size) {

                            val coords = parseCoordinates(commands[i])
                            if (coords.size >= 2) {
                                val x = if (isRelative) currentPoint.x + coords[0] else coords[0]
                                val y = if (isRelative) currentPoint.y + coords[1] else coords[1]
                                path.moveTo(x, y)
                                currentPoint.set(x, y)
                                pathStartPoint.set(x, y)
                                lastControlPoint.set(x, y)

                                // Xử lý các điểm tiếp theo như LineTo
                                for (j in 2 until coords.size step 2) {
                                    if (j + 1 < coords.size) {
                                        val nextX = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                        val nextY = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]
                                        path.lineTo(nextX, nextY)
                                        currentPoint.set(nextX, nextY)
                                        lastControlPoint.set(nextX, nextY)
                                    }
                                }
                            }
                        }
                    }

                    'L' -> { // Line to
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 2) {
                                if (j + 1 < coords.size) {
                                    val x = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                    val y = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]
                                    path.lineTo(x, y)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x, y)
                                }
                            }
                        }
                    }

                    'H' -> { // Horizontal line to
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (coord in coords) {
                                val x = if (isRelative) currentPoint.x + coord else coord
                                path.lineTo(x, currentPoint.y)
                                currentPoint.x = x
                                lastControlPoint.set(x, currentPoint.y)
                            }
                        }
                    }

                    'V' -> { // Vertical line to
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (coord in coords) {
                                val y = if (isRelative) currentPoint.y + coord else coord
                                path.lineTo(currentPoint.x, y)
                                currentPoint.y = y
                                lastControlPoint.set(currentPoint.x, y)
                            }
                        }
                    }

                    'C' -> { // Cubic Bezier curve
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 6) {
                                if (j + 5 < coords.size) {
                                    val x1 = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                    val y1 = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]
                                    val x2 = if (isRelative) currentPoint.x + coords[j + 2] else coords[j + 2]
                                    val y2 = if (isRelative) currentPoint.y + coords[j + 3] else coords[j + 3]
                                    val x = if (isRelative) currentPoint.x + coords[j + 4] else coords[j + 4]
                                    val y = if (isRelative) currentPoint.y + coords[j + 5] else coords[j + 5]

                                    path.cubicTo(x1, y1, x2, y2, x, y)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x2, y2)
                                }
                            }
                        }
                    }

                    'S' -> { // Smooth cubic Bezier curve
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 4) {
                                if (j + 3 < coords.size) {
                                    val x1 = if (lastCommand == 'C' || lastCommand == 'S') {
                                        2 * currentPoint.x - lastControlPoint.x
                                    } else {
                                        currentPoint.x
                                    }
                                    val y1 = if (lastCommand == 'C' || lastCommand == 'S') {
                                        2 * currentPoint.y - lastControlPoint.y
                                    } else {
                                        currentPoint.y
                                    }

                                    val x2 = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                    val y2 = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]
                                    val x = if (isRelative) currentPoint.x + coords[j + 2] else coords[j + 2]
                                    val y = if (isRelative) currentPoint.y + coords[j + 3] else coords[j + 3]

                                    path.cubicTo(x1, y1, x2, y2, x, y)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x2, y2)
                                }
                            }
                        }
                    }

                    'Q' -> { // Quadratic Bezier curve
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 4) {
                                if (j + 3 < coords.size) {
                                    val x1 = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                    val y1 = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]
                                    val x = if (isRelative) currentPoint.x + coords[j + 2] else coords[j + 2]
                                    val y = if (isRelative) currentPoint.y + coords[j + 3] else coords[j + 3]

                                    path.quadTo(x1, y1, x, y)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x1, y1)
                                }
                            }
                        }
                    }

                    'T' -> { // Smooth quadratic Bezier curve
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 2) {
                                if (j + 1 < coords.size) {
                                    val x1 = if (lastCommand == 'Q' || lastCommand == 'T') {
                                        2 * currentPoint.x - lastControlPoint.x
                                    } else {
                                        currentPoint.x
                                    }
                                    val y1 = if (lastCommand == 'Q' || lastCommand == 'T') {
                                        2 * currentPoint.y - lastControlPoint.y
                                    } else {
                                        currentPoint.y
                                    }

                                    val x = if (isRelative) currentPoint.x + coords[j] else coords[j]
                                    val y = if (isRelative) currentPoint.y + coords[j + 1] else coords[j + 1]

                                    path.quadTo(x1, y1, x, y)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x1, y1)
                                }
                            }
                        }
                    }

                    'A' -> { // Elliptical arc
                        i++
                        if (i < commands.size) {
                            val coords = parseCoordinates(commands[i])
                            for (j in coords.indices step 7) {
                                if (j + 6 < coords.size) {
                                    val rx = coords[j]
                                    val ry = coords[j + 1]
                                    val xAxisRotation = coords[j + 2]
                                    val largeArcFlag = coords[j + 3] != 0f
                                    val sweepFlag = coords[j + 4] != 0f
                                    val x = if (isRelative) currentPoint.x + coords[j + 5] else coords[j + 5]
                                    val y = if (isRelative) currentPoint.y + coords[j + 6] else coords[j + 6]

                                    addArcToPath(path, currentPoint.x, currentPoint.y, x, y, rx, ry, xAxisRotation, largeArcFlag, sweepFlag)
                                    currentPoint.set(x, y)
                                    lastControlPoint.set(x, y)
                                }
                            }
                        }
                    }

                    'Z' -> { // Close path
                        path.close()
                        currentPoint.set(pathStartPoint.x, pathStartPoint.y)
                        lastControlPoint.set(pathStartPoint.x, pathStartPoint.y)
                    }
                }

                lastCommand = upperCmd
            }
            i++
        }

        return path
    }

    private fun tokenizePathData(pathData: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = COMMAND_PATTERN.matcher(pathData)
        var lastEnd = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Thêm số trước command
            if (start > lastEnd) {
                val numbersStr = pathData.substring(lastEnd, start).trim()
                if (numbersStr.isNotEmpty()) {
                    tokens.add(numbersStr)
                }
            }

            // Thêm command
            tokens.add(matcher.group())
            lastEnd = end
        }

        // Thêm số cuối cùng
        if (lastEnd < pathData.length) {
            val numbersStr = pathData.substring(lastEnd).trim()
            if (numbersStr.isNotEmpty()) {
                tokens.add(numbersStr)
            }
        }

        return tokens
    }

    private fun parseCoordinates(coordStr: String): FloatArray {
        val cleanStr = coordStr.replace(",", " ").trim()
        val matcher = NUMBER_PATTERN.matcher(cleanStr)
        val coords = mutableListOf<Float>()

        while (matcher.find()) {
            try {
                coords.add(matcher.group().toFloat())
            } catch (e: NumberFormatException) {
            }
        }

        return coords.toFloatArray()
    }

    private fun addArcToPath1(
        path: Path,
        x0: Float, y0: Float,
        x: Float, y: Float,
        rx: Float, ry: Float,
        angle: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean
    ) {
        if (rx == 0f || ry == 0f) {
            path.lineTo(x, y)
            return
        }

        // Convert angle to radians
        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        // Step 1: Compute (x1', y1')
        val dx2 = (x0 - x) / 2.0
        val dy2 = (y0 - y) / 2.0

        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var rxAdj = abs(rx)
        var ryAdj = abs(ry)

        // Step 2: Ensure radii are large enough
        val rxSq = rxAdj * rxAdj
        val rySq = ryAdj * ryAdj
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        val lambda = x1pSq / rxSq + y1pSq / rySq
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rxAdj *= scale.toFloat()
            ryAdj *= scale.toFloat()
        }

        val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0
        val numerator = rxSq * rySq - rxSq * y1pSq - rySq * x1pSq
        val denominator = rxSq * y1pSq + rySq * x1pSq
        val coef = sign * sqrt(max(numerator / denominator, 0.0))

        val cxp = coef * (rxAdj * y1p) / ryAdj
        val cyp = coef * (-ryAdj * x1p) / rxAdj

        val cx = (cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2).toFloat()
        val cy = (sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2).toFloat()

        fun angle(uX: Double, uY: Double, vX: Double, vY: Double): Double {
            val dot = uX * vX + uY * vY
            val magU = sqrt(uX * uX + uY * uY)
            val magV = sqrt(vX * vX + vY * vY)
            val cosTheta = dot / (magU * magV)
            val sign = if (uX * vY - uY * vX < 0) -1.0 else 1.0
            return sign * acos(min(1.0, max(-1.0, cosTheta)))
        }

        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rxAdj, (y1p - cyp) / ryAdj)
        var deltaTheta = angle(
            (x1p - cxp) / rxAdj, (y1p - cyp) / ryAdj,
            (-x1p - cxp) / rxAdj, (-y1p - cyp) / ryAdj
        )

        if (!sweepFlag && deltaTheta > 0) {
            deltaTheta -= 2 * PI
        } else if (sweepFlag && deltaTheta < 0) {
            deltaTheta += 2 * PI
        }

        // Approximate arc using cubic Bezier curves
        val numSeg = ceil(abs(deltaTheta / (PI / 2))).toInt()
        val delta = deltaTheta / numSeg
        var t = theta1

        var currentX = x0
        var currentY = y0

        for (i in 0 until numSeg) {
            val t1 = t
            val t2 = t + delta
            val cosT1 = cos(t1)
            val sinT1 = sin(t1)
            val cosT2 = cos(t2)
            val sinT2 = sin(t2)

            val dx = (4.0 / 3.0) * tan((t2 - t1) / 4.0)

            val x1 = currentX + (dx * (-rxAdj * sinT1 * cosPhi - ryAdj * cosT1 * sinPhi)).toFloat()
            val y1 = currentY + (dx * (-rxAdj * sinT1 * sinPhi + ryAdj * cosT1 * cosPhi)).toFloat()

            val x2 = (cx + rxAdj * cosT2 * cosPhi - ryAdj * sinT2 * sinPhi).toFloat()
            val y2 = (cy + rxAdj * cosT2 * sinPhi + ryAdj * sinT2 * cosPhi).toFloat()

            val cx2 = x2 + (dx * (rxAdj * sinT2 * cosPhi + ryAdj * cosT2 * sinPhi)).toFloat()
            val cy2 = y2 + (dx * (rxAdj * sinT2 * sinPhi - ryAdj * cosT2 * cosPhi)).toFloat()

            path.cubicTo(x1, y1, cx2, cy2, x2, y2)

            currentX = x2
            currentY = y2
            t = t2
        }
    }

    private fun addArcToPath(
        path: Path,
        x0: Float, y0: Float,
        x: Float, y: Float,
        rx: Float, ry: Float,
        angle: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean
    ) {
        if (rx == 0f || ry == 0f || (x0 == x && y0 == y)) {
            path.lineTo(x, y)
            return
        }

        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x0 - x) / 2.0
        val dy2 = (y0 - y) / 2.0

        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var rxAdj = abs(rx).toDouble()
        var ryAdj = abs(ry).toDouble()

        val rxSq = rxAdj * rxAdj
        val rySq = ryAdj * ryAdj
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        val lambda = x1pSq / rxSq + y1pSq / rySq
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rxAdj *= scale
            ryAdj *= scale
        }

        val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0

        val rxSqAdj = rxAdj * rxAdj
        val rySqAdj = ryAdj * ryAdj

        val numerator = rxSqAdj * rySqAdj - rxSqAdj * y1pSq - rySqAdj * x1pSq
        val denominator = rxSqAdj * y1pSq + rySqAdj * x1pSq

        val coef = sign * sqrt(max(0.0, numerator / denominator))

        val cxp = coef * (rxAdj * y1p) / ryAdj
        val cyp = coef * (-ryAdj * x1p) / rxAdj

        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y) / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val det = ux * vy - uy * vx
            return atan2(det, dot)
        }

        val theta1 = vectorAngle(1.0, 0.0, (x1p - cxp) / rxAdj, (y1p - cyp) / ryAdj)
        var deltaTheta = vectorAngle(
            (x1p - cxp) / rxAdj, (y1p - cyp) / ryAdj,
            (-x1p - cxp) / rxAdj, (-y1p - cyp) / ryAdj
        )

        if (!sweepFlag && deltaTheta > 0) {
            deltaTheta -= 2 * PI
        } else if (sweepFlag && deltaTheta < 0) {
            deltaTheta += 2 * PI
        }

        val segments = ceil(abs(deltaTheta) / (PI / 2)).toInt()
        val delta = deltaTheta / segments

        var t = theta1

        for (i in 0 until segments) {
            val cosT = cos(t)
            val sinT = sin(t)
            val cosT2 = cos(t + delta)
            val sinT2 = sin(t + delta)

            val alpha = sin(delta) * (sqrt(4.0 + 3.0 * tan(delta / 2.0) * tan(delta / 2.0)) - 1.0) / 3.0

            val q1x = cosT - alpha * sinT
            val q1y = sinT + alpha * cosT

            val q2x = cosT2 + alpha * sinT2
            val q2y = sinT2 - alpha * cosT2

            val x1 = (rxAdj * q1x * cosPhi - ryAdj * q1y * sinPhi + cx).toFloat()
            val y1 = (rxAdj * q1x * sinPhi + ryAdj * q1y * cosPhi + cy).toFloat()

            val x2 = (rxAdj * q2x * cosPhi - ryAdj * q2y * sinPhi + cx).toFloat()
            val y2 = (rxAdj * q2x * sinPhi + ryAdj * q2y * cosPhi + cy).toFloat()

            val endX = (rxAdj * cosT2 * cosPhi - ryAdj * sinT2 * sinPhi + cx).toFloat()
            val endY = (rxAdj * cosT2 * sinPhi + ryAdj * sinT2 * cosPhi + cy).toFloat()

            path.cubicTo(x1, y1, x2, y2, endX, endY)

            t += delta
        }
    }

    private fun addArcToPath3(
        path: Path,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        rx: Float, ry: Float,
        angle: Float,
        largeArcFlag: Boolean,
        sweepFlag: Boolean
    ) {
        if (rx == 0f || ry == 0f) {
            path.lineTo(x2, y2)
            return
        }

        if (x1 == x2 && y1 == y2) {
            return
        }

        var rxAbs = abs(rx).toDouble()
        var ryAbs = abs(ry).toDouble()

        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (x1 - x2) / 2.0
        val dy2 = (y1 - y2) / 2.0

        val x1Prime = cosPhi * dx2 + sinPhi * dy2
        val y1Prime = -sinPhi * dx2 + cosPhi * dy2

        val x1PrimeSq = x1Prime * x1Prime
        val y1PrimeSq = y1Prime * y1Prime
        val rxSq = rxAbs * rxAbs
        val rySq = ryAbs * ryAbs

        val lambda = x1PrimeSq / rxSq + y1PrimeSq / rySq
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rxAbs *= scale
            ryAbs *= scale
        }

        val rxSqAdj = rxAbs * rxAbs
        val rySqAdj = ryAbs * ryAbs

        val sign = if (largeArcFlag == sweepFlag) -1.0 else 1.0

        val numerator = rxSqAdj * rySqAdj - rxSqAdj * y1PrimeSq - rySqAdj * x1PrimeSq
        val denominator = rxSqAdj * y1PrimeSq + rySqAdj * x1PrimeSq

        val coeff = sign * sqrt(max(0.0, numerator / denominator))

        val cxPrime = coeff * (rxAbs * y1Prime) / ryAbs
        val cyPrime = coeff * (-ryAbs * x1Prime) / rxAbs

        val cx = cosPhi * cxPrime - sinPhi * cyPrime + (x1 + x2) / 2.0
        val cy = sinPhi * cxPrime + cosPhi * cyPrime + (y1 + y2) / 2.0

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val det = ux * vy - uy * vx
            return atan2(det, dot)
        }

        val theta1 = angle(1.0, 0.0, (x1Prime - cxPrime) / rxAbs, (y1Prime - cyPrime) / ryAbs)

        var deltaTheta = angle(
            (x1Prime - cxPrime) / rxAbs, (y1Prime - cyPrime) / ryAbs,
            (-x1Prime - cxPrime) / rxAbs, (-y1Prime - cyPrime) / ryAbs
        )

        if (!sweepFlag && deltaTheta > 0) {
            deltaTheta -= 2 * PI
        } else if (sweepFlag && deltaTheta < 0) {
            deltaTheta += 2 * PI
        }

        val segments = ceil(abs(deltaTheta) / (PI / 2)).toInt()
        val delta = deltaTheta / segments

        var t = theta1

        for (i in 0 until segments) {
            val cosT = cos(t)
            val sinT = sin(t)
            val cosT2 = cos(t + delta)
            val sinT2 = sin(t + delta)

            val alpha = sin(delta) * (sqrt(4.0 + 3.0 * tan(delta / 2.0) * tan(delta / 2.0)) - 1.0) / 3.0

            val ep1x = cosT - alpha * sinT
            val ep1y = sinT + alpha * cosT
            val ep2x = cosT2 + alpha * sinT2
            val ep2y = sinT2 - alpha * cosT2

            val x1Ctrl = (rxAbs * ep1x * cosPhi - ryAbs * ep1y * sinPhi + cx).toFloat()
            val y1Ctrl = (rxAbs * ep1x * sinPhi + ryAbs * ep1y * cosPhi + cy).toFloat()

            val x2Ctrl = (rxAbs * ep2x * cosPhi - ryAbs * ep2y * sinPhi + cx).toFloat()
            val y2Ctrl = (rxAbs * ep2x * sinPhi + ryAbs * ep2y * cosPhi + cy).toFloat()

            val endX = (rxAbs * cosT2 * cosPhi - ryAbs * sinT2 * sinPhi + cx).toFloat()
            val endY = (rxAbs * cosT2 * sinPhi + ryAbs * sinT2 * cosPhi + cy).toFloat()

            path.cubicTo(x1Ctrl, y1Ctrl, x2Ctrl, y2Ctrl, endX, endY)

            t += delta
        }
    }
}