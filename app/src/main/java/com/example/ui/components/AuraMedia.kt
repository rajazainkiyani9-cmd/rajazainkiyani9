package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.size.Precision
import com.example.ui.theme.AuraAmber
import com.example.ui.theme.AuraCoral
import com.example.ui.theme.AuraMagenta
import com.example.ui.theme.AuraStoryGradient
import com.example.ui.theme.AuraViolet
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val AURA_FILTERS = listOf("Original", "Clarendon", "Juno", "Lark", "Moon", "Vintage")

private val decodedImageByteCache = LruCache<String, ByteArray>(96)
private val IMAGE_LOADER_LOCK = Any()
@Volatile private var cachedImageLoader: ImageLoader? = null

fun getAuraImageLoader(context: Context): ImageLoader {
  return cachedImageLoader ?: synchronized(IMAGE_LOADER_LOCK) {
    cachedImageLoader ?: ImageLoader.Builder(context.applicationContext)
      .memoryCache {
        MemoryCache.Builder(context.applicationContext)
          .maxSizePercent(0.25)
          .build()
      }
      .diskCache {
        DiskCache.Builder()
          .directory(context.applicationContext.cacheDir.resolve("aura_image_cache"))
          .maxSizeBytes(64L * 1024 * 1024)
          .build()
      }
      .bitmapConfig(Bitmap.Config.ARGB_8888)
      .allowHardware(true)
      .respectCacheHeaders(false)
      .crossfade(false)
      .build()
      .also { cachedImageLoader = it }
  }
}

