package com.amalalbert.heatmap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.annotation.AnyThread
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.graphics.createBitmap
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A class for rendering a heat map in an Android view.
 * <br/>
 */
class HeatMap @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attributeSet, defStyleAttr), View.OnTouchListener {

    /**
     * The data that will be displayed in the heat map.
     */
    private var data: ArrayList<DataPoint> = ArrayList()

    /**
     * A buffer for new data that hasn't been displayed yet.
     */
    private var dataBuffer: ArrayList<DataPoint> = ArrayList()

    /**
     * Whether the information stored in dataBuffer has changed.
     */
    private var dataModified = false

    /**
     * The value that corresponds to the minimum of the gradient scale.
     */
    private var min = Double.NEGATIVE_INFINITY

    /**
     * The value that corresponds to the maximum of the gradient scale.
     */
    private var max = Double.POSITIVE_INFINITY

    /**
     * The amount of blur to use.
     */
    private var mBlur = 0.85F

    /**
     * The radius (px) of the circle each data point takes up.
     */
    private var mRadius: Float = 200.0f

    /**
     * If greater than 0 this will be used as the transparency for the entire map.
     */
    private var opacity = 0

    /**
     * The minimum opacity to use in the map. Only used when {@link HeatMap#opacity} is 0.
     */
    private var minOpacity = 0

    /**
     * The maximum opacity to use in the map. Only used when {@link HeatMap#opacity} is 0.
     */
    private var maxOpacity = 255

    /**
     * The bounds of actual data. For the sake of efficiency this stops us updating outside
     * of where data is present.
     */
    private var mRenderBoundaries = DoubleArray(size = 4)

