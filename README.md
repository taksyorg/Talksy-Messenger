# Talksy Android

Android-клиент мессенджера Talksy (Java, minSdk 24). Исходный код открыт.

## Сборка

Нужны JDK 17 и Android SDK. Откройте папку в Android Studio или выполните `./gradlew assembleDebug`.

Настройки сборки задаются в `local.properties` (файл не попадает в git) или переменными окружения:

| Параметр | Назначение |
|---|---|
| `TALKSY_SERVER_URL` | адрес вашего сервера |
| `TALKSY_TURN_HOST`, `TALKSY_TURN_USER`, `TALKSY_TURN_PASS` | TURN-сервер для звонков |
| `MAPS_API_KEY` | ключ Google Maps (без него карта серая) |

Push-уведомления (FCM) необязательны: положите свой `google-services.json` в `app/` (образец: `app/google-services.json.example`). Без него приложение собирается и работает без push.

## Релизы

Релизная сборка подписывается ключом из переменных окружения `TALKSY_KEYSTORE_FILE`, `TALKSY_KEYSTORE_PASSWORD`, `TALKSY_KEY_ALIAS`, `TALKSY_KEY_PASSWORD`. Релизный APK сжимается и обфусцируется R8: имена классов и методов в нём заменены на короткие.

APK собирается автоматически и публикуется в Releases при создании тега вида `v1.1` (см. `.github/workflows/release.yml`).

## Лицензия

MIT, см. `LICENSE`.
