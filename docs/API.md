# REST API Demo 0.1

Базовый адрес: `http://localhost:8080`; Android Emulator: `http://10.0.2.2:8080`. Авторизации нет.

| Метод | Путь | Запрос | Успех |
|---|---|---|---|
| GET | /api/health | — | 200 `{"status":"UP"}` |
| GET | /api/users | — | 200 `[{"id":1,"displayName":"Аня"},{"id":2,"displayName":"Максим"}]` |
| POST | /api/photos | multipart: file (JPEG/PNG), senderId, recipientId | 201 метаданные |
| GET | /api/photos/latest | userId — ID получателя | 200 метаданные |
| GET | /api/photos/{id}/image | id — ID фото | 200 image/jpeg, бинарное тело |

Пример метаданных:
```json
{"id":12,"senderId":1,"recipientId":2,"overlayText":"Я В ШОКЕ","filter":"BLACK_AND_WHITE","imageUrl":"/api/photos/12/image","createdAt":"2026-10-28T12:30:00Z"}
```

filter: ORIGINAL либо BLACK_AND_WHITE, равновероятно. Время UTC. URL относительный — добавляйте базовый адрес сервера. Повторная загрузка URL возвращает сохранённый JPEG.

Ошибки JSON: `{"message":"Пользователь не найден: 999"}`. 400 — отсутствующий/неверный параметр, неподдерживаемый или повреждённый файл, размер изображения вне 32×32–20 мегапикселей. 404 — пользователь, фотография или файл отсутствует, либо получателю ещё ничего не прислали. 413 — превышен лимит 10 МБ файла/11 МБ всего запроса. 500 — ошибка хранения/сервера.

## Android и виджет

CameraX получает фото; отправьте multipart через Retrofit/OkHttp: part `file` с именем файла и типом image/jpeg или image/png, текстовые части `senderId` и `recipientId`. Не задавайте multipart boundary вручную. Пользовательский текст не передаётся. Перед отправкой нормализуйте EXIF-ориентацию; сервер сохраняет геометрию пикселей.

Виджет запрашивает latest с ID владельца, затем скачивает baseUrl + imageUrl и показывает Bitmap. На 404 покажите пустое состояние. Обновляйте при ручном обновлении или периодически средствами Android; push отсутствует.

В основном манифесте добавьте `<uses-permission android:name="android.permission.INTERNET"/>`. Только в `android/app/src/debug/AndroidManifest.xml` добавьте:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="true" />
</manifest>
```
Это разрешает HTTP только debug-варианту. Не включайте разрешение в release-манифест.

## curl (PowerShell)
```powershell
curl.exe http://localhost:8080/api/users
curl.exe -F "file=@photo.jpg" -F "senderId=1" -F "recipientId=2" http://localhost:8080/api/photos
curl.exe "http://localhost:8080/api/photos/latest?userId=2"
curl.exe http://localhost:8080/api/photos/12/image -o meme.jpg
```
Замените 12 на ID из ответа. На Linux используйте curl вместо curl.exe.

Новые фото обрезаются по центру до квадрата 1:1 без растяжения (сторона равна меньшей стороне исходника). Текст равновероятно располагается сверху, в центре или снизу; позиция фиксируется в готовом JPEG. Старые фото не изменяются.

Настройка подключения: [сервер](../backend/README.md). Использование приложения и виджета: [руководство пользователя](../README.md). Сборка: [Android](../android/README.md).
