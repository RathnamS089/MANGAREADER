import SwiftUI

struct BrowseView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                ScreenHeader(title: "Browse", subtitle: "\(app.sources.installed.count) sources")
                if app.sources.installed.isEmpty {
                    EmptyMessage(title: "No sources yet", message: "Add a GitHub extension repo in the Extensions tab to start browsing.")
                }
                ForEach(app.sources.installed, id: \.id) { source in
                    NavigationLink(value: SourceRoute(id: source.id)) {
                        HStack(spacing: 16) {
                            ZStack {
                                Circle().fill(Color.inkCardHigh)
                                DotGrid(color: .primary.opacity(0.15), spacing: 6, radius: 0.8).clipShape(Circle())
                                Text(source.name.prefix(1).uppercased()).font(.title2.weight(.medium))
                            }
                            .frame(width: 48, height: 48)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(source.name).font(.headline).foregroundStyle(.primary)
                                InkLabel("\(source.lang) · \(source.def.type) · v\(source.def.version)")
                            }
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(.secondary)
                        }
                        .padding(16)
                        .background(Color.inkCard, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                    }
                    .buttonStyle(BouncyButtonStyle(scale: 0.97))
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Color.inkBackground)
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct SourceView: View {
    let sourceId: String
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var submitted = ""
    @State private var items: [MangaItem] = []
    @State private var page = 1
    @State private var loading = false
    @State private var end = false
    @State private var error: String?
    @State private var loadTask: Task<Void, Never>?

    private let columns = [GridItem(.adaptive(minimum: 108), spacing: 14)]

    var body: some View {
        if let source = app.sources.source(sourceId) {
            content(source)
        } else {
            MissingSourceView()
        }
    }

    @ViewBuilder
    private func content(_ source: any Source) -> some View {
        let grid = ScrollView {
            LazyVGrid(columns: columns, spacing: 18) {
                ForEach(Array(items.enumerated()), id: \.element.key) { index, manga in
                    NavigationLink(value: manga) {
                        MangaCard(title: manga.title, cover: manga.cover, headers: source.headers)
                    }
                    .buttonStyle(BouncyButtonStyle(scale: 0.96))
                    .onAppear { if index >= items.count - 6 { load(source, reset: false) } }
                }
            }
            .padding(.horizontal, 20)

            Group {
                if let error {
                    EmptyMessage(title: "Couldn't load", message: error) {
                        Pill(title: "Retry") { load(source, reset: items.isEmpty) }
                    }
                } else if loading {
                    LoadingDots().padding(32)
                } else if end && items.isEmpty {
                    EmptyMessage(title: "Nothing found")
                }
            }
            .frame(maxWidth: .infinity)
        }
        .background(Color.inkBackground)
        .navigationTitle(source.name)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: submitted) { load(source, reset: true) }

        if source.supportsSearch {
            grid
                .searchable(text: $query, prompt: "Search \(source.name)")
                .onSubmit(of: .search) { submitted = query.trimmingCharacters(in: .whitespaces) }
                .onChange(of: query) { _, new in if new.isEmpty { submitted = "" } }
        } else {
            grid
        }
    }

    private func load(_ source: any Source, reset: Bool) {
        if reset {
            loadTask?.cancel()
            items = []
            page = 1
            end = false
        } else if loading || end {
            return
        }
        loading = true
        error = nil
        let q = submitted
        let p = page
        loadTask = Task {
            do {
                let result = q.isEmpty ? try await source.popular(page: p) : try await source.search(q, page: p)
                guard !Task.isCancelled else { return }
                let known = Set(items.map(\.key))
                let fresh = result.filter { !known.contains($0.key) }
                items += fresh
                if fresh.isEmpty { end = true }
                page = p + 1
            } catch is CancellationError {
                return
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }
}
