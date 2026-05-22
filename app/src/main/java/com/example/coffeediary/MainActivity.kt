package com.example.coffeediary

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var splashOverlay: SplashOverlayView
    private lateinit var db: AppDatabase
    private var isPageLoaded = false
    private var isSplashDone = false
    private var currentPage = "main"

    /** 咖啡杯抠图分割器（懒加载） */
    private val segmenter by lazy { CoffeeSegmenter(this) }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleGalleryResult(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 Room 数据库
        db = AppDatabase.getInstance(this)

        webView = findViewById(R.id.webView)
        splashOverlay = findViewById(R.id.splashOverlay)

        // 配置 WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
        // 启用硬件加速层，提升 WebView 渲染性能
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isPageLoaded = true
                tryDismissSplash()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                val grantResults = resources.mapNotNull { resource ->
                    if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    ) {
                        resource
                    } else {
                        null
                    }
                }.toTypedArray()
                if (grantResults.isNotEmpty()) {
                    request.grant(grantResults)
                }
            }
        }

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // 启动启动动画
        splashOverlay.startAnimation()

        // 启动动画结束时的回调
        splashOverlay.animator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(a: android.animation.Animator) {}
            override fun onAnimationEnd(a: android.animation.Animator) {
                isSplashDone = true
                tryDismissSplash()
            }
            override fun onAnimationCancel(a: android.animation.Animator) {}
            override fun onAnimationRepeat(a: android.animation.Animator) {}
        })

        // 后台加载 HTML
        webView.loadUrl("file:///android_asset/main.html")

        // 运行时申请摄像头权限
        requestCameraPermission()
    }

    /**
     * 当页面加载完成且启动动画结束时，关闭启动屏
     */
    private fun tryDismissSplash() {
        if (isPageLoaded && isSplashDone) {
            // Splash 结束后恢复白色主题的系统栏
            window.statusBarColor = ContextCompat.getColor(this, R.color.white)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }

            splashOverlay.dismiss {
                webView.visibility = View.VISIBLE
                // 通知 WebView 启动入场动画（之前被 splash 遮住，动画一直 paused）
                webView.evaluateJavascript("document.body.classList.add('app-ready')", null)
            }
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        }
    }

    override fun onBackPressed() {
        if (currentPage == "menu") {
            webView.loadUrl("file:///android_asset/main.html")
            currentPage = "main"
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun handleGalleryResult(uri: Uri) {
        var bitmap: Bitmap? = null
        var resized: Bitmap? = null
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 缩放到最大 800px 宽
            val maxSize = 800
            var w = bitmap.width
            var h = bitmap.height
            if (w > maxSize || h > maxSize) {
                val ratio = min(maxSize.toFloat() / w, maxSize.toFloat() / h)
                w = (w * ratio).toInt()
                h = (h * ratio).toInt()
            }
            resized = Bitmap.createScaledBitmap(bitmap, w, h, true)

            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            outputStream.close()

            runOnUiThread {
                webView.evaluateJavascript("onGalleryImage('$base64')") { }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show()
            }
        } finally {
            bitmap?.recycle()
            resized?.recycle()
        }
    }

    inner class AndroidBridge {
        @android.webkit.JavascriptInterface
        fun loadRecords(): String {
            return runBlocking {
                val records = db.coffeeRecordDao().getAllRecords()
                JSONArray().apply {
                    records.forEach { r ->
                        put(JSONObject().apply {
                            put("id", r.id)
                            put("name", r.name)
                            put("date", r.date)
                            put("photo", r.photo)
                            put("temp", r.temp)
                            put("sugar", r.sugar)
                        })
                    }
                }.toString()
            }
        }

        @android.webkit.JavascriptInterface
        fun saveRecord(json: String): Long {
            val obj = JSONObject(json)
            val record = CoffeeRecord(
                name = obj.getString("name"),
                date = obj.getString("date"),
                photo = obj.optString("photo", ""),
                temp = obj.optString("temp", "ice"),
                sugar = obj.optString("sugar", "half")
            )
            return runBlocking { db.coffeeRecordDao().insertRecord(record) }
        }

        @android.webkit.JavascriptInterface
        fun deleteRecord(id: Long) {
            runBlocking { db.coffeeRecordDao().deleteRecord(id) }
        }

        @android.webkit.JavascriptInterface
        fun deleteRecordsByName(name: String) {
            runBlocking { db.coffeeRecordDao().deleteRecordsByName(name) }
        }

        @android.webkit.JavascriptInterface
        fun saveImage(base64: String) {
            saveImageToGallery(base64)
        }

        @android.webkit.JavascriptInterface
        fun openGallery() {
            runOnUiThread {
                galleryLauncher.launch("image/*")
            }
        }

        @android.webkit.JavascriptInterface
        fun loadPage(page: String) {
            runOnUiThread {
                when (page) {
                    "menu" -> {
                        currentPage = "menu"
                        webView.loadUrl("file:///android_asset/menu.html")
                    }
                    "main" -> {
                        currentPage = "main"
                        webView.loadUrl("file:///android_asset/main.html")
                    }
                }
            }
        }

        /**
         * 咖啡杯轮廓抠图：对 Base64 图片做背景移除，返回透明背景 PNG
         * @param base64Image 输入（支持 data:image/...;base64,... 或纯 Base64）
         * @return 透明 PNG 的 Base64 字符串，失败返回空字符串
         */
        @android.webkit.JavascriptInterface
        fun segmentCoffeeCup(base64Image: String): String {
            if (!segmenter.isModelLoaded()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "抠图模型未就绪", Toast.LENGTH_SHORT).show()
                }
                return ""
            }
            return try {
                segmenter.segmentBase64Image(base64Image) ?: ""
            } catch (e: Exception) {
                android.util.Log.e("Segment", "抠图失败", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "抠图处理失败", Toast.LENGTH_SHORT).show()
                }
                ""
            }
        }
    }

    private fun saveImageToGallery(base64: String) {
        try {
            val bytes = Base64.decode(base64.split(",")[1], Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "CFTI_${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CoffeeDiary")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            val outputStream = resolver.openOutputStream(uri!!)
            outputStream?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            runOnUiThread {
                Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_CAMERA = 100
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "摄像头权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要摄像头权限才能拍照", Toast.LENGTH_SHORT).show()
            }
        }
    }
}