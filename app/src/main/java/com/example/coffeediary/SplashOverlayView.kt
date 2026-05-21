package com.example.coffeediary

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class SplashOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== 颜色 ====================
    private val bgColor = Color.parseColor("#1A0E0C") // 深咖啡黑
    private val accentGold = Color.parseColor("#D4A574") // 金咖啡
    private val accentCream = Color.parseColor("#F5E0C3") // 奶白

    // ==================== 图标 ====================
    private val iconBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.icon)
    private var iconScale = 100f // dp, converted to px in init
    private var cachedIconBitmap: Bitmap? = null
    private var cachedIconSize: Int = -1

    // ==================== 画笔 ====================
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentGold
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentGold
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentCream
        style = Paint.Style.FILL
        textSize = 64f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentGold
        style = Paint.Style.FILL
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        alpha = 180
    }

    // ==================== 文字路径 ====================
    private data class LetterPath(val path: Path, val length: Float, val pathMeasure: PathMeasure)
    private val letterPaths: List<LetterPath>

    // ==================== 图标画笔 ====================
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val totalAnimDuration = 2200L

    init {
        val density = context.resources.displayMetrics.density
        textFillPaint.textSize = 64f * density
        strokePaint.strokeWidth = 4f * density
        ringPaint.strokeWidth = 3f * density
        subtitlePaint.textSize = 14f * density
        iconScale *= density

        // 创建每个字母的路径
        val letters = listOf(
            Pair('C', "C"),
            Pair('F', "F"),
            Pair('T', "T"),
            Pair('I', "I")
        )

        val letterSpacing = 8f * density
        val textSize = 64f * density
        val totalWidth = letters.sumOf { letterWidth(it.first, textSize).toDouble() }.toFloat() +
                letterSpacing * (letters.size - 1)

        letterPaths = buildList {
            var cursorX = 0f
            val offsetX = -totalWidth / 2f

            letters.forEach { (char, _) ->
                val path = Path()
                val charWidth = letterWidth(char, textSize)
                
                // 使用 getTextPath 获取文字的描边路径
                textFillPaint.getTextPath(
                    char.toString(), 0, 1,
                    offsetX + cursorX + charWidth / 2f, 0f,
                    path
                )

                val pm = PathMeasure(path, false)
                val length = pm.length
                add(LetterPath(path, length, pm))
                cursorX += charWidth + letterSpacing
            }
        }
    }

    private fun letterWidth(char: Char, textSize: Float): Float {
        val rect = Rect()
        textFillPaint.getTextBounds(char.toString(), 0, 1, rect)
        return rect.width().toFloat()
    }

    // ==================== 动画 ====================
    private var progress = 0f
    private var isAnimationStarted = false

    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = totalAnimDuration
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
            progress = anim.animatedValue as Float
            invalidate()
        }
    }

    fun startAnimation() {
        if (!isAnimationStarted) {
            isAnimationStarted = true
            animator.start()
        }
    }

    fun isAnimationRunning(): Boolean = animator.isRunning

    fun dismiss(onDismiss: () -> Unit) {
        animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                visibility = View.GONE
                onDismiss()
            }
            .start()
    }

    // ==================== 阶段计算 ====================
    private fun phase(from: Float, to: Float): Float {
        return ((progress - from) / (to - from)).coerceIn(0f, 1f)
    }

    // ==================== 绘制 ====================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val density = context.resources.displayMetrics.density
        val iconRadius = iconScale / 2f

        // --- 背景：始终全黑，不渐入 ---
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // --- 环形：立即开始绘制 ---
        val ringProgress = phase(0f, 0.4f)
        if (ringProgress > 0f) {
            ringPaint.alpha = 255
            val ringRect = RectF(
                cx - iconRadius - 12f * density,
                cy * 0.35f - iconRadius - 12f * density,
                cx + iconRadius + 12f * density,
                cy * 0.35f + iconRadius + 12f * density
            )
            canvas.drawArc(ringRect, -90f, 360f * ringProgress, false, ringPaint)
        }

        // --- 图标 Bitmap ---
        val iconAlpha = phase(0.4f, 0.7f)
        if (iconAlpha > 0f) {
            val targetSize = (iconRadius * 2 * 0.8).toInt()
            if (cachedIconSize != targetSize || cachedIconBitmap == null) {
                cachedIconBitmap?.recycle()
                cachedIconBitmap = Bitmap.createScaledBitmap(
                    iconBitmap, targetSize, targetSize, true
                )
                cachedIconSize = targetSize
            }
            val scaledIcon = cachedIconBitmap!!
            // 圆形裁切图标
            val iconPath = Path().apply {
                addCircle(cx, cy * 0.35f, iconRadius * 0.8f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(iconPath)
            iconPaint.alpha = (iconAlpha * 255).toInt()
            canvas.drawBitmap(
                scaledIcon,
                cx - scaledIcon.width / 2f,
                cy * 0.35f - scaledIcon.height / 2f,
                iconPaint
            )
            canvas.restore()
        }

        // --- 文字 C F T I 描边动画 ---
        val textCy = cy * 0.35f + iconRadius + 60f * density + 40f * density
        canvas.save()
        canvas.translate(cx, textCy)

        letterPaths.forEachIndexed { index, letterPath ->
            val letterStart = 0.05f + index * 0.08f
            val letterEnd = letterStart + 0.18f
            val letterPhase = phase(letterStart, letterEnd)
            if (letterPhase > 0f) {
                val drawPath = Path()
                val stop = letterPath.length * letterPhase.coerceAtMost(1f)
                letterPath.pathMeasure.getSegment(0f, stop, drawPath, true)
                strokePaint.alpha = 255
                canvas.drawPath(drawPath, strokePaint)
            }
        }

        // --- 文字填充过渡 ---
        val fillPhase = phase(0.45f, 0.7f)
        if (fillPhase > 0f) {
            textFillPaint.alpha = (fillPhase * 255).toInt()
            canvas.drawText("CFTI", 0f, 0f, textFillPaint)
        }

        canvas.restore()

        // --- 副标题 ---
        val subtitleCy = textCy + 40f * density
        val subtitlePhase = phase(0.6f, 0.9f)
        if (subtitlePhase > 0f) {
            subtitlePaint.alpha = (subtitlePhase * 180).toInt()
            canvas.drawText("COFFEE  PERSONALITY", cx, subtitleCy, subtitlePaint)
        }
    }
}
