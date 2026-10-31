# Проверка Property View

Проект собран и проверен в рабочей среде 30 октября 2026 года.
Ниже зафиксированы выполненные проверки; внешние PostgreSQL и MySQL в этом прогоне не использовались.

## Среда

- **Java:** OpenJDK 21.0.12.1, Ubuntu, amd64.
- **Maven:** 3.9.12.
- **Компиляция:** `release 21`, major version class-файла `65`.
- **База:** H2 2.3.232, схема создана Liquibase и проверена Hibernate.

## Автоматические тесты

Команда `mvn --batch-mode --no-transfer-progress clean verify` завершилась с `BUILD SUCCESS`.
Отчёт Maven Surefire:

```text
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
```

Проверены обязательные методы, краткий и полный JSON, необязательные поля, все фильтры поиска,
AND-семантика, регистр, SQL wildcard escaping, повторяющиеся amenities, четыре гистограммы,
ошибки 400/404, вложенная валидация и генерация OpenAPI.
Тесты используют настоящий persistence-слой, отдельную H2 и миграции Liquibase.

## Запуск и HTTP

Приложение запущено командой `mvn spring-boot:run`.
Лог подтвердил порт и context path:

```text
Tomcat started on port 8092 (http) with context path '/property-view'
Started PropertyViewApplication
```

Затем выполнены реальные HTTP-запросы к запущенному серверу:

| Проверка | Результат |
|---|---|
| GET списка до создания | `200`, `[]` |
| POST отеля из `examples/create-hotel.json` | `201`, id и корректный `Location` |
| POST 10 amenities | `204` |
| GET полной информации | `200`, 10 amenities, `checkIn=14:00` |
| Поиск `city=minsk` и двух amenities | `200`, один подходящий отель |
| Гистограммы city, brand, country, amenities | `200`, корректные счётчики |
| OpenAPI JSON | `200`, 5 путей и 6 операций |
| Swagger UI | `200`, HTML Swagger UI |
| GET `/hotels` без общего префикса | `404` |

## Границы проверки

Профили и JDBC-драйверы PostgreSQL/MySQL подготовлены, но соединение с этими СУБД не тестировалось.
Нагрузочное тестирование, автоматическая проверка конкурентных запросов и анализ уязвимостей зависимостей в этот прогон не входят.
