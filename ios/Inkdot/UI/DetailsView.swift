import NukeUI
import SwiftUI

struct DetailsView: View {
    let manga: MangaItem
    @Environment(AppModel.self) private var app
    @State private var details: MangaDetails?
    @State private var chapters: [Chapter]?
    @State private var error: String?
    @State private var reader: ReaderTarget?
    @State private var expanded = false

    var body: some View {
        if let source = app.sources.source(manga.sourceId) {
            content(source)
        } else {
            MissingSourceView()
        }
    }

    private func content(_ source: any Source) -> some View {
        let entry = app.library.entries[manga.id]
        let cover = details?.cover ?? manga.cover
        var withCover = manga
        withCover.cover = cover
        let list = chapters ?? []
        let resume = entry?.lastChapterKey.flatMap { key in list.first { $0.key == key } }
        let target = resume ?? list.last
        let inLibrary = entry?.inLibrary == true

        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                hero(withCover, author: details?.author, sourceName: source.name, headers: source.headers)

                HStack(spacing: 10) {
                    Pill(title: resume != nil ? "Continue" : "Start reading", systemImage: "play.fill") {
                        if let target { reader = ReaderTarget(manga: withCover, chapterKey: target.key) }
                    }
                    .frame(maxWidth: .infinity)
                    .opacity(target == nil ? 0.4 : 1)
                    .disabled(target == nil)
                    Pill(title: inLibrary ? "In library" : "Add", systemImage: inLibrary ? "checkmark" : "plus", filled: false) {
                        app.library.setInLibrary(withCover, !inLibrary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)

                if let description = details?.description {
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(expanded ? nil : 3)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .onTapGesture { withAnimation(.spring(response: 0.4, dampingFraction: 0.9)) { expanded.toggle() } }
                }

                InkLabel("Chapters · \(chapters.map { "\($0.count)" } ?? "…")")
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 8)

                if let error {
                    EmptyMessage(title: "Couldn't load", message: error) {
                        Pill(title: "Retry") { Task { await load(source, refresh: true) } }
                    }
                } else if chapters == nil {
                    LoadingDots().frame(maxWidth: .infinity).padding(32)
                } else if list.isEmpty {
                    EmptyMessage(title: "No chapters to read", message: "This source has none in your language. Licensed series are often only on the publisher's official app.")
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(list) { chapter in
                            let current = entry?.lastChapterKey == chapter.key
                            let read = entry?.read.contains(chapter.key) == true
                            Button { reader = ReaderTarget(manga: withCover, chapterKey: chapter.key) } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(chapter.name).font(.body).lineLimit(1).foregroundStyle(.primary)
                                        if let date = chapter.date { InkLabel(String(date.prefix(10))) }
                                    }
                                    Spacer()
                                    if current { RedDot(size: 8) }
                                }
                                .padding(.horizontal, 20)
                                .padding(.vertical, 14)
                                .contentShape(Rectangle())
                                .opacity(read && !current ? 0.45 : 1)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .background(Color.inkBackground)
        .ignoresSafeArea(edges: .top)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await load(source, refresh: true) } } label: { Image(systemName: "arrow.clockwise") }
            }
        }
        .task { await load(source, refresh: false) }
        .fullScreenCover(item: $reader) { ReaderView(manga: $0.manga, chapterKey: $0.chapterKey) }
    }

    private func hero(_ manga: MangaItem, author: String?, sourceName: String, headers: [String: String]) -> some View {
        ZStack(alignment: .bottomLeading) {
            if let url = manga.cover.flatMap(URL.init(string:)) {
                LazyImage(request: Img.request(url, headers: headers, kind: .thumb)) { state in
                    if let image = state.image { image.resizable().scaledToFill().blur(radius: 24).opacity(0.5) }
                }
                .frame(height: 380)
                .clipped()
            }
            LinearGradient(colors: [Color.inkBackground.opacity(0.2), Color.inkBackground], startPoint: .top, endPoint: .bottom)
            HStack(alignment: .bottom, spacing: 18) {
                CoverView(url: manga.cover, title: manga.title, headers: headers)
                    .frame(width: 118, height: 170)
                    .shadow(color: .black.opacity(0.35), radius: 16, y: 8)
                VStack(alignment: .leading, spacing: 6) {
                    InkLabel(sourceName)
                    Text(manga.title).font(.title2.weight(.semibold)).lineLimit(4)
                    if let author { Text(author).font(.subheadline).foregroundStyle(.secondary).lineLimit(1) }
                }
                .padding(.bottom, 4)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .frame(height: 380)
    }

    private func load(_ source: any Source, refresh: Bool) async {
        error = nil
        if refresh { chapters = nil }
        async let d = try? source.details(manga)
        do {
            chapters = try await app.sources.chapters(source, manga, refresh: refresh)
        } catch {
            self.error = error.localizedDescription
        }
        details = await d
    }
}
