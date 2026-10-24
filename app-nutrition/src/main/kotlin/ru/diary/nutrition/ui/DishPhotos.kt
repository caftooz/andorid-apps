package ru.diary.nutrition.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Фотографии встроенных блюд: лежат в `assets/dishes/<id>.webp`, работают без сети.
 * Все снимки — из открытых источников под свободными лицензиями (Creative Commons), авторы в `credits.json`.
 */
object DishPhotos {
    data class Credit(val title: String, val author: String, val license: String, val licenseUrl: String, val source: String)

    @Volatile private var creditsCache: Map<String, Credit>? = null

    fun credits(ctx: Context): Map<String, Credit> = creditsCache ?: runCatching {
        val o = ctx.assets.open("dishes/credits.json").use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        o.mapValues { (_, v) ->
            val m = v.jsonObject
            fun s(k: String) = m[k]?.jsonPrimitive?.content.orEmpty()
            Credit(s("title"), s("author"), s("license"), s("licenseUrl"), s("source"))
        }
    }.getOrDefault(emptyMap()).also { creditsCache = it }

    // Кэш по размеру в байтах: миниатюры декодируются с уменьшением, чтобы список не занимал много памяти.
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(id: String, sample: Int): ImageBitmap? = cache.get("$id@$sample")

    suspend fun load(ctx: Context, id: String, sample: Int): ImageBitmap? = cached(id, sample) ?: withContext(Dispatchers.IO) {
        runCatching {
            ctx.assets.open("dishes/$id.webp").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }.getOrNull()?.asImageBitmap()?.also { cache.put("$id@$sample", it) }
    }
}

/** Фото блюда; пока грузится — мягкая подложка того же размера. `sample` = 1 для крупного снимка, 4 для миниатюры. */
@Composable
fun DishPhoto(id: String, modifier: Modifier, sample: Int = 1) {
    val ctx = LocalContext.current
    var bmp by remember(id, sample) { mutableStateOf(DishPhotos.cached(id, sample)) }
    LaunchedEffect(id, sample) { if (bmp == null) bmp = DishPhotos.load(ctx, id, sample) }
    val b = bmp
    if (b != null) Image(b, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)))
}