fun filterColorMatrix(filterName: String): ColorMatrix? {
  return when (filterName) {
    "Clarendon" ->
      ColorMatrix(
        floatArrayOf(
          1.15f, 0f, 0f, 0f, 5f,
          0f, 1.08f, 0f, 0f, 5f,
          0f, 0f, 1.25f, 0f, 12f,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    "Juno" ->
      ColorMatrix(
        floatArrayOf(
          1.22f, 0.05f, 0f, 0f, 8f,
          0f, 1.05f, 0f, 0f, 0f,
          0f, 0f, 0.90f, 0f, -5f,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    "Lark" ->
      ColorMatrix(
        floatArrayOf(
          1.05f, 0.08f, 0.05f, 0f, 12f,
          0f, 1.12f, 0.05f, 0f, 10f,
          0f, 0f, 1.08f, 0f, 8f,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    "Moon" -> ColorMatrix().apply { setToSaturation(0f) }
    "Vintage" ->
      ColorMatrix(
        floatArrayOf(
          1.10f, 0.15f, 0.05f, 0f, 10f,
          0.05f, 1.02f, 0.05f, 0f, 5f,
          0.0f, 0.05f, 0.80f, 0f, 10f,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    else -> null
  }
}

suspend fun compressUriToBase64DataUri(
  context: Context,
  uri: Uri,
  highQuality: Boolean = true,
): String? =
  withContext(Dispatchers.IO) {
    try {
      val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, boundsOpts)
      }
      val maxDim = if (highQuality) 1440 else 720
      var sampleSize = 1
      val rawW = boundsOpts.outWidth
      val rawH = boundsOpts.outHeight
      if (rawW > 0 && rawH > 0) {
        while ((rawW / sampleSize) > maxDim * 2 || (rawH / sampleSize) > maxDim * 2) {
          sampleSize *= 2
        }
      }

      val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
      val original =
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@withContext null

      compressBitmapInternal(original, maxDim = maxDim, highQuality = highQuality)
    } catch (_: Exception) {
      null
    }
  }

suspend fun compressBitmapToBase64DataUri(
  bitmap: Bitmap,
  highQuality: Boolean = true,
): String? =
  withContext(Dispatchers.IO) {
    try {
      val maxDim = if (highQuality) 1440 else 720
      compressBitmapInternal(bitmap, maxDim = maxDim, recycleInput = false, highQuality = highQuality)
    } catch (_: Exception) {
      null
    }
  }

suspend fun renderTextStoryDataUri(startColor: Int, endColor: Int): String =
  withContext(Dispatchers.Default) {
    val width = 720
    val height = 1280
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint =
      Paint().apply {
        shader =
          LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP,
          )
      }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    compressBitmapInternal(bmp, maxDim = 1280, recycleInput = true, highQuality = true)
  }

private fun compressBitmapInternal(
  original: Bitmap,
  maxDim: Int = 1440,
  recycleInput: Boolean = true,
  highQuality: Boolean = true,
): String {
  val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
  val targetW = (original.width * scale).toInt().coerceAtLeast(1)
  val targetH = (original.height * scale).toInt().coerceAtLeast(1)
  val scaled =
    if (scale < 1f) Bitmap.createScaledBitmap(original, targetW, targetH, true) else original

  var quality = if (highQuality) 88 else 64
  val maxBytes = if (highQuality) 250_000 else 130_000
  val baos = ByteArrayOutputStream()
  scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
  while (baos.size() > maxBytes && quality > 34) {
    baos.reset()
    quality -= 10
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
  }
  if (scaled !== original) scaled.recycle()
  if (recycleInput) original.recycle()

  val bytes = baos.toByteArray()
  val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
  val dataUri = "data:image/jpeg;base64,$encoded"
  decodedImageByteCache.put(fastCacheKey(dataUri), bytes)
  return dataUri
}

private fun fastCacheKey(data: String): String {
  return "aura_${data.length}_${data.hashCode()}"
}

@Composable
fun AuraImage(
  imageData: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  filterName: String = "Original",
  contentScale: ContentScale = ContentScale.Crop,
  targetSizePx: Int = 1440,
) {
  val context = LocalContext.current
  val auraImageLoader = remember(context) { getAuraImageLoader(context) }
  val colorFilter =
    remember(filterName) { filterColorMatrix(filterName)?.let { ColorFilter.colorMatrix(it) } }

  if (imageData.startsWith("data:image")) {
    val cacheKey = remember(imageData) { fastCacheKey(imageData) }
    val cachedImmediately = remember(cacheKey) { decodedImageByteCache.get(cacheKey) }
    val bytes by
      produceState(initialValue = cachedImmediately, key1 = cacheKey) {
        if (value == null) {
          value =
            withContext(Dispatchers.Default) {
              try {
                val base64Part = imageData.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                  Base64.decode(base64Part, Base64.DEFAULT).also {
                    decodedImageByteCache.put(cacheKey, it)
                  }
                } else {
                  null
                }
              } catch (_: Exception) {
                null
              }
            }
        }
      }

    if (bytes != null) {
      val request =
        remember(cacheKey, targetSizePx) {
          ImageRequest.Builder(context)
            .data(bytes)
            .memoryCacheKey("${cacheKey}_$targetSizePx")
            .size(targetSizePx)
            .precision(Precision.INEXACT)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .allowHardware(true)
            .crossfade(false)
            .build()
        }
      AsyncImage(
        model = request,
        imageLoader = auraImageLoader,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
        colorFilter = colorFilter,
      )
    } else {
      Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
  } else if (imageData.isNotBlank()) {
    val request =
      remember(imageData, targetSizePx) {
        ImageRequest.Builder(context)
          .data(imageData)
          .memoryCacheKey("${imageData}_$targetSizePx")
          .size(targetSizePx)
          .precision(Precision.INEXACT)
          .bitmapConfig(Bitmap.Config.ARGB_8888)
          .allowHardware(true)
          .crossfade(false)
          .build()
      }
    AsyncImage(
      model = request,
      imageLoader = auraImageLoader,
      contentDescription = contentDescription,
      modifier = modifier,
      contentScale = contentScale,
      filterQuality = FilterQuality.High,
      colorFilter = colorFilter,
    )
  } else {
    Box(
      modifier =
        modifier.background(
          Brush.linearGradient(listOf(AuraCoral.copy(alpha = 0.25f), AuraViolet.copy(alpha = 0.25f)))
        )
    )
  }
}

@Composable
fun AuraAvatar(
  avatarUrl: String,
  username: String,
  size: Dp,
  modifier: Modifier = Modifier,
  hasStory: Boolean = false,
  isStorySeen: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val ringModifier =
    if (hasStory) {
      if (isStorySeen) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
      } else {
        Modifier.border(2.5.dp, AuraStoryGradient, CircleShape)
      }
    } else {
      Modifier
    }

  val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

  Box(
    modifier =
      modifier
        .size(size)
        .then(ringModifier)
        .padding(if (hasStory) 3.5.dp else 0.dp)
        .clip(CircleShape)
        .then(clickMod),
    contentAlignment = Alignment.Center,
  ) {
    if (avatarUrl.isNotBlank()) {
      AuraImage(
        imageData = avatarUrl,
        contentDescription = "$username avatar",
        modifier = Modifier.fillMaxSize().clip(CircleShape),
        targetSizePx = 280,
      )
    } else {
      val initial = username.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(AuraAmber, AuraMagenta, AuraViolet))),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = initial,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = (size.value * 0.4f).sp,
        )
      }
    }
  }
}
