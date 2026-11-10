package vision2touch.app.com
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var speechResult: TextView

    // Handles the result returned by Android's speech recognition
    private val speechLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val data = result.data

                val results =
                    data?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )

                val spokenText =
                    results?.firstOrNull()

                if (!spokenText.isNullOrEmpty()) {

                    speechResult.text = spokenText

                } else {

                    Toast.makeText(
                        this,
                        "No speech detected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Recognition button
        val runButton =
            findViewById<Button>(R.id.btnRun)

        // Speech button
        val speechButton =
            findViewById<Button>(R.id.btnSpeech)

        // Text where the English speech will appear
        speechResult =
            findViewById(R.id.speechResult)

        // RECOGNISE
        runButton.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    RecognitionActivity::class.java
                )
            )
        }

        // SPEAK
        speechButton.setOnClickListener {

            startSpeechRecognition()
        }
    }

    private fun startSpeechRecognition() {

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                // English (United Kingdom)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-GB"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak now"
                )
            }

        try {

            speechLauncher.launch(intent)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Speech recognition is not available on this device",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

RecogtitionActivity.kt:

package vision2touch.app.com

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecognitionActivity : AppCompatActivity() {

    private var handLandmarker: HandLandmarker? = null

    // Sign already sent to the Pico.
    private var lastSentSign: String? = null

    // Track stable predictions.
    private var candidateSign: String? = null
    private var consecutiveFrames = 0

    private val requiredFrames = 8
    private val minimumConfidence = 0.20f

    private lateinit var previewView: PreviewView
    private lateinit var detectedSignText: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var bluetoothExecutor: ExecutorService
    private lateinit var classifier: SignClassifier

    // HC-05 Bluetooth connection.
    @Volatile
    private var bluetoothSocket: BluetoothSocket? = null

    @Volatile
    private var bluetoothConnected = false

    private val sppUuid: UUID =
        UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB"
        )

    // Camera permission.
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                startCamera()
            } else {
                Log.e("CameraX", "Camera permission denied")

                detectedSignText.text =
                    "Camera permission is required"
            }
        }

    // Bluetooth permission for Android 12+.
    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                connectToHC05()
            } else {
                Log.e(
                    "Bluetooth",
                    "Bluetooth connection permission denied"
                )

                if (::detectedSignText.isInitialized) {
                    detectedSignText.text =
                        "Bluetooth permission denied"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_recognition)

        // X button: return to the previous screen.
        val closeButton =
            findViewById<AppCompatImageButton>(R.id.closeButton)

        closeButton.setOnClickListener {
            Log.d("Navigation", "Returning to home screen")
            finish()
        }

        previewView = findViewById(R.id.previewView)
        detectedSignText = findViewById(R.id.detectedSignText)

        cameraExecutor = Executors.newSingleThreadExecutor()
        bluetoothExecutor = Executors.newSingleThreadExecutor()

        classifier = SignClassifier(this)

        setupHandLandmarker()

        // Connect to the paired HC-05.
        connectToHC05()

        // Start camera.
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // =========================================
    // BLUETOOTH CONNECTION
    // =========================================

    private fun connectToHC05() {

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(
                Manifest.permission.BLUETOOTH_CONNECT
            )
            return
        }

        bluetoothExecutor.execute {

            var newSocket: BluetoothSocket? = null

            try {
                val manager =
                    getSystemService(BluetoothManager::class.java)

                val adapter: BluetoothAdapter? = manager.adapter

                if (adapter == null) {
                    Log.e(
                        "Bluetooth",
                        "Bluetooth is not supported"
                    )
                    return@execute
                }

                if (!adapter.isEnabled) {
                    Log.e(
                        "Bluetooth",
                        "Please enable Bluetooth in phone settings"
                    )
                    return@execute
                }

                // Pair HC-05 in Android settings before opening this screen.
                val device: BluetoothDevice? =
                    adapter.bondedDevices.find {
                        it.name == "HC-05"
                    }

                if (device == null) {
                    Log.e(
                        "Bluetooth",
                        "HC-05 not found. Pair it in Android settings."
                    )

                    runOnUiThread {
                        detectedSignText.text =
                            "Please pair HC-05 in Bluetooth settings"
                    }

                    return@execute
                }

                Log.d(
                    "Bluetooth",
                    "Connecting to ${device.name}"
                )

                newSocket =
                    device.createRfcommSocketToServiceRecord(
                        sppUuid
                    )

                newSocket.connect()

                bluetoothSocket = newSocket
                bluetoothConnected = true

                Log.d(
                    "Bluetooth",
                    "Connected to HC-05 successfully"
                )

                runOnUiThread {
                    detectedSignText.text =
                        "Bluetooth connected"
                }

            } catch (e: Exception) {

                bluetoothConnected = false

                try {
                    newSocket?.close()
                } catch (_: IOException) {
                }

                if (bluetoothSocket === newSocket) {
                    bluetoothSocket = null
                }

                Log.e(
                    "Bluetooth",
                    "Failed to connect to HC-05",
                    e
                )

                runOnUiThread {
                    detectedSignText.text =
                        "Bluetooth connection failed"
                }
            }
        }
    }

    // =========================================
    // MEDIAPIPE HAND LANDMARKER
    // =========================================

    private fun setupHandLandmarker() {

        val baseOptions =
            BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

        val options =
            HandLandmarker.HandLandmarkerOptions
                .builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    handleHandLandmarkerResult(result)
                }
                .setErrorListener { error ->
                    Log.e(
                        "MediaPipe",
                        "Hand Landmarker error",
                        error
                    )
                }
                .build()

        handLandmarker =
            HandLandmarker.createFromOptions(
                this,
                options
            )
    }

    // =========================================
    // CAMERA
    // =========================================

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .setOutputImageFormat(
                        ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                    )
                    .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->

                try {
                    val bitmap = imageProxy.toBitmap()

                    val mpImage =
                        BitmapImageBuilder(bitmap).build()

                    val timestamp =
                        System.currentTimeMillis()

                    handLandmarker?.detectAsync(
                        mpImage,
                        timestamp
                    )

                } catch (e: Exception) {

                    Log.e(
                        "MediaPipe",
                        "Frame processing error",
                        e
                    )

                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                Log.e(
                    "CameraX",
                    "Camera failed",
                    e
                )
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // =========================================
    // SIGN RECOGNITION
    // =========================================

    private fun handleHandLandmarkerResult(
        result: HandLandmarkerResult
    ) {

        if (result.landmarks().isEmpty()) {

            runOnUiThread {
                detectedSignText.text = "No hand detected"
            }

            lastSentSign = null
            candidateSign = null
            consecutiveFrames = 0

            return
        }

        val hand = result.landmarks()[0]

        val features =
            LandmarkUtils.normaliseLandmarks(hand)

        val prediction =
            classifier.predict(features)

        if (
            prediction == null ||
            prediction.confidence < minimumConfidence
        ) {
            candidateSign = null
            consecutiveFrames = 0

            runOnUiThread {
                detectedSignText.text = "Recognising sign..."
            }

            return
        }

        val currentSign = prediction.label

        runOnUiThread {
            detectedSignText.text =
                "${prediction.label}\n\n" +
                        "Confidence: " +
                        "${(prediction.confidence * 100).toInt()}%"
        }

        // Require a stable prediction across frames.
        if (currentSign == candidateSign) {
            consecutiveFrames++
        } else {
            candidateSign = currentSign
            consecutiveFrames = 1
        }

        // Send each stable sign once until the hand disappears
        // or another sign becomes the current prediction.
        if (
            consecutiveFrames >= requiredFrames &&
            currentSign != lastSentSign
        ) {
            lastSentSign = currentSign

            Log.d(
                "Recognition",
                "Sending sign: $currentSign"
            )

            sendSignToPico(currentSign)
        }
    }

    // =========================================
    // SEND SIGN OVER BLUETOOTH
    // =========================================

    private fun sendSignToPico(sign: String) {

        bluetoothExecutor.execute {

            try {
                val socket = bluetoothSocket

                if (
                    socket == null ||
                    !bluetoothConnected ||
                    !socket.isConnected
                ) {
                    Log.e(
                        "Bluetooth",
                        "Not connected. Sign not sent: $sign"
                    )
                    return@execute
                }

                // Send sign followed by newline.
                val message = "$sign\n"

                socket.outputStream.write(
                    message.toByteArray(Charsets.UTF_8)
                )

                socket.outputStream.flush()

                Log.d(
                    "Bluetooth",
                    "Sent sign to Pico: $sign"
                )

            } catch (e: Exception) {

                bluetoothConnected = false

                Log.e(
                    "Bluetooth",
                    "Failed to send sign: $sign",
                    e
                )
            }
        }
    }

    // =========================================
    // CLEANUP
    // =========================================

    override fun onDestroy() {

        handLandmarker?.close()

        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }

        bluetoothConnected = false

        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(
                "Bluetooth",
                "Error closing Bluetooth socket",
                e
            )
        }

        bluetoothSocket = null

        if (::bluetoothExecutor.isInitialized) {
            bluetoothExecutor.shutdown()
        }

        super.onDestroy()
    }
}

