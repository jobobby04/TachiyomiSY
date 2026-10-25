package tachiyomi.core.common.util.system

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.blue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import androidx.core.graphics.green
import androidx.core.graphics.red
import ca.mpreg.imagedecoder.ImageDecoder
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

object ImageUtil {

    fun isImage(name: String?, openStream: (() -> InputStream)? = null): Boolean {
        if (name == null) return false
        // SY -->
        if (File(name).extension.equals("cbi", ignoreCase = true)) return true
        // SY <--

        val extension = name.substringAfterLast('.')
        return ImageType.entries.any { it.extension == extension } || openStream?.let { findImageType(it) } != null
    }

    fun findImageType(openStream: () -> InputStream): ImageType? {
        return openStream().use { findImageType(it) }
    }

    fun findImageType(stream: InputStream): ImageType? {
        return try {
            val format = ImageDecoder.open(stream).use { dec -> dec.format }
            when (format) {
                ImageDecoder.Format.JPEG -> ImageType.JPEG
                ImageDecoder.Format.PNG -> ImageType.PNG
                ImageDecoder.Format.WEBP -> ImageType.WEBP
                ImageDecoder.Format.GIF -> ImageType.GIF
                ImageDecoder.Format.AVIF -> ImageType.AVIF
                ImageDecoder.Format.HEIF -> ImageType.HEIF
                ImageDecoder.Format.JXL -> ImageType.JXL
                ImageDecoder.Format.JP2 -> ImageType.JP2
                else -> null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "findImageType: ${e.message}" }
            null
        }
    }

    fun getExtensionFromMimeType(mime: String?, openStream: () -> InputStream): String {
        val type = mime?.let { ImageType.entries.find { it.mime == mime } } ?: findImageType(openStream)
        return type?.extension ?: "jpg"
    }