    /**
     * Colors to be used in building the gradient.
     */
    @ColorInt
    private var colors = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt())

    /**
     * The stops to position the colors at.
     */
    private var positions = floatArrayOf(0.0f, 1.0f)

    /**
     * A paint for solid black.
     */
    private var mBlack: Paint? = null

    private var mTransparentBackground = true

    /**
     * A paint for the background fill.
     */
    private var mBackground: Paint? = null

    /**
     * A paint to be used to fill objects.
     */
    private var mFill: Paint? = null

    /**
     * The color palette being used to create the radial gradients.
     */
    private var palette: IntArray? = null

    /**
     * Whether the palette needs refreshed.
     */
    private var needsRefresh = true

    /**
     * Update the shadow layer when the size changes.
     */
    private var sizeChange = false

    /**
     * The top padding on the heatmap.
     */
    private var mTop: Float = 0f

    /**
     * The left padding on the heatmap.
     */
    private var mLeft: Float = 0F

    /**
     * The right padding on the heatmap.
     */
    private var mRight: Float = 0F

    /**
     * The bottom padding on the heatmap.
     */
    private var mBottom: Float = 0F

    /**
     * The maximum width of the rendering surface.
     */
    private var mMaxWidth: Int? = 0

    /**
     * The maximum height of the rendering surface.
     */
    private var mMaxHeight: Int? = 0

    /**
     * The aspect ratio scale.
     */
    private var mScale: Float? = null

    /**
     * A listener for click events.
     */
    private var mListener: OnMapClickListener? = null

    /**
     * The bitmap that the shadow layer is rendered into.
     */
    private var mShadow: Bitmap? = null

    /**
     * A lock to make sure that the bitmap is not rendered more than once at a time.
     */
    private val tryRefreshLock = Object()

    /**
     * Should the drawing cache be used or should a new bitmap be created.
     */
    private var mUseDrawingCache = false

    /**
     * A listener that is used to draw
     */
    private var mMarkerCallback: HeatMapMarkerCallback? = null

    /**
     * Set a right padding for the data positions. The gradient will still extend into the
     * padding area.
     * @param padding The amount of padding to add to the right of the data points (in pixels).
     */
    fun setRightPadding(padding: Float) {
        mRight = padding
    }

    /**
     * Set a left padding for the data positions. The gradient will still extend into the
     * padding area.
     * @param padding The amount of padding to add to the left of the data points (in pixels).
     */
    fun setLeftPadding(padding: Float) {
        mLeft = padding
    }

    /**
     * Set a top padding for the data positions. The gradient will still extend into the
     * padding area.
     * @param padding The amount of padding to add to the top of the data points (in pixels).
     */
    fun setTopPadding(padding: Float) {
        mTop = padding
    }

    /**
     * Set a bottom padding for the data positions. The gradient will still extend into the
     * padding area.
     * @param padding The amount of padding to add to the bottom of the data points (in pixels).
     */
    fun setBottomPadding(padding: Float) {
        mBottom = padding
    }

    /**
     * Show markers at the data positions.
     * @param callback Callback that will draw the data point markers.
     */
    fun setMarkerCallback(callback: HeatMapMarkerCallback) {
        mMarkerCallback = callback
    }

    /**
     * Set the blur factor for the heat map. Must be between 0 and 1.
     * @param blur The blur factor
     */
    @AnyThread
    fun setBlur(blur: Double) {
        if (blur > 1.0 || blur < 0.0)
            throw IllegalArgumentException("Blur must be between 0 and 1.")
        mBlur = blur.toFloat()
    }

    /**
     * Get the heat map's blur factor.
     */
    @AnyThread
    fun getBlur(): Float {
        return mBlur
    }

    /**
     * Sets the value associated with the maximum on the gradient scale.
     *
     * This should be greater than the minimum value.
     * @param max The maximum value.
     */
    @AnyThread
    fun setMaximum(max: Double) {
        this.max = max
    }

    /**
     * Sets the value associated with the minimum on the gradient scale.
     *
     * This should be less than the maximum value.
     * @param min The minimum value.
     */
    @AnyThread
    fun setMinimum(min: Double) {
        this.min = min
    }

    /**
     * Set the opacity to be used in the heat map. This opacity will be used for the entire map.
     * @param opacity The opacity in the range [0,255].
     */
    @AnyThread
    fun setOpacity(opacity: Int) {
        this.opacity = opacity
    }

    /**
     * Set the minimum opacity to be used in the map. Only used when {@link HeatMap#opacity} is 0.
     * @param min The minimum opacity in the range [0,255].
     */
    @AnyThread
    fun setMinimumOpacity(min: Int) {
        this.minOpacity = min
    }

    /**
     * Set the maximum opacity to be used in the map. Only used when {@link HeatMap#opacity} is 0.
     * @param max The maximum opacity in the range [0,255].
     */
    @AnyThread
    fun setMaximumOpacity(max: Int) {
        this.maxOpacity = max
    }

    /**
     * Set the circles radius when drawing data points.
     * @param radius The radius in pixels.
     */
    @AnyThread
    fun setRadius(radius: Float) {
        this.mRadius = radius
    }

    /**
     * Use the drawing cache instead of creating a new {@link Bitmap}. Causes {@link NullPointerException} on some
     * devices so is disabled by default.
     * @param use Use the drawing cache instead of a new {@link Bitmap}.
     */
    fun setUseDrawingCache(use: Boolean) {
        this.mUseDrawingCache = use
        invalidate()
    }

    /**
     * The maximum width of the bitmap that is used to render the heatmap.
     * @param width The maximum width in pixels.
     */
    fun setMaxDrawingWidth(width: Int) {
        mMaxWidth = width
        mScale = null
    }

    /**
     * The maximum height of the bitmap that is used to render the heatmap.
     * @param height The maximum height in pixels.
     */
    fun setMaxDrawingHeight(height: Int) {
        mMaxHeight = height
        mScale = null
    }

    /**
     * Set the color stops used for the heat map's gradient. There needs to be at least 2 stops
     * and there should be one at a position of 0 and one at a position of 1.
     * @param stops A map from stop positions (as fractions of the width in [0,1]) to ARGB colors.
     */
    @AnyThread
    fun setColorStops(stops: Map<Float, Int>) {
        if (stops.size < 2)
            throw IllegalArgumentException("There must be at least 2 color stops")
        colors = IntArray(size = stops.size)
        positions = FloatArray(stops.size)
        var i = 0
        for (key in stops.keys) {
            stops[key]?.let { colors[i] = it }
            positions[i] = key
            i++
        }
        if (!mTransparentBackground)
            mBackground?.setColor(colors[0])
    }

    /**
     * Add a new data point to the heat map.
     *
     * Does not refresh the display. See {@link HeatMap#forceRefresh()} in order to redraw the heat map.
     * @param point A new data point.
     */
    @AnyThread
    fun addData(point: DataPoint) {
        dataBuffer.add(point)
        dataModified = true
    }

    /**
     * Clears the data that is being displayed in the heat map.
     *
     * Does not refresh the display. See {@link HeatMap#forceRefresh()} in order to redraw the heat map.
     */
    @AnyThread
    fun clearData() {
        dataBuffer.clear()
        dataModified = true
    }

    /**
     * Register a callback to be invoked when this view is clicked. It will return the closest
     * data point as well as the clicked location.
     * @param listener The callback that will run
     */
    fun setOnMapClickListener(listener: OnMapClickListener) {
        this.mListener = listener
    }

    /**
     * Register a callback to be invoked when this view is touched.
     * @param listener The callback that will run
     * @deprecated Use {@link #setOnMapClickListener(OnMapClickListener)} instead.
     */
    override fun setOnTouchListener(listener: OnTouchListener) {
        mListener = null
        super.setOnTouchListener(listener)
    }

    /**
     * Simple constructor to use when creating a view from code.
     * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
     */

    /**
     * Constructor that is called when inflating a view from XML. This is called when a view is
     * being constructed from an XML file, supplying attributes that were specified in the XML file.
     * This version uses a default style of 0, so the only attribute values applied are those in the
     * Context's Theme and the given AttributeSet.
     * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    init {
        if (attributeSet == null) {
            initialize()
        } else {
            initialize()
            val a = context.theme.obtainStyledAttributes(
                attributeSet,
                R.styleable.HeatMap,
                0,
                0
            )
            try {
                opacity = a.getInt(R.styleable.HeatMap_opacity, -1)
                if (opacity < 0)
                    opacity = 0
                minOpacity = a.getInt(R.styleable.HeatMap_minOpacity, -1)
                if (minOpacity < 0)
                    minOpacity = 0
                maxOpacity = a.getInt(R.styleable.HeatMap_maxOpacity, -1)
                if (maxOpacity < 0)
                    maxOpacity = 255
                mBlur = a.getFloat(R.styleable.HeatMap_blur, -1f)
                if (mBlur < 0)
                    mBlur = 0.85f
                mRadius = a.getDimension(R.styleable.HeatMap_radius, -1f)
                if (mRadius < 0)
                    mRadius = 200f
                var padding = a.getDimension(R.styleable.HeatMap_dataPadding, -1f)
                if (padding < 0)
                    padding = 0f
                mTop = a.getDimension(R.styleable.HeatMap_dataPaddingTop, -1f)
                if (mTop < 0)
                    mTop = padding
                mBottom = a.getDimension(R.styleable.HeatMap_dataPaddingBottom, -1f)
                if (mBottom < 0)
                    mBottom = padding
                mRight = a.getDimension(R.styleable.HeatMap_dataPaddingRight, -1f)
                if (mRight < 0)
                    mRight = padding
                mLeft = a.getDimension(R.styleable.HeatMap_dataPaddingLeft, -1f)
                if (mLeft < 0)
                    mLeft = padding
                mMaxWidth = a.getDimension(R.styleable.HeatMap_maxDrawingWidth, -1f).toInt()
                mMaxWidth?.let {
                    if (it < 0)
                        mMaxWidth = null
                }
                mMaxHeight = a.getDimension(R.styleable.HeatMap_maxDrawingHeight, -1f).toInt()
                mMaxHeight?.let {
                    if (it < 0)
                        mMaxHeight = null
                }
                mTransparentBackground =
                    a.getBoolean(R.styleable.HeatMap_transparentBackground, true)
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Force a refresh of the heat map.
     *
     * Use this instead of {@link View#invalidate()}.
     */
    fun forceRefresh() {
        needsRefresh = true
        invalidate()
    }

    /**
     * Initialize all of the paints that we're cable of before drawing.
     */
    fun initialize() {
        mBlack = Paint()
        mBlack?.color = 0xff000000.toInt()
        mFill = Paint()
        mFill?.style = Paint.Style.FILL
        mBackground = Paint()
        if (!mTransparentBackground)
            mBackground?.color = 0xfffefefe.toInt()
        data = arrayListOf()
        dataBuffer = arrayListOf()
        super.setOnTouchListener(this)
        if (mUseDrawingCache) {
            val bitmap = createBitmap(width, height).also { bmp ->
                Canvas(bmp).apply {
                    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    this@HeatMap.draw(this)
                }
            }
        }
    }

    @AnyThread
    @SuppressLint("WrongThread")
    private fun getDrawingWidth(): Int {
        if (mMaxWidth == null)
            return width
        return calcMaxWidth().coerceAtMost(width)
    }

    @AnyThread
    @SuppressLint("WrongThread")
    private fun getDrawingHeight(): Int {
        if (mMaxHeight == null)
            return height
        return calcMaxHeight().coerceAtMost(height)
    }

    @AnyThread
    @SuppressWarnings("WrongThread")
    private fun getScale(): Float {
        if (mScale == null) {
            if (mMaxWidth == null || mMaxHeight == null)
                mScale = 1.0f
            else {
                val sourceRatio = width / height
                val targetRatio = (mMaxWidth ?: 1) / (mMaxHeight ?: 1)
                mScale = if (sourceRatio < targetRatio) {
                    mMaxWidth?.toFloat()?.let { width / (it) }
                } else {
                    mMaxHeight?.toFloat()?.let { height / (it) }
                }
            }
        }
        return mScale ?: 1f
    }

    @AnyThread
    @SuppressLint("WrongThread")
    private fun calcMaxHeight(): Int {
        return (height / getScale()).toInt()
    }

    @AnyThread
    @SuppressLint("WrongThread")
    private fun calcMaxWidth(): Int {
        return (width / getScale()).toInt()
    }

    @AnyThread
    @SuppressLint("WrongThread")
    private fun redrawShadow(width: Int, height: Int) {
        mRenderBoundaries[0] = 10000.0
        mRenderBoundaries[1] = 10000.0
        mRenderBoundaries[2] = 0.0
        mRenderBoundaries[3] = 0.0

        if (mUseDrawingCache) {
            val shadowBitmap = createBitmap(width, height)
                .also { bmp ->
                    Canvas(bmp).apply {
                        drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        this@HeatMap.draw(this)
                    }
                }
            mShadow = shadowBitmap
        } else
            mShadow =
                createBitmap(getDrawingWidth(), getDrawingHeight())
        val shadowCanvas = mShadow?.let { Canvas(it) }

        shadowCanvas?.let { drawTransparent(it, width, height) }
    }

    /**
     * Draws the heatmap from a background thread.
     *
     * This allows offloading some of the work that would usualy be done in
     * {@link #onDraw(Canvas)} into a background thread. If the view is redrawn
     * for some reason while this operation is still ongoing, the UI thread
     * will block until this call is finished.
     *
     * The caller should take care to invalidate the view on the UI thread
     * afterwards, but not before this call has finished.
     *
     * <pre>{@code
     * final HeatMap heatmap = (HeatMap) findViewById(R.id.heatmap)
     * new AsyncTask<Void,Void,Void>() {
     *     protected Void doInBackground(Void... params) {
     *         Random rand = new Random()
     *         //add 20 random points of random intensity
     *         for (int i = 0 i < 20 i++) {
     *             heatmap.addData(getRandomDataPoint())
     *         }
     *
     *         heatmap.forceRefreshOnWorkerThread()
     *
     *         return null
     *     }
     *
     *     protected void onPostExecute(Void aVoid) {
     *         heatmap.invalidate()
     *         heatmap.setAlpha(0.0f)
     *         heatmap.animate().alpha(1.0f).setDuration(700L).start()
     *     }
     * }.execute()
     * }</pre>
     */
    @WorkerThread
    @SuppressLint("WrongThread")
    fun forceRefreshOnWorkerThread() {
        synchronized(tryRefreshLock) {
            // These getters are in fact available on this thread. The caller will have to
            // take care that the view is in an acceptable state here.
            tryRefresh(true, getDrawingWidth(), getDrawingHeight())
        }
    }

    /**
     * If needed, refresh the palette.
     */
    @AnyThread
    private fun tryRefresh(forceRefresh: Boolean, width: Int, height: Int) {
        if (forceRefresh || needsRefresh) {
            val bit = createBitmap(256, 1)
            val canvas = Canvas(bit)
            val grad = LinearGradient(0f, 0f, 256f, 1f, colors, positions, Shader.TileMode.CLAMP)
            val paint = Paint()
            paint.style = Paint.Style.FILL
            paint.setShader(grad)
            canvas.drawLine(0f, 0f, 256f, 1f, paint)
            palette = IntArray(256)
            palette?.let { bit.getPixels(it, 0, 256, 0, 0, 256, 1) }

            if (dataModified) {
                data.addAll(dataBuffer)
                dataBuffer.clear()
                dataModified = false
            }

            redrawShadow(width, height)
        } else if (sizeChange) {
            redrawShadow(width, height)
        }
        needsRefresh = false
        sizeChange = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (mMaxWidth == null || mMaxHeight == null)
            sizeChange = true
    }

    /**
     * Draw the heat map.
     *
     * @param canvas Canvas to draw into.
     */
    override fun onDraw(canvas: Canvas) {
        synchronized(tryRefreshLock) {
            tryRefresh(false, getDrawingWidth(), getDrawingHeight())
        }
        drawColour(canvas)
    }

    /**
     * Draw a radial gradient at a given location. Only draws in black with the gradient being only
     * in transparency.
     *
     * @param canvas Canvas to draw into.
     * @param x The x location to draw the point.
     * @param y The y location to draw the point.
     * @param radius The radius (in pixels) of the point.
     * @param blurFactor A factor to scale the circles width by.
     * @param alpha The transparency of the gradient.
     */
    @AnyThread
    private fun drawDataPoint(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        blurFactor: Float,
        alpha: Double
    ) {
        if (blurFactor == 1.0f) {
            mBlack?.let { canvas.drawCircle(x, y, radius.toFloat(), it) }
        } else {
            //create a radial gradient at the requested position with the requested size
            val gradient = RadialGradient(
                x, y, (radius * blurFactor).toFloat(),
                intArrayOf(Color.argb((alpha * 255).toInt(), 0, 0, 0), Color.argb(0, 0, 0, 0)),
                null, Shader.TileMode.CLAMP
            )
            mFill?.setShader(gradient)
            mFill?.let { canvas.drawCircle(x, y, (2 * radius).toFloat(), it) }
        }
    }

    /**
     * Draw a heat map in only black and transparency to be used as the blended base of the coloured
     * version.
     *
     * @param canvas Canvas to draw into.
     * @param width The width of the view.
     * @param height The height of the view.
     */
    @AnyThread
    private fun drawTransparent(canvas: Canvas, width: Int, height: Int) {
        //invert the blur factor
        val blur = 1 - mBlur

        //clear the canvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scale = getScale()
        val top = mTop / scale
        val bottom = mBottom / scale
        val left = mLeft / scale
        val right = mRight / scale

        val w = width - left - right
        val h = height - top - bottom

        //loop through the data points
        for (point in data) {
            val x = (point.x * w) + left
            val y = (point.y * h) + top
            val value = min.coerceAtLeast(point.value.coerceAtMost(max))
            //the edge of the bounding rectangle for the circle
            val rectX = x - mRadius
            val rectY = y - mRadius

            //calculate the transparency of the circle from its percentage between the max and
            //min values
            val alpha = (value - min) / (max - min)

            //draw the point into the canvas
            drawDataPoint(canvas, x, y, mRadius, blur, alpha)

            //update the modified bounds of the image if necessary
            if (rectX < mRenderBoundaries[0])
                mRenderBoundaries[0] = rectX.toDouble()
            if (rectY < mRenderBoundaries[1])
                mRenderBoundaries[1] = rectY.toDouble()
            if ((rectX + (2 * mRadius)) > mRenderBoundaries[2])
                mRenderBoundaries[2] = (rectX + (2 * mRadius)).toDouble()
            if ((rectY + (2 * mRadius)) > mRenderBoundaries[3])
                mRenderBoundaries[3] = (rectY + (2 * mRadius)).toDouble()
        }
    }

    /**
     * Convert the black/transparent heat map into a full colour one.
     *
     * @param canvas The canvas to draw into.
     */
    private fun drawColour(canvas: Canvas) {
        if (data.isEmpty())
            return

        //calculate the bounds of shadow layer that have modified pixels
        var x = mRenderBoundaries[0].toInt()
        var y = mRenderBoundaries[1].toInt()
        var width = mRenderBoundaries[2].toInt()
        var height = mRenderBoundaries[3].toInt()
        var maxWidth = getDrawingWidth()
        var maxHeight = getDrawingHeight()

        if (maxWidth > (mShadow?.getWidth() ?: 1) && mShadow?.getWidth() != 1)
            maxWidth = mShadow?.getWidth() ?: 1
        if (maxHeight > (mShadow?.getHeight() ?: 1) && mShadow?.getHeight() != 1)
            maxHeight = mShadow?.getHeight() ?: 1

        if (x < 0)
            x = 0
        if (y < 0)
            y = 0
        if (x + width > maxWidth)
            width = maxWidth - x
        if (y + height > maxHeight)
            height = maxHeight - y

        //retrieve the modified pixels from the shadow layer
        val pixels = IntArray(width)

        //loop over each retrieved pixel
        for (j in 0 until height) {
            mShadow?.getPixels(pixels, 0, width, x, y + j, width, 1)

            for (i in 0 until width) {
                val pixel = pixels[i]
                //the pixels alpha value (0-255)
                val alpha = 0xff and (pixel shr 24)

                //clamp the alpha value to user specified bounds
                var clampAlpha =
                    if (opacity > 0)
                        opacity
                    else {
                        if (alpha < maxOpacity) {
                            alpha.coerceAtLeast(minOpacity)
                        } else {
                            maxOpacity
                        }
                    }

                //set the pixels colour to its corresponding colour in the palette
                pixels[i] =
                    ((0xff and clampAlpha) shl 24) or (0xffffff and (palette?.get(alpha) ?: 0))
            }

            //set the modified pixels back into the bitmap
            mShadow?.setPixels(pixels, 0, width, x, y + j, width, 1)
        }

        //clear to the min colour
        if (!mTransparentBackground)
            mBackground?.let {
                canvas.drawRect(
                    0F,
                    0F,
                    getWidth().toFloat(),
                    getHeight().toFloat(),
                    it
                )
            }
        //render the bitmap onto the heat map
        mShadow?.let {
            canvas.drawBitmap(
                it,
                Rect(
                    0,
                    0,
                    getDrawingWidth(),
                    getDrawingHeight()
                ), Rect(0, 0, getWidth(), getHeight()), null
            )
        }

        //draw markers at each data point if requested
        if (mMarkerCallback != null) {
            val rwidth = getWidth() - mLeft - mRight
            var rheight = getHeight() - mTop - mBottom

            for (point in data) {
                val rx = (point.x * rwidth) + mLeft
                val ry = (point.y * rheight) + mTop
                mMarkerCallback?.drawMarker(canvas, rx, ry, point)
            }
        }
    }

    private var touchX: Float? = null
    private var touchY: Float? = null


    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        var time = 0L
        if (mListener != null) {
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                var x = motionEvent.x
                var y = motionEvent.y
                val d = sqrt(((touchX ?: 1f) - x).pow(2.0f) + ((touchY ?: 1f) - y).pow(2.0f))
                Log.d("amal", "onTouch:$d ")
                if (d < 100) {
                    x = x / width.toFloat()
                    y = y / height.toFloat()
                    var minDist = Float.MAX_VALUE
                    var minPoint: DataPoint? = null
                    for (point in data) {
                        val dist = point.distanceTo(x, y)
                        if (dist < minDist) {
                            minDist = dist
                            minPoint = point
                        }
                    }
                    Log.d("amal", "onTouch: $x $y $minPoint")
                    mListener?.onMapClicked(x, y, System.currentTimeMillis() - time)
                    return true
                }
            } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                touchX = motionEvent.x
                touchY = motionEvent.y
                time = System.currentTimeMillis()
                return true
            } else if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                var x = motionEvent.x
                var y = motionEvent.y
                val d = sqrt(((touchX ?: 1f) - x).pow(2.0f) + ((touchY ?: 1f) - y).pow(2.0f))
                Log.d("amal", "onTouch:$d ")
                x = x / width.toFloat()
                y = y / height.toFloat()
                var minDist = Float.MAX_VALUE
                var minPoint: DataPoint? = null
                for (point in data) {
                    val dist = point.distanceTo(x, y)
                    if (dist < minDist) {
                        minDist = dist
                        minPoint = point
                    }
                }
                Log.d("amal", "onTouch: $x $y $minPoint")
                mListener?.onMapClicked(x, y, System.currentTimeMillis() - time)
                return true

            }
        }
        return false
    }

    /**
     * Stores data points to display in the heat map.

     * Construct a new data point to be displayed in the heat map.
     *
     * @param x The data points x location as a decimal percent of the views width
     * @param y The data points y location as a decimal percent of the views height
     * @param value The intensity value of the data point
     */
    data class DataPoint(val x: Float, val y: Float, val value: Double) {
        fun distanceTo(x: Float, y: Float): Float {
            return sqrt((x - this.x).pow(2.0f) + (y - this.y).pow(2.0f))
        }
    }

    interface OnMapClickListener {
        fun onMapClicked(x: Int, y: Int, closest: DataPoint)
        fun onMapClicked(x: Float, y: Float, time: Long)
    }
}