LandmarkUtils.kt:

package vision2touch.app.com

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

object LandmarkUtils {

    fun normaliseLandmarks(
        landmarks: List<NormalizedLandmark>
    ): FloatArray {

        if (landmarks.isEmpty()) {
            return FloatArray(0)
        }

        val wrist = landmarks[0]

        val result = FloatArray(
            landmarks.size * 3
        )

        var index = 0

        for (landmark in landmarks) {

            result[index++] =
                landmark.x() - wrist.x()

            result[index++] =
                landmark.y() - wrist.y()

            result[index++] =
                landmark.z() - wrist.z()
        }

        // Scale using distance from wrist
        // to middle-finger MCP joint.

        if (landmarks.size > 9) {

            val dx =
                landmarks[9].x() - wrist.x()

            val dy =
                landmarks[9].y() - wrist.y()

            val dz =
                landmarks[9].z() - wrist.z()

            val scale =
                sqrt(
                    dx * dx +
                            dy * dy +
                            dz * dz
                )

            if (scale > 0.0001f) {

                for (i in result.indices) {

                    result[i] /= scale
                }
            }
        }

        return result
    }
}

SignClassifier.kt:
package vision2touch.app.com

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

data class Prediction(
    val label: String,
    val confidence: Float
)

class SignClassifier(
    private val context: Context
) {

    // The training data is now stored here:
    // /data/data/vision2touch.app.com/files/signs.json
    private val dataFile = File(
        context.filesDir,
        "signs.json"
    )

    private val examples =
        mutableListOf<SignExample>()

    init {
        loadExamples()
    }

    fun addExample(
        label: String,
        features: FloatArray
    ) {
        examples.add(
            SignExample(
                label,
                features
            )
        )

        saveExamples()
    }

    fun getExampleCount(
        label: String
    ): Int {

        return examples.count {
            it.label.equals(
                label,
                ignoreCase = true
            )
        }
    }

    fun getLabels(): List<String> {

        return examples
            .map { it.label }
            .distinct()
    }

    fun predict(
        features: FloatArray
    ): Prediction? {

        if (examples.isEmpty()) {
            return null
        }

        var bestExample: SignExample? = null
        var bestDistance = Float.MAX_VALUE

        for (example in examples) {

            val distance =
                euclideanDistance(
                    features,
                    example.features
                )

            if (distance < bestDistance) {
                bestDistance = distance
                bestExample = example
            }
        }

        val example = bestExample ?: return null

        val confidence =
            1f / (1f + bestDistance)

        val minimumConfidence = 0.60f

        if (confidence < minimumConfidence) {
            return null
        }

        return Prediction(
            label = example.label,
            confidence = confidence
        )
    }

    private fun euclideanDistance(
        a: FloatArray,
        b: FloatArray
    ): Float {

        if (a.size != b.size) {
            return Float.MAX_VALUE
        }

        var sum = 0f

        for (i in a.indices) {

            val difference =
                a[i] - b[i]

            sum += difference * difference
        }

        return sqrt(sum)
    }

    // ============================================================
    // SAVE TRAINING DATA TO signs.json
    // ============================================================

    private fun saveExamples() {

        try {

            val jsonArray = JSONArray()

            for (example in examples) {

                val objectJson = JSONObject()

                objectJson.put(
                    "label",
                    example.label
                )

                val featureArray = JSONArray()

                for (value in example.features) {
                    featureArray.put(value.toDouble())
                }

                objectJson.put(
                    "features",
                    featureArray
                )

                jsonArray.put(objectJson)
            }

            dataFile.writeText(
                jsonArray.toString(4)
            )

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }
    private fun loadExamplesFromJson(
        jsonString: String
    ) {

        val jsonArray =
            JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {

            val objectJson =
                jsonArray.getJSONObject(i)

            val label =
                objectJson.getString("label")

            val featureArray =
                objectJson.getJSONArray("features")

            val features =
                FloatArray(featureArray.length())

            for (j in features.indices) {

                features[j] =
                    featureArray
                        .getDouble(j)
                        .toFloat()
            }

            examples.add(
                SignExample(
                    label,
                    features
                )
            )
        }
    }


    // ============================================================
    // LOAD TRAINING DATA FROM signs.json
    // ============================================================

    private fun loadExamples() {

        try {

            val xmlText =
                context.assets
                    .open("sign_data.xml")
                    .bufferedReader()
                    .use { it.readText() }

            val document =
                javax.xml.parsers.DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(
                        xmlText.byteInputStream()
                    )

            val strings =
                document.getElementsByTagName("string")

            for (i in 0 until strings.length) {

                val node = strings.item(i)

                val name =
                    node.attributes
                        .getNamedItem("name")
                        ?.nodeValue

                if (name == "examples") {

                    val jsonString =
                        node.textContent

                    loadExamplesFromJson(jsonString)

                    break
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }


    // ============================================================
    // EXPORT A COPY OF signs.json
    // ============================================================

    fun exportExamples(): String {

        val jsonArray = JSONArray()

        for (example in examples) {

            val objectJson = JSONObject()

            objectJson.put(
                "label",
                example.label
            )

            val featureArray = JSONArray()

            for (value in example.features) {
                featureArray.put(
                    value.toDouble()
                )
            }

            objectJson.put(
                "features",
                featureArray
            )

            jsonArray.put(objectJson)
        }

        return jsonArray.toString(4)
    }

    fun exportExamplesToFile(): File {

        val file = File(
            context.filesDir,
            "signs.json"
        )

        file.writeText(
            exportExamples()
        )

        return file
    }
}

SignExample.kt:
package vision2touch.app.com

data class SignExample(
    val label: String,
    val features: FloatArray
)

TrainingActivity.kt:
package vision2touch.app.com
// ==========================================================================
// WHAT THIS FILE DOES, IN PLAIN ENGLISH:
//
// 1. Turns on the camera and shows a live preview on screen.
// 2. For every single camera frame, asks MediaPipe "where is the hand,
//    and what shape are its 21 points in?"
// 3. IF the "Record" button is switched on, it writes those 21 points
//    (63 numbers total) into a text file, along with whatever sign name
//    you typed in the box (like "A" or "Hello").
// 4. You do this once per sign, collecting lots of examples, so that
//    later you can train a proper classifier on your computer using
//    this saved file.
// ==========================================================================

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import android.widget.Button

class TrainingActivity : AppCompatActivity() {

    // These match the IDs we gave things in activity_training.xml
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var signLabelInput: android.widget.EditText
    private lateinit var recordButton: android.widget.Button
    private lateinit var countText: android.widget.TextView

    // This is the MediaPipe tool that finds hands and their 21 points
    private lateinit var handLandmarker: HandLandmarker

    private lateinit var classifier: SignClassifier


    // Keeps track of whether we're currently saving data or not
    private var isRecording = false

    // Counts how many examples we've saved for the CURRENT sign
    private var samplesCollected = 0

    // This handles the system's "Allow this app to use the camera?" popup.
    // Once the user answers, this code runs with their yes/no result.
    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera permission is needed for this app to work", Toast.LENGTH_LONG).show()
        }
    }
    private val createDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->

            if (uri != null) {

                try {
                    val json = classifier.exportExamples()

                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }

                    Toast.makeText(
                        this,
                        "Training data exported successfully",
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {

                    Toast.makeText(
                        this,
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training)

        classifier = SignClassifier(this)


        // Connect our Kotlin variables to the actual UI elements on screen
        previewView = findViewById(R.id.previewView)
        signLabelInput = findViewById(R.id.signLabelInput)
        recordButton = findViewById(R.id.recordButton)
        countText = findViewById(R.id.countText)

        setupHandLandmarker()

        // Android requires the user to explicitly say "yes" to camera access
        // before we're allowed to use it. Check if we already have permission;
        // if not, pop up the system's built-in "Allow camera access?" dialog.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }
        val finishButton = findViewById<Button>(R.id.finishButton)

        // Every time you tap the button, we flip between "recording" and "not recording"
        recordButton.setOnClickListener {
            if (signLabelInput.text.isBlank()) {
                // Don't let recording start if no sign name has been typed
                Toast.makeText(this, "Type a sign name first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            finishButton.setOnClickListener {

                isRecording = false

                try {
                    classifier.exportExamplesToFile()

                    Toast.makeText(
                        this,
                        "Training data saved!",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } catch (e: Exception) {

                    Toast.makeText(
                        this,
                        "Failed to save training data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }



            isRecording = !isRecording
            if (isRecording) {
                samplesCollected = 0
                recordButton.text = "Stop Recording"
            } else {
                recordButton.text = "Start Recording"
            }
        }
    }

    // Sets up MediaPipe's Hand Landmarker so it's ready to find hands in each frame
    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task") // must be in your assets folder
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM) // means: keep processing a live video stream
            .setNumHands(1) // we only care about one hand for now
            .setResultListener { result, _ -> onHandResult(result) } // called every time a result is ready
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    // This runs automatically every time MediaPipe finishes analyzing a frame
    private fun onHandResult(result: HandLandmarkerResult) {
        // If no hand was found in this frame, there's nothing to save

        // Only actually SAVE data if the user has pressed "Start Recording"
        if (!isRecording) return

        if (result.landmarks().isEmpty()) return

        val handPoints = result.landmarks()[0] // the 21 points for the first hand

        val label = signLabelInput.text.toString()
        if (label.isEmpty()) return // don't save if no label typed
        // Turn the 21 points into one row of 63 numbers, plus the label at the front
        val features =
            LandmarkUtils.normaliseLandmarks(handPoints)

        classifier.addExample(
            label,
            features
        )


        // Update the on-screen counter so you can see progress
        samplesCollected++
        runOnUiThread {
            countText.text = "Samples collected: $samplesCollected"
        }
    }

    // Sets up the camera and connects it to the Hand Landmarker
    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Shows the live camera feed on screen
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Grabs each frame so we can hand it to MediaPipe.
            // OUTPUT_IMAGE_FORMAT_RGBA_8888 makes each frame directly convertible
            // to a normal Bitmap, which is what makes the conversion below simple.
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val mpImage = convertToMPImage(imageProxy)
                val frameTimeMs = System.currentTimeMillis()
                handLandmarker.detectAsync(mpImage, frameTimeMs)
                imageProxy.close() // always close the frame when you're done with it
            }

            val cameraSelector = try {
                CameraSelector.DEFAULT_BACK_CAMERA
            } catch (e: Exception) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

        }, ContextCompat.getMainExecutor(this))
    }
    // Turns a camera frame (ImageProxy) into the format MediaPipe expects (MPImage).
    // Since we told the camera to output RGBA_8888 format above, CameraX's
    // built-in .toBitmap() does the heavy lifting for us here.
    private fun convertToMPImage(imageProxy: ImageProxy): MPImage {
        val bitmap = imageProxy.toBitmap()
        return BitmapImageBuilder(bitmap).build()
    }
}

AndroidManifest.xml:
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Bluetooth permissions for Android 11 and older -->
    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />

    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />

    <!-- Bluetooth connection permission for Android 12+ -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_CONNECT" />



    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:usesCleartextTraffic="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Vision2Touch">
        <activity
            android:name=".RecognitionActivity"
            android:exported="false" />

        <activity
            android:name=".TrainingActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>

close_button_background.xml:
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#673AB7" />
</shape>

ic_close.xml:
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z" />
</vector>

ic_launcher_background:
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#3DDC84"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#00000000"
        android:pathData="M9,0L9,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,0L19,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,0L29,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,0L39,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,0L49,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,0L59,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,0L69,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,0L79,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M89,0L89,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M99,0L99,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,9L108,9"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,19L108,19"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,29L108,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,39L108,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,49L108,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,59L108,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,69L108,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,79L108,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,89L108,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,99L108,99"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,29L89,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,39L89,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,49L89,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,59L89,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,69L89,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,79L89,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,19L29,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,19L39,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,19L49,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,19L59,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,19L69,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,19L79,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
</vector>

ic_launcher_foreground.xml:
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M31,63.928c0,0 6.4,-11 12.1,-13.1c7.2,-2.6 26,-1.4 26,-1.4l38.1,38.1L107,108.928l-32,-1L31,63.928z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="85.84757"
                android:endY="92.4963"
                android:startX="42.9492"
                android:startY="49.59793"
                android:type="linear">
                <item
                    android:color="#44000000"
                    android:offset="0.0" />
                <item
                    android:color="#00000000"
                    android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFF"
        android:fillType="nonZero"
        android:pathData="M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 -19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z"
        android:strokeWidth="1"
        android:strokeColor="#00000000" />
</vector>

activity_main.xml:
<?xml version="1.0" encoding="utf-8"?>
<!--
    This is the SCREEN LAYOUT for the training/data-collection page.
    Think of this file as just deciding WHERE things appear on screen
    (the camera preview, a text box, a button, a counter).
    The actual "brain" logic lives in TrainingActivity.kt
-->
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- This shows the live camera feed, like a viewfinder -->
    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/controlsLayout"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- This box holds all the buttons/text at the bottom of the screen -->
    <LinearLayout
        android:id="@+id/controlsLayout"
        android:orientation="vertical"
        android:padding="16dp"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toBottomOf="parent">

        <!-- Where you type the name of the sign you're currently recording, e.g. "A" or "Hello" -->
        <!--<EditText
            android:id="@+id/signLabelInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Type the sign name here (e.g. A, Hello)" /> -->

        <!-- Tap this to start/stop saving landmark data for the sign you typed above -->
        <!--<Button
            android:id="@+id/recordButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Start Recording" /> -->

        <!-- Shows how many examples you've saved so far for the current sign -->
        <!--<TextView
            android:id="@+id/countText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="18sp"
            android:gravity="center"
            android:text="Samples collected: 0" /> -->

        <!--<Button
            android:id="@+id/btnTrain"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="TRAIN FURTHER"/>-->

        <!--<TextView
            android:id="@+id/detectedSignText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="No sign detected"
            android:textSize="24sp"
            android:gravity="center"
            android:padding="16dp" /> -->

        <Button
            android:id="@+id/btnRun"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Run Model" />
        <Button
            android:id="@+id/btnSpeech"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Speak"
            android:layout_marginTop="16dp" />

        <TextView
            android:id="@+id/speechResult"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Your spoken words will appear here"
            android:textSize="20sp"
            android:padding="16dp"
            android:layout_marginTop="16dp" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>

recognition_activity.xml:
<?xml version="1.0" encoding="utf-8"?>

<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/resultPanel"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <LinearLayout
        android:id="@+id/resultPanel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="20dp"
        android:background="#FFFFFF"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <TextView
            android:id="@+id/resultTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Detected sign"
            android:textSize="18sp"
            android:gravity="center" />

        <TextView
            android:id="@+id/detectedSignText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Starting camera..."
            android:textSize="28sp"
            android:textStyle="bold"
            android:gravity="center" />

    </LinearLayout>

    <androidx.appcompat.widget.AppCompatImageButton
        android:id="@+id/closeButton"
        android:layout_width="44dp"
        android:layout_height="44dp"
        android:layout_margin="16dp"
        android:background="@drawable/close_button_background"
        android:src="@drawable/ic_close"
        android:contentDescription="Return to home screen"
        android:padding="12dp"
        android:scaleType="fitCenter"
        android:elevation="8dp"
        app:tint="@android:color/white"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>

activity_training.xml:
<?xml version="1.0" encoding="utf-8"?>

<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.camera.view.PreviewView
        android:id="@+id/previewView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/trainingControls"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <LinearLayout
        android:id="@+id/trainingControls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp"
        android:background="#FFFFFF"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <EditText
            android:id="@+id/signLabelInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="Type the sign name (e.g. Hello)"
            android:inputType="text"
            android:padding="12dp" />

        <Button
            android:id="@+id/recordButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Start Recording" />

        <TextView
            android:id="@+id/countText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:gravity="center"
            android:text="Samples collected: 0"
            android:textSize="18sp" />

        <Button
            android:id="@+id/finishButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="Finish &amp; Save" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>

backup_rules.xml:
<?xml version="1.0" encoding="utf-8"?><!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older than API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>

data_extraction_rules.xml::
<?xml version="1.0" encoding="utf-8"?><!--
   Sample data extraction rules file; uncomment and customize as necessary.
   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
   for details.
-->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up.
        <include .../>
        <exclude .../>
        -->
    </cloud-backup>
    <!--
    <device-transfer>
        <include .../>
        <exclude .../>
    </device-transfer>
    -->
</data-extraction-rules>
build.gradle.kts:
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}
build.gradle.kts:
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "vision2touch.app.com"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vision2touch.app.com"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    val cameraxVersion = "1.5.0"

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mediapipe:tasks-vision:latest.release")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")



}
gradle.properties.kts:
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Gradle settings configured through the IDE *will override*
# any settings specified in this file.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. For more details, visit
# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
# org.gradle.parallel=true
# When enabled, the Configuration Cache allows Gradle to skip the configuration
# phase entirely if nothing that affects the build configuration (such as build scripts)
# has changed. Additionally, Gradle applies performance optimizations to task execution.
org.gradle.configuration-cache=true
# Kotlin code style for this project: "official" or "obsolete":
kotlin.code.style=official

gradle-wrapper.properties:
#Thu Sep 17 16:18:30 BST 2026
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

libs.versions.toml:
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.6.1"
material = "1.10.0"
activityKtx = "1.8.0"
constraintlayout = "2.1.4"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }

local.properties:
## This file is automatically generated by Android Studio.
# Do not modify this file -- YOUR CHANGES WILL BE ERASED!
#
# This file should *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.
#
# Location of the SDK. This is only used by Gradle.
# For customization when using a Version Control System, please read the
# header note.
sdk.dir=/Users/Johan/Library/Android/sdk

settings.gradle.kts:
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Vision2Touch"
include(":app")
