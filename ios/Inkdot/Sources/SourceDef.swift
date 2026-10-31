import Foundation

/// A source extension: a small JSON file hosted in a GitHub repo.
/// Same format as the Android app — see extensions-repo/README.md.
struct SourceDef: Codable {
    var id: String
    var name: String
    var lang = "en"
    var version = 1
    /// "html" (CSS selectors, parsed with SwiftSoup) or "json" (paths into an API response).
    var type = "html"
    var baseUrl = ""
    var pageSize = 20
    var headers: [String: String] = [:]
    var popular: ListSpec
    var search: ListSpec?
    var details: DetailsSpec?
    var chapters: ChapterSpec
    var pages: PageSpec

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        lang = try c.decodeIfPresent(String.self, forKey: .lang) ?? "en"
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? "html"
        baseUrl = try c.decodeIfPresent(String.self, forKey: .baseUrl) ?? ""
        pageSize = try c.decodeIfPresent(Int.self, forKey: .pageSize) ?? 20
        headers = try c.decodeIfPresent([String: String].self, forKey: .headers) ?? [:]
        popular = try c.decode(ListSpec.self, forKey: .popular)
        search = try c.decodeIfPresent(ListSpec.self, forKey: .search)
        details = try c.decodeIfPresent(DetailsSpec.self, forKey: .details)
        chapters = try c.decode(ChapterSpec.self, forKey: .chapters)
        pages = try c.decode(PageSpec.self, forKey: .pages)
    }
}

struct ListSpec: Codable {
    var url: String
    var list: String
    var key: String
    var title: String
    var cover: String?
}

struct DetailsSpec: Codable {
    var url = "{key}"
    var description: String?
    var author: String?
    var cover: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url) ?? "{key}"
        description = try c.decodeIfPresent(String.self, forKey: .description)
        author = try c.decodeIfPresent(String.self, forKey: .author)
        cover = try c.decodeIfPresent(String.self, forKey: .cover)
    }
}

struct ChapterSpec: Codable {
    var url = "{key}"
    var list: String
    var key: String
    var name: String
    var date: String?
    /// Set when the site lists chapters oldest first.
    var reverse = false

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url) ?? "{key}"
        list = try c.decode(String.self, forKey: .list)
        key = try c.decode(String.self, forKey: .key)
        name = try c.decode(String.self, forKey: .name)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        reverse = try c.decodeIfPresent(Bool.self, forKey: .reverse) ?? false
    }
}

struct PageSpec: Codable {
    var url = "{key}"
    var list: String
    var image: String

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        url = try c.decodeIfPresent(String.self, forKey: .url) ?? "{key}"
        list = try c.decode(String.self, forKey: .list)
        image = try c.decode(String.self, forKey: .image)
    }
}

struct RepoIndex: Decodable {
    var name = "Repository"
    var sources: [RepoEntry] = []

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "Repository"
        sources = try c.decodeIfPresent([RepoEntry].self, forKey: .sources) ?? []
    }

    private enum CodingKeys: String, CodingKey { case name, sources }
}

struct RepoEntry: Decodable, Identifiable {
    var id: String
    var name: String
    var lang = "en"
    var version = 1
    var file: String
    var description: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        lang = try c.decodeIfPresent(String.self, forKey: .lang) ?? "en"
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        file = try c.decode(String.self, forKey: .file)
        description = try c.decodeIfPresent(String.self, forKey: .description)
    }

    private enum CodingKeys: String, CodingKey { case id, name, lang, version, file, description }
}
