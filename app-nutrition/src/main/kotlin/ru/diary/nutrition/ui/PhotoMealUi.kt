package ru.diary.nutrition.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.diary.core.date.Dates
import ru.diary.core.theme.DiaryTheme
import ru.diary.core.ui.Callout
import ru.diary.core.ui.DangerButton
import ru.diary.core.ui.DiaryIcons
import ru.diary.core.ui.DiarySheet
import ru.diary.core.ui.LabeledField
import ru.diary.core.ui.Muted
import ru.diary.core.ui.PrimaryButton
import ru.diary.core.ui.QuietButton
import ru.diary.core.ui.SecondaryButton
import ru.diary.core.ui.SegmentedControl
import ru.diary.core.ui.ToggleRow
import ru.diary.core.ui.pressScale
import ru.diary.nutrition.data.GeminiRecognizer
import ru.diary.nutrition.domain.PhotoItem
import ru.diary.nutrition.domain.PhotoMeal
import ru.diary.nutrition.domain.Weigh
import ru.diary.core.util.Js
import java.io.ByteArrayOutputStream
import java.io.File

/** Баннер «Блюдо по фото» над плитками вкладки «Блюда». */
@Composable
fun PhotoBanner(onClick: () -> Unit) {
    val x = DiaryTheme.extras
    val src = remember { MutableInteractionSource() }
    val glow = x.button.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick, interactionSource = src, shape = shape, color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().pressScale(src, 0.97f).shadow(10.dp, shape, ambientColor = glow, spotColor = glow),
    ) {
        Row(
            Modifier.background(x.heroBrush).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).background(x.onHero.copy(alpha = 0.2f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(DiaryIcons.get("camera"), null, tint = x.onHero, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Блюдо по фото", style = MaterialTheme.typography.titleMedium, color = x.onHero)
                Text("ИИ определит состав и вес, КБЖУ посчитается само", style = MaterialTheme.typography.bodySmall, color = x.onHero.copy(alpha = 0.9f))
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = x.onHero)
        }
    }
}

/**
 * Панель «Блюдо по фото»: настройка ключа (если его нет), съёмка или выбор фото, распознавание и проверка граммов.
 * Записывается только то, что пользователь подтвердил.
 */
