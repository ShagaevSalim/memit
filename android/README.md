# Сборка Android-приложения

Приложение и виджет находятся в одном модуле `app`, пакет `dev.memit.app`. Инструкция по использованию: [основной README](../README.md).

## Требования

- Android Studio или Android SDK с платформой 35 и Build Tools 35.0.0.
- JDK 21 для сборки.
- Интернет для первого скачивания Gradle и зависимостей.
- Телефон или эмулятор с Android 8.0+ (API 26).

Проект использует Gradle Wrapper 8.11.1 и Android Gradle Plugin 8.9.2. Путь к SDK задаётся переменной `ANDROID_HOME` или параметром `sdk.dir` в локальном `local.properties`.

## Сборка в Android Studio

1. Откройте каталог `android` как проект.
2. Выберите JDK 21 в настройках Gradle и дождитесь синхронизации.
3. Выберите debug-вариант сборки и запустите приложение на устройстве либо соберите APK.

## Сборка из терминала

Из каталога `android` при настроенных `JAVA_HOME` и Android SDK:

```powershell
.\gradlew.bat assembleDebug
```

Linux/macOS:

```sh
sh ./gradlew assembleDebug
```

Установочный файл: `app/build/outputs/apk/debug/app-debug.apk`.

Установка по USB с помощью Android SDK Platform Tools:

```powershell
adb devices
adb -d install -r app\build\outputs\apk\debug\app-debug.apk
```

На телефоне должны быть включены отладка по USB и разрешение на подключение компьютера. Статус в списке устройств — `device`.

## Debug и release

Debug-сборка допускает HTTP для локального сервера. В release HTTP запрещён: требуется HTTPS.

```powershell
.\gradlew.bat assembleRelease
```

Release APK создаётся в `app/build/outputs/apk/release/` без подписи. Перед распространением release-сборку необходимо подписать собственным ключом.

## Структура приложения

| Компонент | Назначение |
|---|---|
| `MainActivity` | CameraX, разрешения, предпросмотр и настройки |
| `CaptureViewModel` | Подготовка и отправка снимка, состояния и защита от повторного нажатия |
| `PhotoNormalizer` | EXIF-ориентация и JPEG без обрезки, текста и фильтра |
| `ApiClient` | HTTP-запросы и multipart-отправка |
| `AppSettings` | Общие сохранённые ID владельца и адрес сервера |
| `MemitWidget`, `WidgetRefreshService`, `WidgetRefreshTask` | Виджет и одноразовая фоновая загрузка через JobScheduler |
| `WidgetCache` | Дисковый кэш отдельно для каждого сервера и владельца |

Сеть и подготовка изображений выполняются вне главного потока. Виджет показывает весь квадрат через `fitCenter`; в RemoteViews передаётся уменьшенный Bitmap со стороной не более 512 пикселей. Для получения нового мема используется ручное обновление.

Команды проверок и сценарии на устройствах: [тестирование](../docs/ANDROID_VERIFICATION.md).

Ручное обновление запускается через JobScheduler без изменения состояния компонентов пакета. После установки исправления зависший старый экземпляр виджета нужно заменить новым через кнопку «Добавить виджет с мемами». Для эмулятора используется `http://10.0.2.2:8080/`.
