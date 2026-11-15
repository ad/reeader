# Reeader

Android-клиент RSS на Java. Источники: Feedbin, Feedly, NewsBlur, The Old Reader, Inoreader, BazQux Reader, FreshRSS, Miniflux и любой сервер с Google Reader API.

Каждый источник описан своим классом-наследником `Service`: название, подсказки, вход и `Backend` для синхронизации. Сервисы на Google Reader API наследуют `GReaderService`. Новый источник: класс в `src/`, строка в `Service.ALL`.

Inoreader пускает в API только с ключами приложения: `INOREADER_APP_ID` и `INOREADER_APP_KEY` из окружения сборки (в CI это секреты). Feedly входит по API-токену, Feedly выдаёт его только на тарифе Enterprise.

`test/smoke.sh` проверяет живые API тестовыми аккаунтами из `.env` (он в `.gitignore`).

## Сборка

Нужны JDK 17+, Android SDK с platform 36 и build-tools.

```bash
./build.sh            # build/reeader.apk
./build.sh install    # собрать, поставить через adb и запустить
```

Без `KEYSTORE_*` APK подписывается `~/.android/debug.keystore`; если его нет, ключ создаётся.

## Релизы и автообновление

Каждый push в `main` собирает APK и публикует релиз `v0.1.<номер сборки>`. Приложение при запуске проверяет последний релиз и предлагает обновиться.

Ключ подписи создаётся один раз:

```bash
keytool -genkeypair -v -keystore release.jks -alias reeader -keyalg RSA -keysize 2048 -validity 10000
gh secret set KEYSTORE_BASE64 --body "$(base64 -i release.jks)"
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body reeader
gh secret set KEY_PASSWORD
```

Сохрани `release.jks` и пароли: без них новые версии не встанут поверх установленной. Push в `main` без секретов падает намеренно. Pull request получает APK как артефакт.

Reader view использует [Mozilla Readability](https://github.com/mozilla/readability) (Apache 2.0), файл `assets/Readability.js`.
