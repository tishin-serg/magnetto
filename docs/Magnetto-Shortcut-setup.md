# Shortcut «Magnetto»

Этот файл содержит готовую схему Shortcut для сборки на iPhone. Секретный
Bearer-токен не хранится в репозитории: Shortcut запрашивает его при первом
запуске и сохраняет в переменной «Magnetto Token».

## Начальная настройка

Создайте Shortcut с именем `Magnetto` и переменные:

- `Magnetto API URL` = `https://api.hamkek.xyz`
- `Magnetto Token` = результат действия «Запросить ввод» с типом «Текст»

Для каждого действия «Получить содержимое URL» добавьте заголовок:

```text
Authorization: Bearer [Magnetto Token]
Content-Type: application/json
```

## Меню

Добавьте действие «Выбрать из меню» с пунктами:

### Новая загрузка

1. «Запросить ввод» — название.
2. POST `${API}/api/v1/catalog/search` с телом `{ "query": "название" }`.
3. «Выбрать из списка» по возвращённым `title`, `year`, `type`.
4. Для `TV` запросить сезон и серии.
5. Выбрать «Ручной» или «Авто».
6. Выбрать назначение: `HOME_PC`, `S3`, `PHONE_VPS`, `PHONE_S3`.
7. Для ручного режима вызвать POST `${API}/api/v1/torrents/search`.
8. Создать уникальный `Idempotency-Key` и вызвать POST `${API}/api/v1/downloads`.
9. Показать полученный `jobId` и завершить Shortcut.

### Мои загрузки

1. GET `${API}/api/v1/downloads`.
2. Показать `title`, `status`, `progress` и `requiredAction`.
3. Для готовой задачи открыть первую ссылку файла.
4. Для `PAUSED` вызвать POST `/pause` или `/resume`.
5. Для `WAITING_FILE_SELECTION` показать файлы и отправить выбранные ID через PUT `/files`.

### Настройки

1. GET `${API}/api/v1/preferences`.
2. Запросить изменяемые значения.
3. PUT `${API}/api/v1/preferences` с JSON настроек.

## Требования безопасности

- Не вставляйте токен в название Shortcut, комментарии или URL.
- Не отправляйте magnet URI в уведомления и логи Shortcut.
- Для первого теста используйте небольшую раздачу.
