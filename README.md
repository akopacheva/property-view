# Property View

REST API каталога отелей на Java 21, Spring Boot, Spring Data JPA, Liquibase и H2.
Проект запускается одной Maven-командой, использует порт `8092` и общий префикс `/property-view`.

## Запуск

Нужны **JDK 21** и Maven 3.6.3 или новее. Отдельный сервер базы данных и Docker для запуска и тестов не нужны.

```bash
java -version
mvn -version
mvn spring-boot:run
```

Обе проверки версии должны показывать Java 21; при необходимости настройте `JAVA_HOME`.
В `pom.xml` установлены `java.version=21`, `maven.compiler.release=21` и Maven Enforcer, запрещающий сборку на другой основной версии JDK.

Базовый адрес: `http://localhost:8092/property-view`.
По умолчанию используется пустая H2 in-memory: данные не сохраняются после завершения процесса, тестовые отели автоматически не создаются.

## Методы API

| Метод | Путь относительно `/property-view` | Успешный ответ |
|---|---|---|
| GET | `/hotels` | `200`, массив кратких описаний |
| GET | `/hotels/{id}` | `200`, полная информация |
| GET | `/search` | `200`, массив кратких описаний |
| POST | `/hotels` | `201`, краткое описание и заголовок `Location` |
| POST | `/hotels/{id}/amenities` | `204`, без тела |
| GET | `/histogram/{param}` | `200`, объект «значение: число отелей» |

Краткое описание содержит `id`, `name`, `description`, `address`, `phone`.
Адрес форматируется как `9 Pobediteley Avenue, Minsk, 220004, Belarus`; полная информация возвращает вложенные `address`, `contacts`, `arrivalTime` и массив `amenities`.

### Создание и просмотр

Команды ниже выполняются из корня проекта в Bash. В Windows PowerShell вместо `curl` используйте `curl.exe`.

```bash
curl -i -X POST http://localhost:8092/property-view/hotels \
  -H "Content-Type: application/json" \
  --data-binary @examples/create-hotel.json

curl http://localhost:8092/property-view/hotels
curl http://localhost:8092/property-view/hotels/1

curl -i -X POST http://localhost:8092/property-view/hotels/1/amenities \
  -H "Content-Type: application/json" \
  --data-binary @examples/amenities.json
```

Замените `1` на `id` из ответа на создание. Содержимое примера отеля воспроизводит данные задания, а не является проверенным справочником.

### Поиск

Поддерживаются `name`, `brand`, `city`, `country`, `amenities`; любой фильтр необязателен.
Поскольку задание не определяет все детали поиска, в реализации приняты следующие правила:

- **Комбинирование:** все переданные фильтры объединяются через AND.
- **Регистр:** поиск регистронезависимый, пробелы по краям значений удаляются.
- **Название:** `name` ищет подстроку; `%` и `_` трактуются буквально, не как SQL-шаблоны.
- **Остальные поля:** `brand`, `city`, `country` и названия amenities сравниваются целиком.
- **Несколько amenities:** отель должен содержать каждое значение. Поддерживаются повторяющиеся параметры и список через запятую.
- **Пустые значения:** пустые фильтры игнорируются; поиск без параметров возвращает все отели.
- **Порядок:** списки отелей сортируются по возрастанию `id`; дубли результатов не возникают.

```bash
curl "http://localhost:8092/property-view/search?city=minsk"

curl -G http://localhost:8092/property-view/search \
  --data-urlencode "brand=Hilton" \
  --data-urlencode "city=minsk" \
  --data-urlencode "amenities=Free WiFi" \
  --data-urlencode "amenities=Free parking"
```

### Amenities и гистограммы

Добавление amenities не заменяет существующий список, обрезает пробелы по краям и игнорирует дубли внутри одного отеля без учёта регистра.
Пустой список допустим; пустые строки, `null`-элементы, строки длиннее 255 символов и более 200 элементов в запросе отклоняются целиком.

Гистограмма принимает только `brand`, `city`, `country`, `amenities`.
Группировка выполняется в базе по сохранённым значениям: `Minsk` и `minsk`, записанные в разных отелях, являются разными ключами гистограммы, хотя поиск находит оба.

```bash
curl http://localhost:8092/property-view/histogram/city
curl http://localhost:8092/property-view/histogram/amenities
```

Каждый отель учитывается не более одного раза для конкретного amenity.
Пустой каталог даёт `[]` для списков и `{}` для гистограмм; отели без amenities не добавляют ключи в гистограмму amenities.

## Валидация и ошибки

