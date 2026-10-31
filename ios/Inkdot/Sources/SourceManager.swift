import Foundation
import Observation

/// Installed sources, extension repos, and a small in-memory chapter cache.
@MainActor @Observable
final class SourceManager {
    private(set) var installed: [DeclarativeSource] = []
    private(set) var repos: [String]

    let local = LocalSource()

    @ObservationIgnored private let dir = URL.applicationSupportDirectory.appending(path: "sources")
    @ObservationIgnored private var chapterCache: [String: [Chapter]] = [:]

    init() {
        repos = (UserDefaults.standard.stringArray(forKey: "repos") ?? []).sorted()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        seedBuiltIns()
        reload()
    }

    /// Installs each source bundled with the app once, so there is something to read before
    /// any repo is added, and upgrades them when the app ships a newer version.
    /// Uninstalling one sticks.
    private func seedBuiltIns() {
        let defaults = UserDefaults.standard
        var seeded = Set(defaults.stringArray(forKey: "seededBuiltInIds") ?? [])
        // Builds before per-source tracking only shipped MangaDex.
        if defaults.bool(forKey: "seededBuiltIns") { seeded.insert("mangadex") }
        for id in ["mangadex", "webtoon"] {
            guard let bundled = Bundle.main.url(forResource: id, withExtension: "json"),
                  let data = try? Data(contentsOf: bundled),
                  let def = try? JSONDecoder().decode(SourceDef.self, from: data) else { continue }
            let target = dir.appending(path: id + ".json")
            let installed = (try? Data(contentsOf: target)).flatMap { try? JSONDecoder().decode(SourceDef.self, from: $0) }
            if let installed {
                if installed.version < def.version { try? data.write(to: target, options: .atomic) }
            } else if !seeded.contains(id) {
                try? data.write(to: target, options: .atomic)
            }
            seeded.insert(id)
        }
        defaults.set(Array(seeded).sorted(), forKey: "seededBuiltInIds")
    }

    func source(_ id: String) -> (any Source)? {
        id == local.id ? local : installed.first { $0.id == id }
    }

    func chapters(_ source: any Source, _ manga: MangaItem, refresh: Bool = false) async throws -> [Chapter] {
        if !refresh, let cached = chapterCache[manga.id] { return cached }
        let chapters = try await source.chapters(manga)
        chapterCache[manga.id] = chapters
        return chapters
    }

    /// Accepts `user/repo`, `github.com/user/repo[/tree/branch/path]`,
    /// a github.com blob link, or any direct URL to an index.json.
    func normalizeRepo(_ input: String) -> String {
        var s = input.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s.removeLast() }
        for prefix in ["https://", "http://"] where s.hasPrefix(prefix) { s.removeFirst(prefix.count) }
        let parts = s.split(separator: "/").map(String.init)

        if parts.first == "github.com", parts.count >= 5, parts[3] == "blob" {
            return "https://raw.githubusercontent.com/\(parts[1])/\(parts[2])/\(parts[4...].joined(separator: "/"))"
        }
        if s.hasSuffix(".json") { return "https://" + s }

        let repoParts = parts.first == "github.com" ? Array(parts.dropFirst()) : parts
        if repoParts.count == 2 || (repoParts.count >= 4 && repoParts[2] == "tree") {
            let user = repoParts[0]
            var repo = repoParts[1]
            if repo.hasSuffix(".git") { repo.removeLast(4) }
            let branch = repoParts.count >= 4 ? repoParts[3] : "main"
            let path = repoParts.count > 4 ? "/" + repoParts[4...].joined(separator: "/") : ""
            return "https://raw.githubusercontent.com/\(user)/\(repo)/\(branch)\(path)/index.json"
        }
        return "https://" + s
    }

    func addRepo(_ url: String) { saveRepos(Array(Set(repos + [url])).sorted()) }
    func removeRepo(_ url: String) { saveRepos(repos.filter { $0 != url }) }

    private func saveRepos(_ list: [String]) {
        repos = list
        UserDefaults.standard.set(list, forKey: "repos")
    }

    func fetchRepo(_ url: String) async throws -> RepoIndex {
        try JSONDecoder().decode(RepoIndex.self, from: Data(try await Http.text(url).utf8))
    }

    @discardableResult
    func install(repo: String, entry: RepoEntry) async throws -> SourceDef {
        guard let base = URL(string: repo), let fileURL = URL(string: entry.file, relativeTo: base) else {
            throw InkError("Bad file path \(entry.file)")
        }
        return try save(Data(try await Http.text(fileURL.absoluteString).utf8))
    }

    @discardableResult
    func installFile(_ url: URL) throws -> SourceDef {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return try save(try Data(contentsOf: url))
    }

    private func save(_ data: Data) throws -> SourceDef {
        let def = try JSONDecoder().decode(SourceDef.self, from: data)
        guard !def.id.isEmpty, def.id != LocalSource.sourceId else { throw InkError("Invalid source id") }
        try data.write(to: dir.appending(path: safe(def.id) + ".json"), options: .atomic)
        reload()
        return def
    }

    func uninstall(_ id: String) {
        try? FileManager.default.removeItem(at: dir.appending(path: safe(id) + ".json"))
        reload()
    }

    private func reload() {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        installed = files
            .filter { $0.pathExtension == "json" }
            .compactMap { url in
                (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode(SourceDef.self, from: $0) }
            }
            .map(DeclarativeSource.init(def:))
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func safe(_ id: String) -> String {
        String(id.map { $0.isLetter || $0.isNumber || "._-".contains($0) ? $0 : "_" })
    }
}
