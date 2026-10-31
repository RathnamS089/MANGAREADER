# Inkdot extension repo

An extension repo is just a GitHub repository (or any static host) with an
`index.json` and one JSON file per source. No APKs, no code execution.

In the app: **Extensions → Add a GitHub repo** and enter `user/repo`
(this reads `https://raw.githubusercontent.com/user/repo/main/index.json`),
`github.com/user/repo/tree/branch/folder`, or a direct link to any `index.json`.

## index.json

```json
{
  "name": "My sources",
  "sources": [
    { "id": "mysite", "name": "My Site", "lang": "en", "version": 1, "file": "sources/mysite.json" }
  ]
}
```

`file` is resolved relative to `index.json`. Bump `version` to offer an update.

## Source file

| field | meaning |
|---|---|
| `type` | `"html"` (CSS selectors via jsoup) or `"json"` (paths into an API response) |
| `baseUrl` | available as `{base}` in URLs |
| `pageSize` | used to compute `{offset}` = (page − 1) × pageSize |
| `headers` | sent with every request, including images (e.g. `Referer`) |
| `popular`, `search` | `url`, `list`, `key`, `title`, `cover` |
| `details` | `url`, `description`, `author`, `cover` (optional section) |
| `chapters` | `url`, `list`, `key`, `name`, `date`, `reverse` (set if the site lists oldest first) |
| `pages` | `url`, `list`, `image` |

**URL variables:** `{base}` `{page}` `{offset}` `{query}` `{key}` (the manga
key for details/chapters, the chapter key for pages) and `{mangaKey}`.

**HTML selectors:** `css@attr` reads an attribute (URLs are made absolute),
`css` reads text, `@attr` reads from the list item itself. Use `|` for
fallbacks: `img@data-src|img@src`. For HTML sources `key` is usually a link
(`a@href`) and the next step's `url` is left as the default `{key}`.

```json
"popular": {
  "url": "{base}/popular?page={page}",
  "list": "div.manga-item",
  "key": "a@href",
  "title": "h3",
  "cover": "img@data-src|img@src"
}
```

**JSON paths:** templates with `{path}` placeholders. Paths support
`a.b.c`, `[0]`, `[type=cover_art]` (first array item where `type` equals
`cover_art`), `*` (first value of an object), `$` (the current item) and
`$root` (the whole response). `|` gives fallbacks inside a placeholder.
See `sources/mangadex.json` for a complete example.

Only add sources you have the right to read from.
