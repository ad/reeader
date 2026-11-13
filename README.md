# Reeder for FreshRSS

Android-клиент FreshRSS на Java. Работает через Google Reader API (`/api/greader.php`).

## Сборка

Нужны JDK 17+, Android SDK с platform 36 и build-tools.

```bash
./build.sh            # build/reeder.apk
./build.sh install    # собрать, поставить через adb и запустить
```

Без `KEYSTORE_*` APK подписывается `~/.android/debug.keystore`; если его нет, ключ создаётся.

## Релизы и автообновление

Каждый push в `main` собирает APK и публикует релиз `v0.1.<номер сборки>`. Приложение при запуске проверяет последний релиз и предлагает обновиться.

Ключ подписи создаётся один раз:

```bash
keytool -genkeypair -v -keystore release.jks -alias reeder -keyalg RSA -keysize 2048 -validity 10000
gh secret set KEYSTORE_BASE64 --body "$(base64 -i release.jks)"
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body reeder
gh secret set KEY_PASSWORD
```

Сохрани `release.jks` и пароли: без них новые версии не встанут поверх установленной. Push в `main` без секретов падает намеренно. Pull request получает APK как артефакт.

Reader view использует [Mozilla Readability](https://github.com/mozilla/readability) (Apache 2.0), файл `assets/Readability.js`.
