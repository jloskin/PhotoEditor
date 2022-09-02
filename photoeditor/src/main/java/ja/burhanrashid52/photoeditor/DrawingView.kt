package ja.burhanrashid52.photoeditor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.util.Pair
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import ja.burhanrashid52.photoeditor.shape.*
import java.util.*

/**
 *
 *
 * This is custom drawing view used to do painting on user touch events it it will paint on canvas
 * as per attributes provided to the paint
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 12/1/18
 */
class DrawingView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private val drawShapes = Stack<ShapeAndPaint?>()
    private val redoShapes = Stack<ShapeAndPaint?>()
    internal var currentShape: ShapeAndPaint? = null
    var isDrawingEnabled = false
        private set
    private var viewChangeListener: BrushViewChangeListener? = null
    var currentShapeBuilder: ShapeBuilder = ShapeBuilder()

    // eraser parameters
    private var isErasing = false
    var eraserSize = DEFAULT_ERASER_SIZE

    init {
        //Caution: This line is to disable hardware acceleration to make eraser feature work properly
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isVisible = false
    }

    // endregion
    private fun createPaint(): Paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)

        // apply shape builder parameters
        strokeWidth = currentShapeBuilder.shapeSize
        alpha = currentShapeBuilder.shapeOpacity
        color = currentShapeBuilder.shapeColor
    }

    private fun createEraserPaint(): Paint = createPaint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    fun clearAll() {
        drawShapes.clear()
        redoShapes.clear()
        invalidate()
    }

    fun setBrushViewChangeListener(brushViewChangeListener: BrushViewChangeListener?) {
        viewChangeListener = brushViewChangeListener
    }

    public override fun onDraw(canvas: Canvas) {
        drawShapes.forEach { shape -> shape?.shape?.draw(canvas, shape.paint) }
    }

    /**
     * Handle touch event to draw paint on canvas i.e brush drawing
     *
     * @param event points having touch info
     * @return true if handling touch events
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isDrawingEnabled) {
            val touchX = event.x
            val touchY = event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> onTouchEventDown(touchX, touchY)
                MotionEvent.ACTION_MOVE -> onTouchEventMove(touchX, touchY)
                MotionEvent.ACTION_UP -> onTouchEventUp(touchX, touchY)
            }
            invalidate()
            true
        } else {
            false
        }
    }

    private fun onTouchEventDown(touchX: Float, touchY: Float) {
        createShape()
        currentShape?.shape?.startShape(touchX, touchY)
    }

    private fun onTouchEventMove(touchX: Float, touchY: Float) {
        currentShape?.shape?.moveShape(touchX, touchY)
    }

    private fun onTouchEventUp(touchX: Float, touchY: Float) {
        currentShape?.apply {
            shape.stopShape()
            endShape(touchX, touchY)
        }
    }

    private fun createShape() {
        val paint = if (isErasing) createEraserPaint() else createPaint()
        val shape: AbstractShape = if (!isErasing) {
            when (currentShapeBuilder.shapeType) {
                ShapeType.OVAL -> OvalShape()
                ShapeType.RECTANGLE -> RectangleShape()
                ShapeType.LINE -> LineShape()
                ShapeType.BRUSH, null -> BrushShape()
            }
        } else {
            BrushShape()
        }

        currentShape = ShapeAndPaint(shape, paint)
        drawShapes.push(currentShape)
        viewChangeListener?.onStartDrawing()
    }

    private fun endShape(touchX: Float, touchY: Float) {
        if (currentShape?.shape?.hasBeenTapped() == true) {
            // just a tap, this is not a shape, so remove it
            drawShapes.remove(currentShape)
            //handleTap(touchX, touchY);
        }
        viewChangeListener?.apply {
            onStopDrawing()
            onViewAdd(this@DrawingView)
        }
    }

    fun undo(): Boolean {
        if (!drawShapes.empty()) {
            redoShapes.push(drawShapes.pop())
            invalidate()
        }
        viewChangeListener?.onViewRemoved(this)
        return !drawShapes.empty()
    }

    fun redo(): Boolean {
        if (!redoShapes.empty()) {
            drawShapes.push(redoShapes.pop())
            invalidate()
        }
        viewChangeListener?.onViewAdd(this)
        return !redoShapes.empty()
    }

    // region eraser
    fun brushEraser() {
        isDrawingEnabled = true
        isErasing = true
    }

    // endregion
    // region Setters/Getters

    fun enableDrawing(brushDrawMode: Boolean) {
        isDrawingEnabled = brushDrawMode
        isErasing = !brushDrawMode
        if (brushDrawMode) {
            visibility = VISIBLE
        }
    }

    // endregion
    val drawingPath: Pair<Stack<ShapeAndPaint?>, Stack<ShapeAndPaint?>>
        get() = Pair(drawShapes, redoShapes)

    companion object {
        const val DEFAULT_ERASER_SIZE = 50.0f
    }

    // region constructors
}