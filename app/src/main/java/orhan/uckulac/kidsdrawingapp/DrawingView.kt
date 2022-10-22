package orhan.uckulac.kidsdrawingapp

import android.content.Context
import android.graphics.*
import android.view.View
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent

// use this as a view
class DrawingView(context: Context, attrs: AttributeSet): View(context, attrs) {

    private var mDrawPath: CustomPath? = null  // A variable of CustomPath inner class to use it further.
    private var mCanvasBitmap: Bitmap? = null  // A bitmap is simply a rectangle of pixels. represents a bitmap image
    private var mDrawPaint: Paint? = null
    private var mCanvasPaint: Paint? = null
    private var mBrushSize: Float = 0.toFloat()
    private var color = Color.BLACK
    private var canvas: Canvas? = null
    private var mPaths = ArrayList<CustomPath>()


    init {
        setUpDrawing()
    }

    private fun setUpDrawing(){
        mDrawPaint = Paint()
        mDrawPath = CustomPath(color, mBrushSize)
        mDrawPath!!.color = color
        mDrawPaint!!.style = Paint.Style.STROKE
        mDrawPaint!!.strokeJoin = Paint.Join.ROUND
        mDrawPaint!!.strokeCap = Paint.Cap.ROUND

        // Dithering affects how colors that are higher precision than the device are down-sampled.
        // No dithering is generally faster, but higher precision colors are just truncated down (e.g. 8888 -> 565). Dithering tries to distribute the error inherent in this process, to reduce the visual artifacts
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
//        mBrushSize = 20.toFloat() no needed anymore in here, I will set it in MainActivity
    }

    // use this to set canvas bitmap
    // use this to display our bitmap on our view
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)  // Canvas() requires bitmap
    }

    // Change Canvas to Canvas? if fails
    /**
     * this method is called when a stroke is drawn on the canvas
     * as part of the painting
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(mCanvasBitmap!!, 0f,0f, mCanvasPaint)

        for (path in mPaths){
            mDrawPaint!!.strokeWidth = path.brushThickness  // set path. because we can draw different brush thicknesses in 1 drawing screen
            mDrawPaint!!.color = path.color  // same as above
            canvas.drawPath(path, mDrawPaint!!)
        }

        // what if we didn't start drawing anything?
        // if mDrawPath is not empty:
        if (!mDrawPath!!.isEmpty){
            mDrawPaint!!.strokeWidth = mDrawPath!!.brushThickness  // set draw width
            mDrawPaint!!.color = mDrawPath!!.color  // set custom path color
            canvas.drawPath(mDrawPath!!, mDrawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            // what should happen when we start drawing?
            MotionEvent.ACTION_DOWN ->{
                mDrawPath!!.color = color
                mDrawPath!!.brushThickness = mBrushSize

//                mDrawPath!!.reset()  // when re-clicking to the screen, reset previous
                mDrawPath!!.moveTo(touchX!!, touchY!!)
            }

            MotionEvent.ACTION_MOVE ->{
                mDrawPath!!.lineTo(touchX!!, touchY!!)
            }

            MotionEvent.ACTION_UP ->{  // when stopped moving, reset the view
                mPaths.add(mDrawPath!!)
                mDrawPath = CustomPath(color, mBrushSize)
            }
            else -> return false
        }

        // invalidate the whole view. If the view is visible, onDraw will be called at some point
        invalidate()

        return true
    }

    fun setSizeForBrush(newSize: Float){
        // this will adjust brush thickness according to the screen size
        mBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, newSize, resources.displayMetrics)
        mDrawPaint!!.strokeWidth = mBrushSize
    }


    internal inner class CustomPath(var color: Int,
                                    var brushThickness: Float): Path() { // Converts the provided path string to a Path object of the default filesystem.
    }

}

