import Foundation
import SwiftSoup

/// Runs a `SourceDef`: fetch a URL, pick a list of items, read fields off each one.
final class DeclarativeSource: Source {
    let def: SourceDef

    init(def: SourceDef) { self.def = def }

    var id: String { def.id }
    var name: String { def.name }
    var lang: String { def.lang }
    var headers: [String: String] { def.headers }
    var supportsSearch: Bool { def.search != nil }
    private var isJson: Bool { def.type.lowercased() == "json" }

    private enum Doc { case html(Document), json(Any) }
    private enum Item { case element(Element), json(Any) }

    func popular(page: Int) async throws -> [MangaItem] {
        try await list(def.popular, vars(page: page))
    }

    func search(_ query: String, page: Int) async throws -> [MangaItem] {
        guard let spec = def.search else { return [] }
        return try await list(spec, vars(page: page, query: query))
    }

    func details(_ manga: MangaItem) async throws -> MangaDetails {
        guard let spec = def.details else { return MangaDetails(cover: manga.cover) }
        let doc = try await load(url(spec.url, vars(key: manga.key)))
        let item = rootItem(doc)
        return MangaDetails(
            description: field(doc, item, spec.description),
            cover: field(doc, item, spec.cover) ?? manga.cover,
            author: field(doc, item, spec.author)
        )
    }

    func chapters(_ manga: MangaItem) async throws -> [Chapter] {
        let spec = def.chapters
        let doc = try await load(url(spec.url, vars(key: manga.key)))
        var seen = Set<String>()
        let chapters = items(doc, spec.list).compactMap { item -> Chapter? in
            guard let key = field(doc, item, spec.key), seen.insert(key).inserted else { return nil }
            return Chapter(key: key, name: field(doc, item, spec.name) ?? "Chapter", date: field(doc, item, spec.date))
        }
        return spec.reverse ? chapters.reversed() : chapters
    }

    func pages(_ manga: MangaItem, chapter: Chapter) async throws -> [URL] {
        let spec = def.pages
        let doc = try await load(url(spec.url, vars(key: chapter.key, mangaKey: manga.key)))
        return items(doc, spec.list).compactMap { field(doc, $0, spec.image).flatMap(URL.init(string:)) }
    }

    private func list(_ spec: ListSpec, _ v: [String: String]) async throws -> [MangaItem] {
        let doc = try await load(url(spec.url, v))
        var seen = Set<String>()
        return items(doc, spec.list).compactMap { item in
            guard let key = field(doc, item, spec.key), seen.insert(key).inserted else { return nil }
            return MangaItem(sourceId: id, key: key, title: field(doc, item, spec.title) ?? "Untitled", cover: field(doc, item, spec.cover))
        }
    }

    private func vars(page: Int = 1, query: String = "", key: String = "", mangaKey: String = "") -> [String: String] {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return [
            "base": def.baseUrl.hasSuffix("/") ? String(def.baseUrl.dropLast()) : def.baseUrl,
            "page": "\(page)",
            "offset": "\((page - 1) * def.pageSize)",
            "query": query.addingPercentEncoding(withAllowedCharacters: allowed) ?? query,
            "key": key,
            "mangaKey": mangaKey,
        ]
    }

    /// Replaces `{name}` with known variables and leaves anything else untouched.
    private func url(_ template: String, _ v: [String: String]) -> String {
        var out = ""
        var i = template.startIndex
        while i < template.endIndex {
            if template[i] == "{", let close = template[i...].firstIndex(of: "}"),
               let value = v[String(template[template.index(after: i)..<close])] {
                out += value
                i = template.index(after: close)
            } else {
                out.append(template[i])
                i = template.index(after: i)
            }
        }
        return out
    }

    private func load(_ url: String) async throws -> Doc {
        let body = try await Http.text(url, headers: def.headers)
        if isJson {
            return .json(try JSONSerialization.jsonObject(with: Data(body.utf8), options: [.fragmentsAllowed]))
        }
        return .html(try SwiftSoup.parse(body, url))
    }

    private func rootItem(_ doc: Doc) -> Item {
        switch doc {
        case .html(let d): return .element(d)
        case .json(let j): return .json(j)
        }
    }

    private func items(_ doc: Doc, _ selector: String) -> [Item] {
        switch doc {
        case .html(let d): return ((try? d.select(selector).array()) ?? []).map(Item.element)
        case .json(let j): return ((JsonPath.resolve(j, selector, root: j) as? [Any]) ?? []).map(Item.json)
        }
    }

    private func field(_ doc: Doc, _ item: Item, _ spec: String?) -> String? {
        switch (item, doc) {
        case (.element(let e), _): return HtmlSelector.get(e, spec)
        case (.json(let value), .json(let root)): return JsonPath.template(spec, item: value, root: root)
        default: return nil
        }
    }
}
