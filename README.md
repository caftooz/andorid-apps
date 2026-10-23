# Дневники: «Тренировки» и «Рацион» (Android)

Два самостоятельных нативных приложения на Kotlin и Jetpack Compose. Перенос двух веб-прототипов
(`trenirovki.html`, `raciona.html` из <https://github.com/caftooz/diary>). Всё работает офлайн, без сети, аналитики и сторонних SDK.
Если установлены оба приложения, они показывают друг другу связанные данные (раздел «Обмен данными»).

## Структура репозитория

| Путь | Что внутри |
| --- | --- |
| `core/` | Общий модуль: тема (Material You + статичная палитра), шрифты Onest и Unbounded, графики на Canvas, общие компоненты, утилиты дат и JS-совместимых чисел, **контракт обмена данными** и клиент (`ru.diary.core.contract`) |
| `app-training/` | «Тренировки», пакет `ru.diary.training`: `domain` (правила, импорт/экспорт), `data` (Room, DataStore), `ui` (Compose), `rest` (таймер отдыха) |
| `app-nutrition/` | «Рацион», пакет `ru.diary.nutrition`, та же схема слоёв |
| `tools/golden/` | Node-скрипты, которые берут данные и эталонные результаты прямо из HTML-прототипов |
| `gradle/libs.versions.toml` | Каталог версий |

Архитектура: одна Activity, Navigation Compose, MVVM + Repository, `StateFlow`, корутины. DI ручной (`AppContainer`).
Хранение: Room (у каждого приложения своя БД, схемы экспортируются в `schemas/`) и DataStore для настроек.

### Данные из прототипа

Каталог упражнений, программы A/B/C, встроенные шаблоны планов и каталог блюд **не переписывались вручную**.
`tools/golden/gen.mjs` и `gen-nutrition.mjs` выгружают их из HTML в `catalog.json` (ресурсы приложений):

```
node tools/golden/gen.mjs <trenirovki.html> app-training
node tools/golden/gen-nutrition.mjs <raciona.html> app-nutrition
```

## Сборка

Нужны JDK 17+ (подойдёт JBR из Android Studio) и Android SDK с платформой 36.

```
# Windows, Git Bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
echo 'sdk.dir=C\:/Users/<вы>/AppData/Local/Android/Sdk' > local.properties   # файл в git не попадает

./gradlew assembleDebug        # оба приложения, debug
./gradlew lint                 # проверка lint
./gradlew assembleRelease      # release с R8 и сжатием ресурсов
```

APK: `app-training/build/outputs/apk/<тип>/` и `app-nutrition/build/outputs/apk/<тип>/`.
Идентификаторы: `ru.diary.training` и `ru.diary.nutrition`.

### Подпись release

Оба приложения **обязательно подписываются одним ключом**: от этого зависит обмен данными (signature-permission).
Ключ в репозиторий не коммитится (`*.jks`, `*.keystore`, `local.properties` в `.gitignore`).

```
keytool -genkeypair -v -keystore diary.jks -alias diary -keyalg RSA -keysize 4096 -validity 10000
```

Затем в `local.properties` (или переменными окружения с теми же именами):

```
DIARY_KEYSTORE=C\:/путь/к/diary.jks
DIARY_KEYSTORE_PASSWORD=...
DIARY_KEY_ALIAS=diary
DIARY_KEY_PASSWORD=...
```

Без этих значений release-сборка получается неподписанной (`*-release-unsigned.apk`).
Debug-сборки обоих приложений подписаны общим debug-ключом, поэтому обмен данными работает и в них.

## Обмен данными между приложениями

Контракт описан в `core/.../contract/DiaryContract.kt` (версия контракта `1`).

* Каждое приложение держит `ContentProvider` (`exported=true`) с `readPermission` и custom-permission
  `protectionLevel="signature"`: читать могут только приложения с тем же ключом подписи. Запись снаружи невозможна
  (`insert`/`update`/`delete` бросают исключение). Дополнительно разрешение проверяется в коде провайдера.
* Адреса: `content://ru.diary.training.provider/{meta|days}` и `content://ru.diary.nutrition.provider/{meta|days}`;
  `days?from=yyyy-MM-dd&to=yyyy-MM-dd` отдаёт строку на каждую дату диапазона.
* «Тренировки» отдают по дням: ккал на занятиях (оценка пользователя), минуты, тип дня по плану, название тренировки;
  в `meta`: последний вес и флаг нормы белка.
* «Рацион» отдаёт по дням: ккал, белки, жиры, углеводы, число приёмов; в `meta`: цели по ккал и белку.
* Изменения: после записи вызывается `notifyChange`, клиент слушает `ContentObserver` плюс события установки и удаления пакета,
  поэтому данные обновляются без перезапуска, в том числе когда второе приложение ставят или удаляют во время работы.
* На Android 11+ второе приложение объявлено в `<queries>`. Любая ошибка (нет приложения, другая подпись, сбой провайдера)
  превращается в «интеграции нет»: блоки просто скрываются.
* Расход ккал — **оценка пользователя на занятиях**, а не общий расход; базовый обмен не считается, баланс не является медицинским выводом.

## Совместимость с веб-версией

JSON-экспорт и импорт совместимы с прототипами (`dnevnik-trenirovok` `version: 3`, `dnevnik-raciona` `version: 1`).
Импорт очищает значения как `clean*`-функции прототипа, повреждённые записи пропускает и показывает их число;
режимы «заменить» и «объединить» повторяют `applyImport`. Старые типы занятий `handstand`, `stretch`, `pullday` превращаются в `track`.

## Отличия от веб-версии

* В реальном `raciona.html` нет меню на 2 недели, списка покупок, заморозки, обеда в колледже, графика вт–сб и режима 80/20;
  перенесено то, что есть в коде (20 блюд с рецептами и КБЖУ, дневник, «вне плана», неделя, свои блюда).
* В журнале рациона нет жиров и углеводов: для обмена данными они считаются по каталогу блюд, для записей вне плана и своих блюд равны 0.
* У встроенных домашних занятий всегда заполнена иконка, а у «подходов за день» длительность по умолчанию 20 (в веб-версии это происходит после первого импорта).
* Облачное хранилище веб-версии (`window.claude`) не переносилось: на телефоне данные только локальные.
