import CryptoKit
import Foundation
import ZIPFoundation

/// Reads manga from the Files app: a CBZ/ZIP, or a folder holding images,
/// chapter sub-folders and/or CBZ files. Access survives relaunches through
/// security-scoped bookmarks embedded in the keys.
///
/// Manga keys:   `tree:<bookmark>` or `file:<bookmark>`
/// Chapter keys: `dir:<bookmark>|<relative path>` or `zip:<bookmark>|<relative path>`
final class LocalSource: Source {
    static let sourceId = "local"

    let id = LocalSource.sourceId
    let name = "Local files"
    let lang = "—"
    let headers: [String: String] = [:]
    let supportsSearch = false

    func open(_ url: URL) throws -> MangaItem {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let isDir = (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? url.hasDirectoryPath
        let bookmark = try url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil)
        let title = isDir ? url.lastPathComponent : url.deletingPathExtension().lastPathComponent
        return MangaItem(sourceId: id, key: (isDir ? "tree:" : "file:") + bookmark.base64EncodedString(), title: title)
    }

    func popular(page: Int) async throws -> [MangaItem] { [] }
    func search(_ query: String, page: Int) async throws -> [MangaItem] { [] }

    func details(_ manga: MangaItem) async throws -> MangaDetails {
        MangaDetails(description: manga.key.hasPrefix("tree:") ? "Folder on this iPhone" : "Archive on this iPhone")
    }

    func chapters(_ manga: MangaItem) async throws -> [Chapter] {
        let (kind, bookmark) = split(manga.key, ":")
        if kind == "file" { return [Chapter(key: "zip:\(bookmark)|", name: manga.title)] }

        return try withAccess(bookmark) { root in
            let children = try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.isDirectoryKey], options: [.skipsHiddenFiles])
            let nested = children
                .filter { $0.isDirectory || Self.isArchive($0) }
                .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
                .map { url in
                    Chapter(
                        key: (url.isDirectory ? "dir:" : "zip:") + bookmark + "|" + url.lastPathComponent,
                        name: url.isDirectory ? url.lastPathComponent : url.deletingPathExtension().lastPathComponent
                    )
                }
            let loose = children.contains { !$0.isDirectory && Self.isImage($0) } ? [Chapter(key: "dir:\(bookmark)|", name: manga.title)] : []
            return Array((loose + nested).reversed())
        }
    }

    func pages(_ manga: MangaItem, chapter: Chapter) async throws -> [URL] {
        let (kind, rest) = split(chapter.key, ":")
        let (bookmark, relative) = split(rest, "|")
        let out = URL.cachesDirectory.appending(path: "local/" + Self.hash(chapter.key))
        let done = out.appending(path: ".done")
        let fm = FileManager.default

        if !fm.fileExists(atPath: done.path()) {
            try? fm.removeItem(at: out)
            try fm.createDirectory(at: out, withIntermediateDirectories: true)
            try withAccess(bookmark) { root in
                let target = relative.isEmpty ? root : root.appending(path: relative)
                if kind == "zip" {
                    let archive = try Archive(url: target, accessMode: .read)
                    for entry in archive where entry.type == .file && Self.isImage(URL(fileURLWithPath: entry.path)) {
                        let name = entry.path.replacingOccurrences(of: "/", with: "_")
                        _ = try archive.extract(entry, to: out.appending(path: name))
                    }
                } else {
                    for file in try fm.contentsOfDirectory(at: target, includingPropertiesForKeys: nil, options: [.skipsHiddenFiles]) where Self.isImage(file) {
                        try fm.copyItem(at: file, to: out.appending(path: file.lastPathComponent))
                    }
                }
            }
            fm.createFile(atPath: done.path(), contents: nil)
        }

        return try fm.contentsOfDirectory(at: out, includingPropertiesForKeys: nil, options: [.skipsHiddenFiles])
            .filter(Self.isImage)
            .sorted { $0.lastPathComponent.localizedStandardCompare($1.lastPathComponent) == .orderedAscending }
    }

    private func withAccess<T>(_ bookmark: String, _ body: (URL) throws -> T) throws -> T {
        guard let data = Data(base64Encoded: bookmark) else { throw InkError("Broken file link") }
        var stale = false
        let url = try URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale)
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        return try body(url)
    }

    private func split(_ s: String, _ separator: Character) -> (String, String) {
        guard let i = s.firstIndex(of: separator) else { return (s, "") }
        return (String(s[..<i]), String(s[s.index(after: i)...]))
    }

    private static func hash(_ s: String) -> String {
        SHA256.hash(data: Data(s.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }

    static func isImage(_ url: URL) -> Bool {
        ["jpg", "jpeg", "png", "webp", "gif", "avif", "heic"].contains(url.pathExtension.lowercased())
    }

    static func isArchive(_ url: URL) -> Bool {
        ["cbz", "zip"].contains(url.pathExtension.lowercased())
    }
}

private extension URL {
    var isDirectory: Bool { (try? resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false }
}