@Composable
fun PhotoMealSheet(vm: NutritionViewModel, date: String, today: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasPhotoKey.collectAsStateWithLifecycle()
    val state by vm.photo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    fun handle(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { loadJpeg(ctx, uri) }.getOrNull() }
            if (r == null) { vm.photoError("Не удалось открыть фото."); return@launch }
            preview = r.second
            vm.recognizePhoto(r.first)
        }
    }

    // Адрес файла для камеры получаем только при съёмке: любая ошибка превращается в сообщение, а не в падение экрана.
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) handle(photoUri) }
    fun launchCamera() {
        val uri = runCatching { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile(ctx)) }.getOrNull()
        if (uri == null) { vm.photoError("Не удалось подготовить камеру. Выберите фото из галереи."); return }
        photoUri = uri
        runCatching { camera.launch(uri) }.onFailure { vm.photoError("На телефоне нет приложения камеры. Выберите фото из галереи.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else vm.photoError("Без доступа к камере можно выбрать фото из галереи.")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { handle(it) }
    fun shoot() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    DiarySheet({ vm.resetPhoto(); onDismiss() }, title = "Блюдо по фото", subtitle = "Распознавание через Google Gemini", icon = "camera") { close ->
        when {
            !hasKey || !s.photoAi -> PhotoAiSetup(vm, compact = true)
            else -> when (val st = state) {
                PhotoState.Idle -> {
                    Muted("Сфотографируйте тарелку сверху целиком — так проще оценить вес. После распознавания можно поправить граммы.")
                    PrimaryButton("Сфотографировать", ::shoot, Modifier.fillMaxWidth())
                    SecondaryButton("Выбрать из галереи", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth())
                }
                PhotoState.Working -> {
                    preview?.let { PhotoPreview(it) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text("Распознаю блюдо…", style = MaterialTheme.typography.titleSmall)
                    }
                }
                is PhotoState.Error -> {
                    preview?.let { PhotoPreview(it) }
                    Callout { Text(st.message, style = MaterialTheme.typography.bodyMedium) }
                    PrimaryButton("Попробовать ещё раз", { vm.resetPhoto() }, Modifier.fillMaxWidth())
                }
                is PhotoState.Draft -> PhotoReview(st.meal, preview, date, today, onRetake = { vm.resetPhoto() }) { meal ->
                    vm.savePhotoMeal(meal, date); close()
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(img: ImageBitmap) {
    Image(img, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
}

/** Проверка распознанного: название, продукты с граммами (можно править и убирать), итог и «Записать». */
@Composable
private fun PhotoReview(meal: PhotoMeal, preview: ImageBitmap?, date: String, today: String, onRetake: () -> Unit, onSave: (PhotoMeal) -> Unit) {
    var name by rememberSaveable(meal) { mutableStateOf(meal.name) }
    val grams = remember(meal) { mutableStateListOf(*meal.items.map { it.grams.toString() }.toTypedArray()) }
    val removed = remember(meal) { mutableStateListOf<Int>() }
    val current = PhotoMeal(
        name, meal.items.indices.filter { it !in removed }.mapNotNull { i -> grams[i].toIntOrNull()?.takeIf { it in 1..2000 }?.let { PhotoItem(meal.items[i].food, it) } }, meal.note,
    )
    preview?.let { PhotoPreview(it) }
    LabeledField("Название", name, { name = it }, maxLength = 120)
    if (meal.note.isNotEmpty()) Muted("ИИ: ${meal.note}")
    Text("Состав — проверьте вес", style = MaterialTheme.typography.titleSmall)
    meal.items.forEachIndexed { i, it ->
        if (i in removed) return@forEachIndexed
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(it.food.name, style = MaterialTheme.typography.bodyMedium)
                val g = grams[i].toIntOrNull() ?: 0
                Muted("${Js.round(it.food.per100.kcal * g / 100).toInt()} ккал")
            }
            LabeledField("г", grams[i], { v -> grams[i] = v.filter(Char::isDigit) }, Modifier.width(96.dp), numeric = true, maxLength = 4)
            QuietButton("✕", { removed.add(i) }, Modifier.semantics { contentDescription = "Убрать: ${it.food.name}" })
        }
    }
    val n = current.total
    Text("Итого ${current.grams} г", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    MacroRow(Js.round(n.kcal), Weigh.round1(n.p), Weigh.round1(n.f), Weigh.round1(n.c))
    Muted("Клетчатка ${fmt1(n.fiber)} г. Вес по фото — оценка: при необходимости поправьте граммы.")
    PrimaryButton("Записать · " + Dates.label(date, today).lowercase(), { onSave(current) }, Modifier.fillMaxWidth(), enabled = current.items.isNotEmpty())
    QuietButton("Сфотографировать заново", onRetake)
}

/** Настройка распознавания: что это, условия бесплатного тарифа Google, ключ и модель. */
@Composable
fun PhotoAiSetup(vm: NutritionViewModel, compact: Boolean = false) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasPhotoKey.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var key by rememberSaveable { mutableStateOf("") }
    if (compact) Muted("Чтобы распознавать фото, нужен бесплатный ключ Gemini API от Google. Это займёт минуту.")
    Callout {
        Text("Перед включением", style = MaterialTheme.typography.titleSmall)
        Text("• Ключ бесплатный: Google AI Studio → «Get API key». У бесплатного тарифа есть дневной лимит запросов.", style = MaterialTheme.typography.bodySmall)
        Text("• На бесплатном тарифе Google использует присланные фото для улучшения своих продуктов, их могут просматривать люди-проверяющие. Не фотографируйте ничего личного.", style = MaterialTheme.typography.bodySmall)
        Text("• Gemini API доступен не во всех странах (например, недоступен в России) и только с 18 лет.", style = MaterialTheme.typography.bodySmall)
        Text("• Ключ хранится только на телефоне, в зашифрованном виде. Фото уходит в Google только когда вы его делаете здесь.", style = MaterialTheme.typography.bodySmall)
    }
    QuietButton("Открыть Google AI Studio", { uri.openUri("https://aistudio.google.com/apikey") })
    if (hasKey) {
        Text("Ключ сохранён", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        ToggleRow("Распознавать фото блюд", s.photoAi, { vm.setPhotoAi(it) })
        Text("Модель", style = MaterialTheme.typography.titleSmall)
        SegmentedControl(GeminiRecognizer.MODELS, s.photoModel, { vm.setPhotoModel(it) })
        Muted("3.8 Flash точнее, 3.5 Flash-Lite быстрее и с более щедрым бесплатным лимитом.")
        DangerButton("Удалить ключ", { vm.removePhotoKey() })
    } else {
        LabeledField("Ключ Gemini API", key, { key = it.trim() }, password = true, placeholder = "AIza…", maxLength = 200)
        PrimaryButton("Сохранить ключ", { vm.savePhotoKey(key); key = "" }, Modifier.fillMaxWidth(), enabled = key.length >= 20)
    }
}

private fun photoFile(ctx: Context): File = File(ctx.cacheDir, "photos").apply { mkdirs() }.let { File(it, "meal.jpg") }

/**
 * Фото → JPEG не больше 1024 px по длинной стороне (для распознавания этого достаточно, а запрос остаётся лёгким).
 * На Android 9+ поворот по EXIF учитывается автоматически.
 */
private fun loadJpeg(ctx: Context, uri: Uri, max: Int = 1024): Pair<ByteArray, ImageBitmap> {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            val w = info.size.width; val h = info.size.height
            val k = maxOf(w, h).toFloat() / max
            if (k > 1f) dec.setTargetSize((w / k).toInt(), (h / k).toInt())
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("нет изображения")
        val k = maxOf(raw.width, raw.height).toFloat() / max
        if (k > 1f) Bitmap.createScaledBitmap(raw, (raw.width / k).toInt(), (raw.height / k).toInt(), true) else raw
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray() to bmp.asImageBitmap()
}
