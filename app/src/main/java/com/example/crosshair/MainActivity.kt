package com.example.crosshair

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc


class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2, SensorEventListener {

    private lateinit var buttonStartPreview: Button
    private lateinit var buttonStopPreview: Button
    private lateinit var buttonInvert: Button
    private lateinit var openCvCameraView: MyJavaCameraView
    private lateinit var inputMat: Mat
    private lateinit var seekBarZoom: SeekBar
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    private var isPreviewActive = false
    private lateinit var textViewStatus: TextView
    private var isOpenCvInitialized = false
    private val cameraPermissionRequestCode = 100

    private var reticleX = 320
    private var reticleY = 240
    private var stepSize = 5
    private var distance = 100
    private var bulletDrop = 0
    private var zoomLevel = 0
    private val maxZoomLevel = 70 // Increase max zoom level

    private var pitch = 0.0f
    private var roll = 0.0f

    private var invertPreview = false // Flag for invert preview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewStatus = findViewById(R.id.textViewStatus)
        buttonStartPreview = findViewById(R.id.buttonStartPreview)
        buttonStopPreview = findViewById(R.id.buttonStopPreview)
        buttonInvert = findViewById(R.id.buttonInvert)
        openCvCameraView = findViewById(R.id.cameraView)
        seekBarZoom = findViewById(R.id.seekBarZoom)

        if (!OpenCVLoader.initDebug()) {
            textViewStatus.text = "OpenCV initialization failed"
        } else {
            textViewStatus.text = "OpenCV initialized"
            isOpenCvInitialized = true
        }

        // Retrieve saved reticle position from SharedPreferences
        val sharedPreferences = getSharedPreferences("CrosshairPrefs", Context.MODE_PRIVATE)
        reticleX = sharedPreferences.getInt("reticleX", 320)
        reticleY = sharedPreferences.getInt("reticleY", 240)

        checkCameraPermission()

        openCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK)
        openCvCameraView.setCvCameraViewListener(this)

        buttonStartPreview.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                openCvCameraView.enableView()
                isPreviewActive = true
            } else {
                checkCameraPermission()
            }
            updateControls()
        }

        buttonStopPreview.setOnClickListener {
            openCvCameraView.disableView()
            isPreviewActive = false
            updateControls()
        }

        buttonInvert.setOnClickListener {
            invertPreview = !invertPreview // Toggle invert preview
        }

        findViewById<Button>(R.id.buttonUp).setOnClickListener { reticleY -= stepSize }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { reticleY += stepSize }
        findViewById<Button>(R.id.buttonLeft).setOnClickListener { reticleX -= stepSize }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { reticleX += stepSize }
        findViewById<Button>(R.id.buttonDistPlus).setOnClickListener { distance += 10 }
        findViewById<Button>(R.id.buttonDistMinus).setOnClickListener { distance -= 10 }
        findViewById<Button>(R.id.buttonDropPlus).setOnClickListener { bulletDrop += stepSize }
        findViewById<Button>(R.id.buttonDropMinus).setOnClickListener { bulletDrop -= stepSize }

        seekBarZoom.max = maxZoomLevel
        seekBarZoom.progress = zoomLevel
        seekBarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                zoomLevel = progress
                openCvCameraView.setZoom(zoomLevel)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateControls()

        // Initialize the sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        val sharedPreferences = getSharedPreferences("CrosshairPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putInt("reticleX", reticleX)
            putInt("reticleY", reticleY)
            apply()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> gravity = event.values
            Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values
        }

        if (gravity != null && geomagnetic != null) {
            val rotationMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        } else {
            openCvCameraView.setCameraPermissionGranted()
        }
    }

    private fun updateControls() {
        buttonStartPreview.isEnabled = !isPreviewActive && isOpenCvInitialized
        buttonStopPreview.isEnabled = isPreviewActive
        buttonInvert.isEnabled = isPreviewActive
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCameraViewStarted(width: Int, height: Int) {
        inputMat = Mat(height, width, CvType.CV_8UC4)
        isPreviewActive = true
        updateControls()
    }

    override fun onCameraViewStopped() {
        inputMat.release()
        isPreviewActive = false
        updateControls()
    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        inputFrame!!.rgba().copyTo(inputMat)

        if (invertPreview) {
            Core.bitwise_not(inputMat, inputMat) // Invert colors
        }
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//        val distanceTextColor = getTextColorBasedOnBackground(inputMat, Point(150.0, 590.0))
//        val dropTextColor = getTextColorBasedOnBackground(inputMat, Point(150.0, 560.0) )
        val reticleColor = getContrastingColor(inputMat, reticleX, reticleY)
        val distanceTextColor = getTextColorBasedOnBackground(inputMat, 150, if (isLandscape) 150 else 560)
        val dropTextColor = getTextColorBasedOnBackground(inputMat, 150, if (isLandscape) 180 else 590)

        drawReticle(inputMat, reticleX, reticleY, reticleColor)
        drawDistanceMarker(inputMat, distance, distanceTextColor, isLandscape)
        drawBulletDropMarker(inputMat, reticleX, reticleY, bulletDrop, reticleColor, dropTextColor, isLandscape)
        drawInclinometer(inputMat, pitch, roll)

        return inputMat
    }

    private fun getContrastingColor(frame: Mat, x: Int, y: Int): Scalar {
        val color = frame.get(y, x) ?: doubleArrayOf(255.0, 255.0, 255.0)
        val brightness = (color[0] * 0.299 + color[1] * 0.587 + color[2] * 0.114).toInt()
        return if (brightness > 128) Scalar(241.0, 45.0, 93.0) else Scalar(2.0, 248.0, 249.0)
    }

//    private fun getTextColorBasedOnBackground(frame: Mat, point: Point): Scalar {
//        val color = frame.get(point.y.toInt(), point.x.toInt()) ?: doubleArrayOf(255.0, 255.0, 255.0)
//        val brightness = (color[0] * 0.299 + color[1] * 0.587 + color[2] * 0.114).toInt()
//        return if (brightness > 128) Scalar(255.0, 0.0, 0.0) else Scalar(0.0, 255.0, 0.0)
//    }
private fun getTextColorBasedOnBackground(frame: Mat, x: Int, y: Int): Scalar {
    val width = frame.cols()
    val height = frame.rows()

    // Adjust sample points around the text position to ensure within bounds
    val samplePoints = arrayOf(
        Point(x.toDouble(), y.toDouble()),
        Point((x + 10).coerceAtMost(width - 1).toDouble(), y.toDouble()),
        Point((x - 10).coerceAtLeast(0).toDouble(), y.toDouble()),
        Point(x.toDouble(), (y + 10).coerceAtMost(height - 1).toDouble()),
        Point(x.toDouble(), (y - 10).coerceAtLeast(0).toDouble())
    )

    var brightnessSum = 0.0
    var count = 0

    for (point in samplePoints) {
        val color = frame.get(point.y.toInt(), point.x.toInt()) ?: continue
        val brightness = (color[0] * 0.299 + color[1] * 0.587 + color[2] * 0.114)
        brightnessSum += brightness
        count++
    }

    val averageBrightness = brightnessSum / count
    return if (averageBrightness > 128) Scalar(255.0, 0.0, 0.0) else Scalar(0.0, 255.0, 0.0)
}


    private fun drawReticle(frame: Mat, x: Int, y: Int, color: Scalar) {

        // Triangle vertices for the main shape
        val triangleVertices = MatOfPoint(
            Point(x.toDouble(), (y - 25).toDouble()), // Top vertex
            Point((x - 22).toDouble(), (y + 13).toDouble()), // Bottom-left
            Point((x + 22).toDouble(), (y + 13).toDouble())  // Bottom-right
        )

        // Draw main triangle
        Imgproc.polylines(frame, listOf(triangleVertices), true, color, 2)

        // Draw inner triangle
        val innerTriangleVertices = MatOfPoint(
            Point(x.toDouble(), (y - 7).toDouble()),
            Point((x - 6).toDouble(), (y + 3).toDouble()),
            Point((x + 6).toDouble(), (y + 3).toDouble())
        )
        Imgproc.polylines(frame, listOf(innerTriangleVertices), true, color, 1)

        // Draw circle at the center
        Imgproc.circle(frame, Point(x.toDouble(), y.toDouble()), 2, color, -1)

        // Draw lines from the triangle sides toward the center
        Imgproc.line(
            frame,
            Point(x.toDouble(), (y - 25).toDouble()),
            Point(x.toDouble(), (y - 7).toDouble()),
            color,
            1
        )
        Imgproc.line(
            frame,
            Point((x - 22).toDouble(), (y + 13).toDouble()),
            Point((x - 6).toDouble(), (y + 3).toDouble()),
            color,
            1
        )
        Imgproc.line(
            frame,
            Point((x + 22).toDouble(), (y + 13).toDouble()),
            Point((x + 6).toDouble(), (y + 3).toDouble()),
            color,
            1
        )

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Check the orientation of the screen
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawDistanceMarker(frame: Mat, distance: Int, textColor: Scalar, isLandscape: Boolean) {
        val position = if (isLandscape) {
            Point(150.0, 330.0) // Landscape mode position
        } else {
            Point(150.0, 560.0) // Portrait mode position
        }

        Imgproc.putText(frame, "Distance: $distance m", position, Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, textColor, 2)
    }


//    private fun drawDistanceMarker(frame: Mat, distance: Int, textColor: Scalar) {
//
//        Imgproc.putText(frame, "Distance: $distance m", Point(150.0, 590.0), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, textColor, 2)
//    }

    private fun drawBulletDropMarker(frame: Mat, x: Int, y: Int, drop: Int, color: Scalar, textColor: Scalar, isLandscape: Boolean) {
        val position = if (isLandscape) {
            Point(150.0, 360.0) // Landscape mode position
        } else {
            Point(150.0, 590.0) // Portrait mode position
        }
        Imgproc.circle(frame, Point(x.toDouble(), (y + drop).toDouble()), 5, color, -1)
        Imgproc.putText(frame, "Drop: $drop px", position, Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, textColor, 2)
    }

    private fun drawInclinometer(frame: Mat, pitch: Float, roll: Float) {
        val frameWidth = frame.cols()
        val frameHeight = frame.rows()

        val isLandscape = frameWidth > frameHeight

        // Horizontal inclinometer line (Roll)
        val levelLineY = frameHeight - 100.0
        val inclinometerXStart = 50.0
        val inclinometerXEnd = frameWidth - 50.0
        val inclinometerCenterX = frameWidth / 2.0
        val inclinometerTiltX = if (isLandscape) {
            inclinometerCenterX - pitch * 10.0 // Adjust the factor to control sensitivity
        } else {
            inclinometerCenterX - roll * 0.5 // Adjust the factor to control sensitivity
        }

        // Vertical inclinometer line (Pitch)
        val levelLineX = 100.0
        val inclinometerYStart = 50.0
        val inclinometerYEnd = frameHeight - 50.0
        val inclinometerCenterY = frameHeight / 2.0
        val inclinometerTiltY = if (isLandscape) {
            inclinometerCenterY + roll * 10.0 // Adjust the factor to control sensitivity
        } else {
            inclinometerCenterY + pitch * 0.5 // Adjust the factor to control sensitivity
        }

        // Draw the horizontal inclinometer line for roll
        Imgproc.line(frame, Point(inclinometerXStart, levelLineY), Point(inclinometerXEnd, levelLineY), Scalar(255.0, 0.0, 0.0), 2)
        // Draw the tilt level for roll
        Imgproc.circle(frame, Point(inclinometerTiltX, levelLineY), 10, Scalar(0.0, 255.0, 0.0), -1)

        // Draw the vertical inclinometer line for pitch
        Imgproc.line(frame, Point(levelLineX, inclinometerYStart), Point(levelLineX, inclinometerYEnd), Scalar(0.0, 0.0, 255.0), 2)
        // Draw the tilt level for pitch
        Imgproc.circle(frame, Point(levelLineX, inclinometerTiltY), 10, Scalar(255.0, 255.0, 0.0), -1)

        // Draw the center point on the red horizontal inclinometer line
        Imgproc.circle(frame, Point(inclinometerCenterX, levelLineY), 10, Scalar(255.0, 255.0, 255.0), -1) // White color for center point
    }


    private fun invertColors(frame: Mat) {
        Core.bitwise_not(frame, frame)
    }
}
