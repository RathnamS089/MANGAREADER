import SwiftUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @Environment(AppModel.self) private var app
    @State private var importing = false
    @State private var reader: ReaderTarget?
    @State private var importError: String?
    @State private var editing = false

    private let columns = [GridItem(.adaptive(minimum: 108), spacing: 14)]

    var body: some View {
        let entries = app.library.entries.values
        let shelf = entries.filter(\.inLibrary).sorted { max($0.lastReadAt, $0.addedAt) > max($1.lastReadAt, $1.addedAt) }
        let recent = entries.filter { $0.lastChapterKey != nil && $0.lastReadAt > 0 }.max { $0.lastReadAt < $1.lastReadAt }

        ScrollView {
            LazyVGrid(columns: columns, spacing: 18) {
                Section {
                    ForEach(shelf) { entry in
                        ZStack(alignment: .topLeading) {
                            NavigationLink(value: entry.item) {
                                MangaCard(title: entry.title, cover: entry.cover, headers: app.headers(entry.sourceId))
                            }
                            .buttonStyle(BouncyButtonStyle(scale: 0.96))
                            .disabled(editing)
                            .contextMenu {
                                Button(role: .destructive) { remove(entry) } label: {
                                    Label("Remove from library", systemImage: "trash")
                                }
                            }
                            if editing {
                                Button { remove(entry) } label: {
                                    Image(systemName: "minus")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundStyle(.white)
                                        .frame(width: 30, height: 30)
                                        .background(Circle().fill(Color.inkRed))
                                        .overlay(Circle().stroke(Color.inkBackground, lineWidth: 3))
                                }
                                .buttonStyle(.bouncy)
                                .offset(x: -8, y: -8)
                                .accessibilityLabel("Remove \(entry.title)")
                                .transition(.scale.combined(with: .opacity))
                            }
                        }
                    }
                } header: {
                    VStack(spacing: 0) {
                        ScreenHeader(title: "Library", subtitle: "\(shelf.count) titles") {
                            HStack(spacing: 10) {
                                if !shelf.isEmpty {
                                    Button {
                                        withAnimation(.snappy) { editing.toggle() }
                                    } label: {
                                        Text(editing ? "Done" : "Edit").font(.body.weight(.medium)).frame(height: 44).padding(.horizontal, 6)
                                    }
                                    .buttonStyle(.glass)
                                }
                                if !editing {
                                    Button { importing = true } label: {
                                        Image(systemName: "plus").font(.title3.weight(.medium)).frame(width: 44, height: 44)
                                    }
                                    .buttonStyle(.glass)
                                    .buttonBorderShape(.circle)
                                }
                            }
                        }
                        if let recent, let chapterKey = recent.lastChapterKey {
                            ContinueCard(entry: recent, headers: app.headers(recent.sourceId)) {
                                reader = ReaderTarget(manga: recent.item, chapterKey: chapterKey)
                            }
                            .contextMenu {
                                Button { withAnimation(.snappy) { app.library.dismissContinue(recent.id) } } label: {
                                    Label("Hide from Continue reading", systemImage: "eye.slash")
                                }
                                if recent.inLibrary {
                                    Button(role: .destructive) { remove(recent) } label: {
                                        Label("Remove from library", systemImage: "trash")
                                    }
                                }
                            }
                            .padding(.bottom, 18)
                        }
                        if shelf.isEmpty {
                            EmptyMessage(title: "Your shelf is empty", message: "Browse a source from Extensions, or tap + to open a CBZ or folder from Files.")
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Color.inkBackground)
        .toolbar(.hidden, for: .navigationBar)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.folder, .zip, UTType(filenameExtension: "cbz") ?? .zip]) { result in
            do {
                let item = try app.sources.local.open(try result.get())
                app.library.setInLibrary(item, true)
            } catch {
                importError = error.localizedDescription
            }
        }
        .alert("Couldn't open", isPresented: .constant(importError != nil)) {
            Button("OK") { importError = nil }
        } message: { Text(importError ?? "") }
        .fullScreenCover(item: $reader) { ReaderView(manga: $0.manga, chapterKey: $0.chapterKey) }
        .onChange(of: shelf.isEmpty) { _, empty in if empty { editing = false } }
    }

    private func remove(_ entry: Entry) {
        withAnimation(.snappy) { app.library.remove(entry.id) }
    }
}

private struct ContinueCard: View {
    let entry: Entry
    let headers: [String: String]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                CoverView(url: entry.cover, title: entry.title, headers: headers, cornerRadius: 12)
                    .frame(width: 64, height: 90)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) { RedDot(); InkLabel("Continue reading") }
                    Text(entry.title).font(.headline).lineLimit(1).foregroundStyle(.primary)
                    Text([entry.lastChapterName, "page \(entry.lastPage + 1)"].compactMap { $0 }.joined(separator: " · "))
                        .font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "play.fill")
                    .foregroundStyle(Color.inkBackground)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(Color.primary))
            }
            .padding(12)
            .background {
                ZStack {
                    Color.inkCard
                    DotGrid(color: .primary.opacity(0.05), spacing: 10)
                }
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            }
        }
        .buttonStyle(BouncyButtonStyle(scale: 0.97))
    }
}
