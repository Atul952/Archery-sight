//package com.example.crosshair
//
//import android.content.Context
//import android.util.AttributeSet
//import android.view.View
//import org.opencv.android.JavaCameraView
//
//class MyJavaCameraView(context: Context, attrs: AttributeSet) : JavaCameraView(context, attrs) {
//    fun setZoom(level: Int) {
//        val params = mCamera.parameters
//        if (params.isZoomSupported) {
//            val maxZoom = params.maxZoom
//            if (level in 0..maxZoom) {
//                params.zoom = level
//                mCamera.parameters = params
//            }
//        }
//    }
//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        // Get the width and height specifications
//        val width = View.MeasureSpec.getSize(widthMeasureSpec)
//        val height = View.MeasureSpec.getSize(heightMeasureSpec)
//
//        // Set the aspect ratio (3:4 for portrait orientation or 4:3 for landscape orientation)
//        val aspectRatio = 16.0 / 15.9
//
//        // Calculate the dimensions based on the aspect ratio and set the view to fill the screen
//        if (width > height * aspectRatio) {
//            // If width is more limiting than height, scale based on height
//            setMeasuredDimension((height * aspectRatio).toInt(), height)
//        } else {
//            // If height is more limiting than width, scale based on width
//            setMeasuredDimension(width, (width / aspectRatio).toInt())
//        }
//    }
//}
package com.example.crosshair

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.opencv.android.JavaCameraView
import android.graphics.Matrix
import android.view.WindowManager

class MyJavaCameraView(context: Context, attrs: AttributeSet) : JavaCameraView(context, attrs) {

    private val aspectRatio = 16.0 / 15.9 // Set desired aspect ratio here

    private val matrix = Matrix()

    @SuppressLint("ServiceCast")
    private fun updateMatrix() {
        if (holder.surface != null) {
            val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            matrix.setRotate(rotation.toFloat())
        }
    }

    fun setZoom(level: Int) {
        val params = mCamera.parameters
        if (params.isZoomSupported) {
            val maxZoom = params.maxZoom
            if (level in 0..maxZoom) {
                params.zoom = level
                mCamera.parameters = params
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)

        // Set the dimensions based on the aspect ratio
        if (width > height * aspectRatio) {
            setMeasuredDimension((height * aspectRatio).toInt(), height)
        } else {
            setMeasuredDimension(width, (width / aspectRatio).toInt())
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutParams = layoutParams.apply {
            height = (w / aspectRatio).toInt() // Adjust the height based on aspect ratio
        }
    }

    override fun deliverAndDrawFrame(inputFrame: CvCameraViewFrame) {
        updateMatrix()
        super.deliverAndDrawFrame(inputFrame)
    }
}

