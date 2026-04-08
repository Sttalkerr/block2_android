# Block2 Android

Практические задания 1 и 2 по модулю 5 разработки мобильных приложений.

В проекте реализованы:

- Дневник с хранением записей во внутреннем хранилище приложения (`context.filesDir`)
- Личная фотогалерея с сохранением фото в `getExternalFilesDir(Environment.DIRECTORY_PICTURES)`
- Экспорт фото в общую галерею через `MediaStore`

## Сборка

```bash
./gradlew assembleDebug
```

## Проверка тестов

```bash
./gradlew testDebugUnitTest
```

## Отчет

Подробный текст для отчета находится в файле `REPORT_module5_practice1_2.md`.
