import Foundation

/// A title as listed by a source. `key` is whatever the source needs to find it again (a URL or an API id).
struct MangaItem: Hashable, Codable {
    let sourceId: String
    let key: String
    let title: String
    var cover: String?

    var id: String { "\(sourceId)|\(key)" }
}

struct MangaDetails {
    var description: String?
    var cover: String?
    var author: String?
}

/// Sources return chapters newest first.
struct Chapter: Hashable, Identifiable {
    let key: String
    let name: String
    var date: String?

    var id: String { key }
}

enum ReaderMode: String, CaseIterable {
    case ltr, rtl, vertical
}

enum ThemeMode: String, CaseIterable {
    case system, dark, light
}

/// Identifies a reader session to present.
struct ReaderTarget: Identifiable {
    let manga: MangaItem
    let chapterKey: String
    var id: String { manga.id + "#" + chapterKey }
}

/// How horizontal pages turn: Apple Books curl, Liquid Glass slide, or a plain slide.
enum TurnStyle: String, CaseIterable {
    case curl, glass, slide

    var next: TurnStyle {
        let all = TurnStyle.allCases
        return all[(all.firstIndex(of: self)! + 1) % all.count]
    }
}
