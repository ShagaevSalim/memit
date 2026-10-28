# Memit Demo 0.1

Backend: Java 21, Spring Boot 3.5, Maven Wrapper, Web, JPA, файловая H2, Java2D. Android и дизайн реализуются отдельно.

## Запуск (PowerShell)

Установите JDK 21 и задайте JAVA_HOME на каталог JDK. Первый запуск Wrapper требует интернет.

```powershell
cd backend
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Linux/macOS: `sh ./mvnw test` и `sh ./mvnw spring-boot:run` из backend.
Всегда запускайте из backend: `data/memit.mv.db` хранит БД, `uploads/` — JPEG. Оба каталога сохраняются после перезапуска; резервируйте вместе. Пользователи 1 (Аня), 2 (Максим) создаются без дубликатов. Последнее фото определяется по времени UTC и ID.

Поместите собственный лицензированный Impact в `backend/fonts/Impact.ttf`. Без него используется жирный SansSerif, в логах предупреждение. Шрифт не включён в репозиторий.

Проверка:
```powershell
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/users
curl.exe -F "file=@photo.jpg" -F "senderId=1" -F "recipientId=2" http://localhost:8080/api/photos
curl.exe "http://localhost:8080/api/photos/latest?userId=2"
curl.exe http://localhost:8080/api/photos/1/image -o received.jpg
```

Эмулятор Android: `http://10.0.2.2:8080`. Физический телефон: LAN IP компьютера, общая Wi-Fi сеть и доступ к порту 8080.

`ApiController` принимает запросы и отдаёт DTO; `ImageService` проверяет формат, выбирает текст/фильтр и рисует JPEG; `Photo` и `DemoUser` описывают таблицы; репозитории выполняют запросы JPA; `ApiErrors` формирует ошибки JSON. Текст и фильтр выбираются однократно, скачивание читает сохранённый файл. Пропорции сохраняются. EXIF-поворот не применяется: клиент должен отправлять пиксели с корректной ориентацией.

Отсутствуют авторизация, регистрация, push, сложные друзья, PostgreSQL, Redis, S3, Docker, платежи и награды. Это локальная демонстрация; ID передаются напрямую.

Контракт и Android: [API](docs/API.md).

Новые фото обрезаются по центру до квадрата 1:1 без растяжения (сторона равна меньшей стороне исходника). Текст равновероятно располагается сверху, в центре или снизу; позиция фиксируется в готовом JPEG. Старые фото не изменяются.
