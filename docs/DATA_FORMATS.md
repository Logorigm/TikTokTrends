# Форматы данных

## CSV-импорт (вкладка «Поиск» → «Импорт CSV»)

Разделитель — `,` или `;` (определяется автоматически). Кодировка UTF-8. Обязательна только колонка `url`.

| Колонка | Пример | Описание |
|---|---|---|
| `url` | `https://www.tiktok.com/@user/video/7300000000000000000` | Ссылка на видео |
| `captured_at` | `2026-10-18T10:00:00Z` | Когда сняты цифры (ISO-8601, дата или epoch). Пусто — время импорта |
| `views`, `likes`, `comments`, `shares` | `12.5K`, `1 200`, `3M` | Счётчики на момент `captured_at` |
| `title` | | Описание |
| `author`, `author_followers`, `author_country` | `user`, `45000`, `FR` | Автор, подписчики, **страна автора** (ISO) |
| `published_at` | | Если пусто — вычисляется из ID видео |
| `hashtags` | `#a #b` или `a;b` | |
| `sound` | | Название звука |
| `category` | | |
| `format` | `pov;split screen` | Форматы/приёмы |
| `popular_in` | `US|FR` | Где вы **наблюдали** популярность (основание: личное наблюдение) |

Повторный импорт той же ссылки с новым `captured_at` добавляет новый замер — так появляется динамика.

## JSON-фид (Настройки → Подключённые источники)

Предназначен для собственного сервера, который получает данные из лицензированного источника и хранит секреты у себя.
Приложение делает `GET` с заголовком `Authorization: Bearer <token>` (если токен задан).

```json
{
  "source": { "name": "My backend", "license": "…", "updated_at": "2026-10-18T10:00:00Z" },
  "items": [
    {
      "url": "https://www.tiktok.com/@user/video/7300000000000000000",
      "title": "…",
      "author": "user",
      "author_followers": 45000,
      "author_country": "FR",
      "published_at": "2026-10-17T08:00:00Z",
      "hashtags": ["a", "b"],
      "sound": { "id": "123", "title": "Song" },
      "category": "Юмор",
      "formats": ["pov"],
      "trend_types": ["SOUND", "FORMAT"],
      "thumbnail_url": "https://…",
      "popular_in": [{ "country": "US", "rank": 3 }],
      "stats": { "captured_at": "2026-10-18T10:00:00Z", "views": 120000, "likes": 9000, "comments": 300, "shares": 500 }
    }
  ]
}
```

`popular_in` — страны, где источник фиксирует популярность (основание «Подключённый источник»). `author_country` — только страна автора. Неизвестные поля игнорируются.
