package com.arruler.app

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: OverlayView
    private lateinit var status: TextView
    private lateinit var total: TextView
    private lateinit var btnUnit: MaterialButton

    private var session: Session? = null
    private var installRequested = false
    private val background = BackgroundRenderer()

    private var unit = UnitSystem.METRIC
    private var viewportChanged = false
    private var width = 1
    private var height = 1

    // GL-thread-only state
    private val anchors = ArrayList<Anchor>()
    private var latestHit: Pose? = null

    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)
    private val vpM = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        surface = findViewById(R.id.surface)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        total = findViewById(R.id.total)
        btnUnit = findViewById(R.id.btnUnit)

        surface.preserveEGLContextOnPause = true
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surface.setRenderer(this)
        surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surface.setWillNotDraw(false)

        findViewById<MaterialButton>(R.id.btnAdd).setOnClickListener { surface.queueEvent { addPoint() } }
        findViewById<MaterialButton>(R.id.btnUndo).setOnClickListener { surface.queueEvent { undo() } }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener { surface.queueEvent { clearAll() } }
        btnUnit.setOnClickListener {
            unit = unit.next()
            overlay.unit = unit
            btnUnit.text = unit.label
        }
        btnUnit.text = unit.label
        overlay.unit = unit
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
                    return
                }
                session = Session(this).also { configure(it) }
            } catch (e: UnavailableUserDeclinedInstallationException) {
                toastAndFinish(getString(R.string.arcore_unsupported)); return
            } catch (e: Exception) {
                toastAndFinish("${getString(R.string.arcore_unsupported)}: ${e.message}"); return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            toastAndFinish("Camera not available. Close other apps using it and retry."); return
        }
        surface.onResume()
        viewportChanged = true
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            surface.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.isEmpty() || results[0] != PackageManager.PERMISSION_GRANTED) {
            toastAndFinish(getString(R.string.camera_needed))
        }
    }

    private fun configure(s: Session) {
        val config = Config(s)
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.focusMode = Config.FocusMode.AUTO
        if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        }
        s.configure(config)
    }

    private fun toastAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    // ---- Measurement actions (GL thread) ----

    private fun addPoint() {
        val pose = latestHit ?: return
        val s = session ?: return
        anchors.add(s.createAnchor(pose))
    }

    private fun undo() {
        if (anchors.isNotEmpty()) anchors.removeAt(anchors.size - 1).detach()
    }

    private fun clearAll() {
        anchors.forEach { it.detach() }
        anchors.clear()
    }

    // ---- GLSurfaceView.Renderer ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        GLES20.glViewport(0, 0, w, h)
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return

        if (viewportChanged) {
            @Suppress("DEPRECATION")
            val rotation = windowManager.defaultDisplay.rotation
            s.setDisplayGeometry(rotation, width, height)
            viewportChanged = false
        }
        s.setCameraTextureName(background.textureId)

        val frame = try { s.update() } catch (e: CameraNotAvailableException) { return }
        background.draw(frame)

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            latestHit = null
            publish(Snapshot(tracking = false))
            updateUi(false, null, 0f)
            return
        }

        camera.getViewMatrix(viewM, 0)
        camera.getProjectionMatrix(projM, 0, 0.02f, 50f)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)

        // Reticle: hit-test the screen center.
        var hitPose: Pose? = null
        for (hit in frame.hitTest(width / 2f, height / 2f)) {
            val t = hit.trackable
            val ok = (t is Plane && t.isPoseInPolygon(hit.hitPose)) ||
                (t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) ||
                t is DepthPoint
            if (ok) { hitPose = hit.hitPose; break }
        }
        latestHit = hitPose

        // Drop anchors that stopped tracking being valid (stopped) from display.
        val poses = anchors.filter { it.trackingState == TrackingState.TRACKING }
            .map { it.pose }
        val screenPts = poses.map { project(it) }
        val segs = (0 until (poses.size - 1).coerceAtLeast(0)).map { dist(poses[it], poses[it + 1]) }
        val totalM = segs.sum()
        val live = if (hitPose != null && poses.isNotEmpty()) dist(poses.last(), hitPose) else null

        publish(
            Snapshot(
                tracking = true,
                reticle = ScreenPt(width / 2f, height / 2f),
                reticleHit = hitPose != null,
                points = screenPts,
                segments = segs,
                liveMeters = live,
                totalMeters = totalM + (live ?: 0f),
            )
        )
        updateUi(true, hitPose, totalM + (live ?: 0f), poses.size)
    }

    private fun publish(snap: Snapshot) {
        overlay.snapshot = snap
        overlay.postInvalidate()
    }

    private var lastTotal = Float.NaN
    private var lastStatus = ""
    private var lastUnit: UnitSystem? = null
    private fun updateUi(tracking: Boolean, hit: Pose?, totalM: Float, count: Int = 0) {
        val msg = when {
            !tracking -> getString(R.string.move_phone)
            hit == null -> getString(R.string.aim)
            else -> getString(R.string.ready)
        }
        val shown = if (count > 0) totalM else Float.NaN
        val unitNow = unit
        val changed = msg != lastStatus || unitNow != lastUnit ||
            (shown.isNaN() != lastTotal.isNaN()) || (!shown.isNaN() && kotlin.math.abs(shown - lastTotal) > 0.0005f)
        if (changed) {
            lastStatus = msg; lastTotal = shown; lastUnit = unitNow
            runOnUiThread {
                status.text = msg
                total.text = if (shown.isNaN()) "—" else "Total: " + unitNow.format(shown)
            }
        }
    }

    // ---- Math ----

    private fun dist(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx(); val dy = a.ty() - b.ty(); val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** World position -> screen pixels, or null if behind the camera. */
    private fun project(p: Pose): ScreenPt? {
        val x = p.tx(); val y = p.ty(); val z = p.tz()
        val cx = vpM[0] * x + vpM[4] * y + vpM[8] * z + vpM[12]
        val cy = vpM[1] * x + vpM[5] * y + vpM[9] * z + vpM[13]
        val cw = vpM[3] * x + vpM[7] * y + vpM[11] * z + vpM[15]
        if (cw <= 0.0001f) return null
        val nx = cx / cw; val ny = cy / cw
        return ScreenPt((nx * 0.5f + 0.5f) * width, (1f - (ny * 0.5f + 0.5f)) * height)
    }
}
