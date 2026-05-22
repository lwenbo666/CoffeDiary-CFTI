package com.example.coffeediary

import android.content.Context
import android.graphics.*
import android.util.Base64
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 咖啡杯轮廓抠图 — 使用 TFLite 背景移除模型
 *
 * 模型：BRIA RMBG-1.4（或等效模型）
 * 输入：1×1024×1024×3 (NHWC), float32, ImageNet 归一化
 * 输出：1×1024×1024×1, float32, sigmoid [0,1] 前景概率
 */
class CoffeeSegmenter(context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var isModelReady = false

    /** 模型输入尺寸（宽=高） */
    private val inputSize = 1024

    // ImageNet 归一化参数
    private val meanR = 0.485f; private val meanG = 0.456f; private val meanB = 0.406f
    private val stdR  = 0.229f; private val stdG  = 0.224f; private val stdB  = 0.225f

    init {
        loadModel(context)
    }

    private fun loadModel(context: Context) {
        try {
            // 优先尝试 rmbg.tflite，回退到 model.tflite
            val modelFile = listOf("rmbg.tflite", "model.tflite").firstOrNull { name ->
                context.assets.list("")?.contains(name) == true
            }
            if (modelFile == null) {
                android.util.Log.w("CoffeeSegmenter", "未找到 TFLite 模型文件")
                return
            }
            val modelBuffer = loadModelFile(context, modelFile)

            val delegateSet = mutableSetOf<String>()

            // 1️⃣ 尝试 GPU 委托
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                try {
                    gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                    val opts = Interpreter.Options().apply { addDelegate(gpuDelegate) }
                    interpreter = Interpreter(modelBuffer, opts)
                    delegateSet.add("GPU")
                    isModelReady = true
                    return
                } catch (e: Exception) {
                    gpuDelegate?.close(); gpuDelegate = null
                }
            }

            // 2️⃣ 尝试 NNAPI
            try {
                nnApiDelegate = NnApiDelegate()
                val opts = Interpreter.Options().apply { addDelegate(nnApiDelegate) }
                interpreter = Interpreter(modelBuffer, opts)
                delegateSet.add("NNAPI")
                isModelReady = true
                return
            } catch (e: Exception) {
                nnApiDelegate?.close(); nnApiDelegate = null
            }

            // 3️⃣ 回退到 CPU
            interpreter = Interpreter(modelBuffer)
            delegateSet.add("CPU")
            isModelReady = true

            android.util.Log.i("CoffeeSegmenter", "模型已加载，使用委托: ${delegateSet.joinToString()}")
        } catch (e: Exception) {
            android.util.Log.e("CoffeeSegmenter", "模型加载失败", e)
        }
    }

    fun isModelLoaded(): Boolean = isModelReady && interpreter != null

    /**
     * 对 Base64 图片做背景移除，返回透明背景的 PNG (Base64)
     * @param base64Input  输入图片（支持 data:image/...;base64,... 或纯 Base64）
     * @return 透明背景 PNG 的 Base64 字符串（data:image/png;base64,...），失败返回 null
     */
    fun segmentBase64Image(base64Input: String): String? {
        val tflite = interpreter ?: return null

        return try {
            val bytes = decodeBase64(base64Input)
            val srcBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            // 预处理 → 推理 → 后处理
            val inputBuffer = preprocess(srcBitmap)
            val outputMask = Array(1) { Array(inputSize) { FloatArray(inputSize) } }
            tflite.run(inputBuffer, outputMask)
            val resultBitmap = postprocess(srcBitmap, outputMask[0])
            srcBitmap.recycle()

            val base64Out = encodeToBase64Png(resultBitmap)
            resultBitmap.recycle()
            base64Out
        } catch (e: Exception) {
            android.util.Log.e("CoffeeSegmenter", "分割失败", e)
            null
        }
    }

    // ----------- 预处理 -----------

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        resized.recycle()

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8  and 0xFF) / 255.0f
            val b = (pixel       and 0xFF) / 255.0f
            // ImageNet 归一化
            buffer.putFloat((r - meanR) / stdR)
            buffer.putFloat((g - meanG) / stdG)
            buffer.putFloat((b - meanB) / stdB)
        }
        return buffer
    }

    // ----------- 后处理 -----------

    private fun postprocess(original: Bitmap, mask: Array<FloatArray>): Bitmap {
        val w = original.width
        val h = original.height
        val sx = inputSize.toFloat() / w
        val sy = inputSize.toFloat() / h

        val srcPixels = IntArray(w * h)
        original.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val my = (y * sy).toInt().coerceIn(0, inputSize - 1)
            for (x in 0 until w) {
                val mx = (x * sx).toInt().coerceIn(0, inputSize - 1)
                // sigmoid 已在模型内部完成，mask 值域 [0, 1]
                val alpha = (mask[my][mx] * 255).toInt().coerceIn(0, 255)
                val pixel = srcPixels[y * w + x]
                val r = pixel shr 16 and 0xFF
                val g = pixel shr 8  and 0xFF
                val b = pixel       and 0xFF
                outPixels[y * w + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ----------- 编解码 -----------

    private fun decodeBase64(base64: String): ByteArray {
        val clean = if (base64.contains(",")) base64.substringAfter(",") else base64
        return Base64.decode(clean, Base64.DEFAULT)
    }

    private fun encodeToBase64Png(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        stream.close()
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val inputStream = context.assets.open(filename)
        val outputStream = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var read: Int
        while (inputStream.read(buf).also { read = it } != -1) {
            outputStream.write(buf, 0, read)
        }
        val modelBytes = outputStream.toByteArray()
        inputStream.close()
        outputStream.close()
        return ByteBuffer.allocateDirect(modelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBytes)
        }
    }

    fun close() {
        interpreter?.close(); interpreter = null
        gpuDelegate?.close();  gpuDelegate  = null
        nnApiDelegate?.close(); nnApiDelegate = null
        isModelReady = false
    }
}
