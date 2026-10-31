import Foundation
import Observation

struct Entry: Codable, Identifiable, Hashable {
    var sourceId: String
    var key: String
    var title: String
    var cover: String?
    var inLibrary = false
    var addedAt: Double = 0
    var lastReadAt: Double = 0
    var lastChapterKey: String?
    var lastChapterName: String?
    var lastPage = 0
    var read: Set<String> = []

    var id: String { "\(sourceId)|\(key)" }
    var item: MangaItem { MangaItem(sourceId: sourceId, key: key, title: title, cover: cover) }
}

/// Library + reading progress, kept as one small JSON file.
@MainActor @Observable
final class Library {
    private(set) var entries: [String: Entry] = [:]

    @ObservationIgnored private let file = URL.applicationSupportDirectory.appending(path: "library.json")
    @ObservationIgnored private var saveTask: Task<Void, Never>?

    init() {
        if let data = try? Data(contentsOf: file),
           let list = try? JSONDecoder().decode([Entry].self, from: data) {
            entries = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        }
    }

    func setInLibrary(_ manga: MangaItem, _ value: Bool) {
        mutate(manga) { e in
            e.inLibrary = value
            if value { e.addedAt = Date.now.timeIntervalSince1970 }
            e.title = manga.title
            if let cover = manga.cover { e.cover = cover }
        }
    }

    /// Takes a title off the shelf and out of Continue reading. Progress and read
    /// markers are kept, so adding it back later resumes where it left off.
    func remove(_ id: String) {
        guard entries[id] != nil else { return }
        entries[id]?.inLibrary = false
        entries[id]?.lastReadAt = 0
        scheduleSave()
    }

    /// Hides a title from Continue reading without touching the shelf or its progress.
    func dismissContinue(_ id: String) {
        guard entries[id] != nil else { return }
        entries[id]?.lastReadAt = 0
        scheduleSave()
    }

    func saveProgress(_ manga: MangaItem, chapterKey: String, chapterName: String, page: Int, finished: Bool) {
        mutate(manga) { e in
            let same = e.lastChapterKey == chapterKey
            e.lastReadAt = Date.now.timeIntervalSince1970
            e.lastChapterName = chapterName.isEmpty ? (same ? e.lastChapterName : nil) : chapterName
            e.lastChapterKey = chapterKey
            e.lastPage = page
            if finished { e.read.insert(chapterKey) }
        }
    }

    private func mutate(_ manga: MangaItem, _ change: (inout Entry) -> Void) {
        var entry = entries[manga.id] ?? Entry(sourceId: manga.sourceId, key: manga.key, title: manga.title, cover: manga.cover)
        change(&entry)
        entries[manga.id] = entry
        scheduleSave()
    }

    /// Coalesces bursts of page turns into one write.
    private func scheduleSave() {
        saveTask?.cancel()
        let snapshot = Array(entries.values)
        let file = file
        saveTask = Task.detached(priority: .utility) {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled, let data = try? JSONEncoder().encode(snapshot) else { return }
            try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
        }
    }
}
