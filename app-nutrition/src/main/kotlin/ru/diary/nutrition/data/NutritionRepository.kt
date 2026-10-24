package ru.diary.nutrition.data

import ru.diary.nutrition.domain.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.diary.nutrition.domain.CustomDish
import ru.diary.nutrition.domain.Entry
import ru.diary.nutrition.domain.NutritionData

/**
 * Хранилище дневника рациона. Изменения принимаются как чистая функция над состоянием; в базу пишется только разница.
 * `onChanged` вызывается после записи, чтобы сообщить второму приложению через `notifyChange`.
 */
class NutritionRepository(db: NutritionDatabase, private val onChanged: () -> Unit = {}) {
    private val dao = db.dao()
    private val lock = Mutex()

    val data: Flow<NutritionData> = combine(dao.entries(), dao.custom(), dao.products()) { e, c, p ->
        NutritionData(
            e.map { Entry(it.id, it.date, it.time, it.dish, it.name, it.portion, it.kcal, it.protein, it.plan, it.fat, it.carbs) },
            c.map { CustomDish(it.id, it.name, it.cat, it.kcal, it.p, it.notes) },
            p.map { Product(it.code, it.name, it.brand, it.kcal, it.p, it.f, it.c, it.packG, it.source, it.updated) },
        )
    }

    suspend fun once(): NutritionData = data.first()

    suspend fun update(transform: (NutritionData) -> NutritionData) = lock.withLock {
        val old = data.first()
        val new = transform(old)
        if (new == old) return@withLock
        if (new.log != old.log) {
            val ids = new.log.mapTo(HashSet()) { it.id }
            old.log.filter { it.id !in ids }.forEach { dao.deleteEntry(it.id) }
            val oldById = old.log.associateBy { it.id }
            dao.upsertEntries(new.log.filter { oldById[it.id] != it }.map(::entity))
        }
        if (new.custom != old.custom) { dao.clearCustom(); dao.upsertCustom(customEntities(new.custom)) }
        if (new.products != old.products) {
            val codes = new.products.mapTo(HashSet()) { it.code }
            old.products.filter { it.code !in codes }.forEach { dao.deleteProduct(it.code) }
            val oldByCode = old.products.associateBy { it.code }
            dao.upsertProducts(new.products.filter { oldByCode[it.code] != it }.map(::productEntity))
        }
        onChanged()
    }

    suspend fun replaceAll(d: NutritionData) = lock.withLock {
        dao.replaceAll(d.log.map(::entity), customEntities(d.custom), d.products.map(::productEntity))
        onChanged()
    }

    private fun entity(e: Entry) = EntryEntity(e.id, e.date, e.time, e.dish, e.name, e.portion, e.kcal, e.protein, e.plan, e.fat, e.carbs)
    private fun productEntity(p: Product) = ProductEntity(p.code, p.name, p.brand, p.kcal, p.p, p.f, p.c, p.packG, p.source, p.updated)
    private fun customEntities(l: List<CustomDish>) = l.mapIndexed { i, c -> CustomDishEntity(c.id, i, c.name, c.cat, c.kcal, c.p, c.notes) }
}
