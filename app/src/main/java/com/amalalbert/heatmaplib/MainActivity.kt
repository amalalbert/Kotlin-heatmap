package com.amalalbert.heatmaplib

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.ArrayMap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.amalalbert.heatmap.HeatMap
import com.amalalbert.heatmap.HeatMap.OnMapClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random


class MainActivity : AppCompatActivity() {
    var heatMap: HeatMap? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        heatMap = findViewById<HeatMap>(R.id.heatmap)
        heatMap?.setMinimum(0.0)
        heatMap?.setMaximum(100.0)
        val rand = Random()
        for (i in 0..19) {
            val point =
                HeatMap.DataPoint(rand.nextFloat(), rand.nextFloat(), rand.nextDouble() * 100.0)
//            heatMap?.addData(point)
        }
        val colors: MutableMap<Float, Int> = ArrayMap()

        //build a color gradient in HSV from red at the center to green at the outside
        for (i in 0..20) {
            val stop = (i.toFloat()) / 20.0f
            val color: Int = doGradient((i * 5).toDouble(), 0.0, 100.0, -0xff0100, -0x10000)
            colors.put(stop, color)
        }
        heatMap?.setColorStops(colors)

        heatMap?.setOnMapClickListener(object : OnMapClickListener {
            override fun onMapClicked(x: Int, y: Int, closest: HeatMap.DataPoint) {
            }

            override fun onMapClicked(x: Float, y: Float, time: Long) {
                lifecycleScope.launch(Dispatchers.Default) {
                    val point =
                        HeatMap.DataPoint(x, y, time.toDouble() * 100)
                    heatMap?.addData(point)
                    withContext(Dispatchers.Main) {
                        heatMap?.forceRefreshOnWorkerThread()
                        heatMap?.invalidate()
                    }
                }
            }
        })
    }

    private fun doGradient(
        value: Double,
        min: Double,
        max: Double,
        min_color: Int,
        max_color: Int
    ): Int {
        if (value >= max) {
            return max_color
        }
        if (value <= min) {
            return min_color
        }
        val hsvmin = FloatArray(3)
        val hsvmax = FloatArray(3)
        val frac = ((value - min) / (max - min)).toFloat()
        Color.RGBToHSV(Color.red(min_color), Color.green(min_color), Color.blue(min_color), hsvmin)
        Color.RGBToHSV(Color.red(max_color), Color.green(max_color), Color.blue(max_color), hsvmax)
        val retval = FloatArray(3)
        for (i in 0..2) {
            retval[i] = interpolate(hsvmin[i], hsvmax[i], frac)
        }
        return Color.HSVToColor(retval)
    }

    private fun interpolate(a: Float, b: Float, proportion: Float): Float {
        return (a + ((b - a) * proportion))
    }
}