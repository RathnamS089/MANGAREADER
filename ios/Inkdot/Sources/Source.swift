import Foundation

protocol Source: AnyObject {
    var id: String { get }
    var name: String { get }
    var lang: String { get }
    var headers: [String: String] { get }
    var supportsSearch: Bool { get }

    func popular(page: Int) async throws -> [MangaItem]
    func search(_ query: String, page: Int) async throws -> [MangaItem]
    func details(_ manga: MangaItem) async throws -> MangaDetails
    func chapters(_ manga: MangaItem) async throws -> [Chapter]
    /// Remote image URLs, or file URLs for local manga.
    func pages(_ manga: MangaItem, chapter: Chapter) async throws -> [URL]
}