    fun isAnimatedAndSupported(source: BufferedSource): Boolean {
        return try {
            val type = findImageType(source.peek().inputStream()) ?: return false
            when (type) {
                ImageType.GIF -> true
                ImageType.WEBP, ImageType.HEIF -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
                    ImageDecoder.open(source.peek().inputStream()).use { dec -> dec.pages > 1 }
                }

                else -> false
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "isAnimatedAndSupported: ${e.message}" }
            false
        }
    }

    enum class ImageType(val mime: String, val extension: String) {
        AVIF("image/avif", "avif"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heif"),
        JPEG("image/jpeg", "jpg"),
        JXL("image/jxl", "jxl"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
        JP2("image/jp2", "jp2"),
        JPX("image/jpx", "jpx"),
    }

    /**
     * Check whether the image is wide (which we consider a double-page spread).
     *
     * @return true if the width is greater than the height
     */
    fun isWideImage(imageSource: BufferedSource): Boolean {
        val options = extractImageOptions(imageSource)
        return options.outWidth > options.outHeight
    }

    /**
     * Extract the 'side' part from [BufferedSource] and return it as [BufferedSource].
     */
    fun splitInHalf(imageSource: BufferedSource, side: Side, /* SY --> */sidePadding: Int/* SY <-- */): BufferedSource {
        val imageBitmap = BitmapFactory.decodeStream(imageSource.inputStream())
        val height = imageBitmap.height
        val width = imageBitmap.width

        val singlePage = Rect(0, 0, /* SY --> */width / 2 + sidePadding/* SY <-- */, height)

        val half = createBitmap(/* SY --> */width / 2 + sidePadding/* SY <-- */, height)
        val part = when (side) {
            Side.RIGHT -> Rect(width - width / 2, 0, width, height)
            Side.LEFT -> Rect(0, 0, width / 2, height)
        }
        half.applyCanvas {
            drawBitmap(imageBitmap, part, singlePage, null)
        }
        val output = Buffer()
        half.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())

        return output
    }

    fun rotateImage(imageSource: BufferedSource, degrees: Float): BufferedSource {
        val imageBitmap = BitmapFactory.decodeStream(imageSource.inputStream())
        val rotated = rotateBitMap(imageBitmap, degrees)

        val output = Buffer()
        rotated.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())

        return output
    }

    private fun rotateBitMap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Split the image into left and right parts, then merge them into a
     * new vertically-aligned image.
     */
    fun splitAndMerge(imageSource: BufferedSource, upperSide: Side): BufferedSource {
        val imageBitmap = BitmapFactory.decodeStream(imageSource.inputStream())
        val height = imageBitmap.height
        val width = imageBitmap.width

        val result = createBitmap(width / 2, height * 2)
        result.applyCanvas {
            // right -> upper
            val rightPart = when (upperSide) {
                Side.RIGHT -> Rect(width - width / 2, 0, width, height)
                Side.LEFT -> Rect(0, 0, width / 2, height)
            }
            val upperPart = Rect(0, 0, width / 2, height)
            drawBitmap(imageBitmap, rightPart, upperPart, null)
            // left -> bottom
            val leftPart = when (upperSide) {
                Side.LEFT -> Rect(width - width / 2, 0, width, height)
                Side.RIGHT -> Rect(0, 0, width / 2, height)
            }
            val bottomPart = Rect(0, height, width / 2, height * 2)
            drawBitmap(imageBitmap, leftPart, bottomPart, null)
        }

        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
        return output
    }

    enum class Side {
        RIGHT,
        LEFT,
    }
    // SY -->

    /**
     * Split the image into left and right parts, then merge them into a
     * new image with added center padding scaled relative to the height of the display view
     * to compensate for scaling.
     */
    fun addHorizontalCenterMargin(
        imageSource: InputStream,
        viewHeight: Int,
        backgroundContext: Context,
    ): BufferedSource {
        val imageBitmap = try {
            ImageDecoder.open(imageSource).use { it.decodeNext() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "addHorizontalCenterMargin" }
            throw e
        }.use { it.toBitmap() }
        val height = imageBitmap.height
        val width = imageBitmap.width

        val centerPadding = 96 / (max(1, viewHeight) / height).coerceAtLeast(1)

        val leftSourcePart = Rect(0, 0, width / 2, height)
        val rightSourcePart = Rect(width / 2, 0, width, height)
        val leftTargetPart = Rect(0, 0, width / 2, height)
        val rightTargetPart = Rect(width / 2 + centerPadding, 0, width + centerPadding, height)

        val bgColor = chooseBackground(backgroundContext, imageSource)
        bgColor.setBounds(width / 2, 0, width / 2 + centerPadding, height)
        val result = createBitmap(width + centerPadding, height)

        result.applyCanvas {
            drawBitmap(imageBitmap, leftSourcePart, leftTargetPart, null)
            drawBitmap(imageBitmap, rightSourcePart, rightTargetPart, null)
            bgColor.draw(this)
        }

        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
        return output
    }
    // SY <--

    /**
     * Algorithm for determining what background to accompany a comic/manga page
     */
    fun chooseBackground(context: Context, imageStream: InputStream): Drawable {
        val image = try {
            ImageDecoder.open(imageStream).use { it.decodeNext() to it.isHdr }.let { (res, hdr) ->
                res.use {
                    val config = if (hdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
                    createBitmap(res.width, res.height, config).also { bitmap ->
                        res.image.rewind()
                        bitmap.copyPixelsFromBuffer(res.image)
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "chooseBackground: ${e.message}" }
            null
        }

        if (image == null) return Color.WHITE.toDrawable()
        return try {
            chooseBackground(context, image)
        } finally {
            image.recycle()
        }
    }

    private fun chooseBackground(context: Context, image: Bitmap): Drawable {
        val whiteColor = Color.WHITE
        if (image.width < 50 || image.height < 50) {
            return whiteColor.toDrawable()
        }

        val top = 5
        val bot = image.height - 5
        val left = (image.width * 0.0275).toInt()
        val right = image.width - left
        val midX = image.width / 2
        val midY = image.height / 2
        val offsetX = (image.width * 0.01).toInt()
        val leftOffsetX = left - offsetX
        val rightOffsetX = right + offsetX

        val topLeftPixel = image[left, top]
        val topRightPixel = image[right, top]
        val midLeftPixel = image[left, midY]
        val midRightPixel = image[right, midY]
        val topCenterPixel = image[midX, top]
        val botLeftPixel = image[left, bot]
        val bottomCenterPixel = image[midX, bot]
        val botRightPixel = image[right, bot]

        val topLeftIsDark = topLeftPixel.isDark()
        val topRightIsDark = topRightPixel.isDark()
        val midLeftIsDark = midLeftPixel.isDark()
        val midRightIsDark = midRightPixel.isDark()
        val topMidIsDark = topCenterPixel.isDark()
        val botLeftIsDark = botLeftPixel.isDark()
        val botRightIsDark = botRightPixel.isDark()

        var darkBG =
            (topLeftIsDark && (botLeftIsDark || botRightIsDark || topRightIsDark || midLeftIsDark || topMidIsDark)) ||
                (topRightIsDark && (botRightIsDark || botLeftIsDark || midRightIsDark || topMidIsDark))

        val topAndBotPixels =
            listOf(topLeftPixel, topCenterPixel, topRightPixel, botRightPixel, bottomCenterPixel, botLeftPixel)
        val isNotWhiteAndCloseTo = topAndBotPixels.mapIndexed { index, color ->
            val other = topAndBotPixels[(index + 1) % topAndBotPixels.size]
            !color.isWhite() && color.isCloseTo(other)
        }
        if (isNotWhiteAndCloseTo.all { it }) {
            return topLeftPixel.toDrawable()
        }

        val cornerPixels = listOf(topLeftPixel, topRightPixel, botLeftPixel, botRightPixel)
        val numberOfWhiteCorners = cornerPixels.map { cornerPixel -> cornerPixel.isWhite() }
            .filter { it }
            .size
        if (numberOfWhiteCorners > 2) {
            darkBG = false
        }

        var blackColor = when {
            topLeftIsDark -> topLeftPixel
            topRightIsDark -> topRightPixel
            botLeftIsDark -> botLeftPixel
            botRightIsDark -> botRightPixel
            else -> whiteColor
        }

        var overallWhitePixels = 0
        var overallBlackPixels = 0
        var topBlackStreak = 0
        var topWhiteStreak = 0
        var botBlackStreak = 0
        var botWhiteStreak = 0
        outer@ for (x in intArrayOf(left, right, leftOffsetX, rightOffsetX)) {
            var whitePixelsStreak = 0
            var whitePixels = 0
            var blackPixelsStreak = 0
            var blackPixels = 0
            var blackStreak = false
            var whiteStreak = false
            val notOffset = x == left || x == right
            inner@ for ((index, y) in (0..<image.height step image.height / 25).withIndex()) {
                val pixel = image[x, y]
                val pixelOff = image[x + (if (x < image.width / 2) -offsetX else offsetX), y]
                if (pixel.isWhite()) {
                    whitePixelsStreak++
                    whitePixels++
                    if (notOffset) {
                        overallWhitePixels++
                    }
                    if (whitePixelsStreak > 14) {
                        whiteStreak = true
                    }
                    if (whitePixelsStreak > 6 && whitePixelsStreak >= index - 1) {
                        topWhiteStreak = whitePixelsStreak
                    }
                } else {
                    whitePixelsStreak = 0
                    if (pixel.isDark() && pixelOff.isDark()) {
                        blackPixels++
                        if (notOffset) {
                            overallBlackPixels++
                        }
                        blackPixelsStreak++
                        if (blackPixelsStreak >= 14) {
                            blackStreak = true
                        }
                        continue@inner
                    }
                }
                if (blackPixelsStreak > 6 && blackPixelsStreak >= index - 1) {
                    topBlackStreak = blackPixelsStreak
                }
                blackPixelsStreak = 0
            }
            if (blackPixelsStreak > 6) {
                botBlackStreak = blackPixelsStreak
            } else if (whitePixelsStreak > 6) {
                botWhiteStreak = whitePixelsStreak
            }
            when {
                blackPixels > 22 -> {
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    darkBG = true
                    overallWhitePixels = 0
                    break@outer
                }
                blackStreak -> {
                    darkBG = true
                    if (x == right || x == rightOffsetX) {
                        blackColor = when {
                            topRightIsDark -> topRightPixel
                            botRightIsDark -> botRightPixel
                            else -> blackColor
                        }
                    }
                    if (blackPixels > 18) {
                        overallWhitePixels = 0
                        break@outer
                    }
                }
                whiteStreak || whitePixels > 22 -> darkBG = false
            }
        }

        val topIsBlackStreak = topBlackStreak > topWhiteStreak
        val bottomIsBlackStreak = botBlackStreak > botWhiteStreak
        if (overallWhitePixels > 9 && overallWhitePixels > overallBlackPixels) {
            darkBG = false
        }
        if (topIsBlackStreak && bottomIsBlackStreak) {
            darkBG = true
        }

        val isLandscape = context.resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            return when {
                darkBG -> blackColor.toDrawable()
                else -> whiteColor.toDrawable()
            }
        }

        val botCornersIsWhite = botLeftPixel.isWhite() && botRightPixel.isWhite()
        val topCornersIsWhite = topLeftPixel.isWhite() && topRightPixel.isWhite()

        val topCornersIsDark = topLeftIsDark && topRightIsDark
        val botCornersIsDark = botLeftIsDark && botRightIsDark

        val topOffsetCornersIsDark = image[leftOffsetX, top].isDark() && image[rightOffsetX, top].isDark()
        val botOffsetCornersIsDark = image[leftOffsetX, bot].isDark() && image[rightOffsetX, bot].isDark()

        val gradient = when {
            darkBG && botCornersIsWhite -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }
            darkBG && topCornersIsWhite -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }
            darkBG -> {
                return blackColor.toDrawable()
            }
            topIsBlackStreak ||
                (
                    topCornersIsDark &&
                        topOffsetCornersIsDark &&
                        (topMidIsDark || overallBlackPixels > 9)
                    ) -> {
                intArrayOf(blackColor, blackColor, whiteColor, whiteColor)
            }
            bottomIsBlackStreak ||
                (
                    botCornersIsDark &&
                        botOffsetCornersIsDark &&
                        (bottomCenterPixel.isDark() || overallBlackPixels > 9)
                    ) -> {
                intArrayOf(whiteColor, whiteColor, blackColor, blackColor)
            }
            else -> {
                return whiteColor.toDrawable()
            }
        }

        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            gradient,
        )
    }

    private fun @receiver:ColorInt Int.isDark(): Boolean =
        red < 40 && blue < 40 && green < 40 && alpha > 200

    private fun @receiver:ColorInt Int.isCloseTo(other: Int): Boolean =
        abs(red - other.red) < 30 && abs(green - other.green) < 30 && abs(blue - other.blue) < 30

    private fun @receiver:ColorInt Int.isWhite(): Boolean =
        red + blue + green > 740

    /**
     * Used to check an image's dimensions without loading it in the memory.
     */
    private fun extractImageOptions(imageSource: BufferedSource): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(imageSource.peek().inputStream(), null, options)
        return options
    }

    // SY -->
    fun ImageDecoder.Frame.toBitmap(): Bitmap {
        return createBitmap(width, height).also { bitmap ->
            image.rewind()
            bitmap.copyPixelsFromBuffer(image)
        }
    }

    fun mergeBitmaps(
        imageDecodeResult: ImageDecoder.Frame,
        imageDecodeResult2: ImageDecoder.Frame,
        isLTR: Boolean,
        centerMargin: Int,
        @ColorInt background: Int = Color.WHITE,
        progressCallback: ((Int) -> Unit)? = null,
    ): BufferedSource {
        val height = imageDecodeResult.height
        val width = imageDecodeResult.width
        val height2 = imageDecodeResult2.height
        val width2 = imageDecodeResult2.width

        val maxHeight = max(height, height2)

        val result = createBitmap(width + width2 + centerMargin, max(height, height2))
        val canvas = Canvas(result)
        canvas.drawColor(background)
        val upperPart = Rect(
            if (isLTR) 0 else width2 + centerMargin,
            (maxHeight - height) / 2,
            (if (isLTR) 0 else width2 + centerMargin) + width,
            height + (maxHeight - height) / 2,
        )

        val imageBitmap = imageDecodeResult.toBitmap()
        canvas.drawBitmap(imageBitmap, imageBitmap.rect, upperPart, null)
        progressCallback?.invoke(98)
        val bottomPart = Rect(
            if (!isLTR) 0 else width + centerMargin,
            (maxHeight - height2) / 2,
            (if (!isLTR) 0 else width + centerMargin) + width2,
            height2 + (maxHeight - height2) / 2,
        )

        val imageBitmap2 = imageDecodeResult2.toBitmap()
        canvas.drawBitmap(imageBitmap2, imageBitmap2.rect, bottomPart, null)
        progressCallback?.invoke(99)

        val output = Buffer()
        result.compress(Bitmap.CompressFormat.JPEG, 100, output.outputStream())
        progressCallback?.invoke(100)
        return output
    }

    private val Bitmap.rect: Rect
        get() = Rect(0, 0, width, height)
    // SY <--
}