Все поля запроса создания обязательны, кроме `description` и `arrivalTime.checkOut`.
Оба необязательных поля могут отсутствовать или иметь значение `null`; они возвращаются в JSON как `null`, если не заданы.

- **Время:** строго `HH:mm`, от `00:00` до `23:59`; пустой `checkOut` не равен отсутствующему.
- **Адрес:** `houseNumber` является положительным целым числом, индекс строковый и сохраняет ведущие нули.
- **Контакты:** email проверяется Bean Validation; телефон непустой, без ограничения национального формата.
- **400:** ошибка валидации, невалидный JSON, неизвестное поле JSON, некорректный id или параметр гистограммы.
- **404:** отель не найден, в том числе при добавлении amenities.
- **405 / 415:** неподдерживаемый HTTP-метод / тип содержимого.

Ошибки возвращаются в формате Problem Details с `status`, `title`, `detail`, `instance`.
При ошибках DTO дополнительно передаётся объект `errors` с путями полей:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/property-view/hotels",
  "errors": {
    "contacts.email": "must be a well-formed email address"
  }
}
```

## Swagger

Swagger UI доступен по адресу `http://localhost:8092/property-view/swagger-ui.html`.
OpenAPI JSON доступен по адресу `http://localhost:8092/property-view/v3/api-docs`; спецификация генерируется из контроллера и DTO.

## Архитектура

```text
controller   HTTP-контракт, статусы, валидация входа
dto          отдельные request/response records
service      транзакции и правила приложения
mapper       преобразование Entity ↔ DTO
repository   Spring Data Repository, Specification, агрегирующие JPQL-запросы
entity       JPA-модель
exception    единая обработка ошибок
```

Используются Dependency Injection, Repository, DTO, Mapper и Specification.
SQL-схему создаёт исключительно Liquibase; Hibernate работает с `ddl-auto=validate`, а Open Session in View отключён.

Amenities хранятся в отдельной таблице с составным первичным ключом и внешним ключом на отель.
Добавление amenities выполняется в транзакции с блокировкой строки отеля, чтобы параллельные добавления не терялись; гистограммы вычисляются через `GROUP BY`, без загрузки всего каталога в память.

Список отелей по заданию возвращается целиком, без пагинации.
Аутентификация, обновление и удаление отелей не добавлены, поскольку не входят в требуемый контракт.

## Тестирование и сборка

```bash
mvn clean verify
java -jar target/property-view-1.0.0.jar
```

Интеграционные тесты поднимают Spring-контекст, применяют Liquibase к отдельной H2 и вызывают API через MockMvc без моков репозитория.
Они проверяют создание, чтение, optional-поля, поиск, повторяющиеся и разделённые запятой amenities, гистограммы, отсутствие дублей, валидацию, ошибки и OpenAPI.

В `.github/workflows/ci.yml` настроен запуск `mvn clean verify` на JDK 21 при push и pull request.
Тесты не зависят от внешних сервисов или заранее заполненной базы.

## Переключение базы

Драйверы H2, PostgreSQL и MySQL включены в зависимости; URL, логин и пароль задаются переменными `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
Hibernate определяет диалект по JDBC-соединению, а Liquibase использует декларативную XML-миграцию вместо SQL, привязанного к H2.

### PostgreSQL

Сначала создайте базу и пользователя на работающем сервере PostgreSQL.
После этого запустите приложение с профилем:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/propertyview'
export DB_USERNAME='propertyview'
export DB_PASSWORD='your-password'
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### MySQL

Сначала создайте базу и пользователя на работающем сервере MySQL.
Используйте профиль `mysql` и соответствующий JDBC URL:

```bash
export DB_URL='jdbc:mysql://localhost:3306/propertyview'
export DB_USERNAME='propertyview'
export DB_PASSWORD='your-password'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Для PowerShell переменные задаются через `$env:DB_URL="..."`, `$env:DB_USERNAME="..."`, `$env:DB_PASSWORD="..."`.
Не сохраняйте пароли в репозитории; наличие профилей само по себе не заменяет проверку на конкретной версии внешней СУБД.

### Файловая H2

Чтобы сохранять данные между запусками, измените только URL.
Каталог `data/` исключён из Git:

```bash
export DB_URL='jdbc:h2:file:./data/propertyview;DB_CLOSE_ON_EXIT=FALSE'
mvn spring-boot:run
```

MongoDB не заявлена как переключаемая конфигурацией база: для неё потребуется отдельная реализация persistence-слоя вместо JPA и иной механизм миграций.
Подготовлены профили именно для реляционных СУБД; автоматические интеграционные тесты в этом проекте используют H2.
