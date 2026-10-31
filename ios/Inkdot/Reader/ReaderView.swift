import Nuke
import NukeUI
import SwiftUI

struct ReaderView: View {
    let manga: MangaItem
    @State private var chapterKey: String
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @AppStorage("readerMode") private var savedMode: ReaderMode = .ltr
    /// Set once the reader taps a mode chip; until then the source's suggestion wins.
    @State private var chosenMode: ReaderMode?
    @AppStorage("turnStyle") private var turnStyle: TurnStyle = .curl
    @AppStorage("spreads") private var spreads = true

    @State private var chapters: [Chapter] = []
    @State private var pages: [URL]?
    @State private var error: String?
    @State private var position: Int?
    @State private var uiVisible = false
    @State private var wide = false
    @State private var prefetcher = ImagePrefetcher()

    init(manga: MangaItem, chapterKey: String) {
        self.manga = manga
        _chapterKey = State(initialValue: chapterKey)
    }

    private var mode: ReaderMode {
        chosenMode ?? app.sources.source(manga.sourceId)?.preferredMode ?? savedMode
    }

    private func setMode(_ new: ReaderMode) {
        chosenMode = new
        savedMode = new
    }

    var body: some View {
        let source = app.sources.source(manga.sourceId)
        let index = chapters.firstIndex { $0.key == chapterKey }
        let chapter = index.map { chapters[$0] } ?? Chapter(key: chapterKey, name: "")
        let next = index.flatMap { $0 > 0 ? chapters[$0 - 1] : nil }
        let prev = index.flatMap { $0 + 1 < chapters.count ? chapters[$0 + 1] : nil }
        let headers = source?.headers ?? [:]
        let current = min(position ?? 0, max((pages?.count ?? 1) - 1, 0))

        ZStack {
            Color.black.ignoresSafeArea()

            if let pages {
                Group {
                    if mode == .vertical {
                        WebtoonReader(pages: pages, headers: headers, position: $position, onTap: toggleUI) {
                            ChapterEnd(name: chapter.name, next: next, onNext: { go(to: next) }, onClose: { dismiss() })
                        }
                    } else if turnStyle == .curl {
                        CurlReader(pages: pages, headers: headers, rtl: mode == .rtl, spreads: spreads, position: $position, onTapCenter: toggleUI) {
                            ChapterEnd(name: chapter.name, next: next, onNext: { go(to: next) }, onClose: { dismiss() })
                        }
                    } else {
                        PagedReader(pages: pages, headers: headers, rtl: mode == .rtl, glass: turnStyle == .glass, spreads: spreads, position: $position, onTapCenter: toggleUI) {
                            ChapterEnd(name: chapter.name, next: next, onNext: { go(to: next) }, onClose: { dismiss() })
                        }
                    }
                }
                .id("\(chapterKey)-\(mode.rawValue)-\(turnStyle == .curl)")
            } else if let error {
                VStack(spacing: 16) {
                    Text(error).foregroundStyle(.white).multilineTextAlignment(.center)
                    Pill(title: "Retry") { Task { await loadPages() } }
                }
                .padding(32)
            } else {
                LoadingDots(color: .white)
            }

            controls(chapter: chapter, current: current, total: pages?.count ?? 0, hasPrev: prev != nil, hasNext: next != nil, prev: prev, next: next)
        }
        .onGeometryChange(for: Bool.self) { $0.size.width > $0.size.height } action: { wide = $0 }
        .environment(\.colorScheme, .dark)
        .statusBarHidden(!uiVisible)
        .persistentSystemOverlays(uiVisible ? .automatic : .hidden)
        .task {
            guard let source else { return }
            chapters = (try? await app.sources.chapters(source, manga)) ?? []
        }
        .task(id: chapterKey) { await loadPages() }
        .task(id: position) {
            guard let pages, let position else { return }
            let urls = pages.dropFirst(position + 1).prefix(4)
            prefetcher.startPrefetching(with: urls.map { Img.request($0, headers: headers, kind: mode == .vertical ? .strip : .page) })
            try? await Task.sleep(for: .milliseconds(250))
            let page = min(position, pages.count - 1)
            app.library.saveProgress(manga, chapterKey: chapterKey, chapterName: chapter.name, page: page, finished: page >= pages.count - 1)
        }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private func toggleUI() {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { uiVisible.toggle() }
    }

    private func go(to chapter: Chapter?) {
        guard let chapter else { return }
        chapterKey = chapter.key
    }

    private func loadPages() async {
        guard let source = app.sources.source(manga.sourceId) else { error = "Source not installed"; return }
        pages = nil
        error = nil
        let saved = app.library.entries[manga.id]
        let start = saved?.lastChapterKey == chapterKey ? saved?.lastPage ?? 0 : 0
        do {
            let loaded = try await source.pages(manga, chapter: Chapter(key: chapterKey, name: ""))
            if loaded.isEmpty { error = "No pages in this chapter"; return }
            position = min(start, loaded.count - 1)
            pages = loaded
        } catch {
            self.error = error.localizedDescription
        }
    }

    @ViewBuilder
    private func controls(chapter: Chapter, current: Int, total: Int, hasPrev: Bool, hasNext: Bool, prev: Chapter?, next: Chapter?) -> some View {
        let showTop = uiVisible || pages == nil
        VStack {
            if showTop {
                HStack(spacing: 14) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.headline).frame(width: 44, height: 44)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(manga.title).font(.headline).lineLimit(1)
                        if !chapter.name.isEmpty { InkLabel(chapter.name, color: .white.opacity(0.65)) }
                    }
                    .foregroundStyle(.white)
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
            Spacer()
            if uiVisible, total > 0 {
                GlassEffectContainer {
                    VStack(spacing: 12) {
                        HStack(spacing: 10) {
                            Button { go(to: prev) } label: { Image(systemName: "chevron.left").frame(width: 40, height: 40) }
                                .buttonStyle(.glass).buttonBorderShape(.circle).disabled(!hasPrev).opacity(hasPrev ? 1 : 0.3)
                                .accessibilityLabel("Previous chapter")
                            Slider(
                                value: Binding(get: { Double(current) }, set: { position = Int($0.rounded()) }),
                                in: 0...Double(max(total - 1, 1))
                            )
                            .tint(.inkRed)
                            Button { go(to: next) } label: { Image(systemName: "chevron.right").frame(width: 40, height: 40) }
                                .buttonStyle(.glass).buttonBorderShape(.circle).disabled(!hasNext).opacity(hasNext ? 1 : 0.3)
                                .accessibilityLabel("Next chapter")
                        }
                        HStack(spacing: 6) {
                            InkLabel("\(current + 1) / \(total)", color: .white.opacity(0.7))
                            Spacer()
                            chip("LTR", mode == .ltr) { setMode(.ltr) }
                            chip("RTL", mode == .rtl) { setMode(.rtl) }
                            chip("WEB", mode == .vertical) { setMode(.vertical) }
                            if wide && mode != .vertical {
                                chip("2UP", spreads) { spreads.toggle() }
                            }
                            if mode != .vertical {
                                chip(turnStyle.rawValue.uppercased(), true, accent: true) { turnStyle = turnStyle.next }
                            }
                        }
                    }
                    .padding(16)
                    .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 32, style: .continuous))
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private func chip(_ text: String, _ selected: Bool, accent: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.inkLabel)
                .tracking(1.2)
                .foregroundStyle(selected && !accent ? .black : .white)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(Capsule().fill(selected ? (accent ? Color.inkRed : .white) : .white.opacity(0.1)))
        }
        .buttonStyle(.bouncy)
    }
}

struct PagedReader<End: View>: View {
    let pages: [URL]
    let headers: [String: String]
    let rtl: Bool
    let glass: Bool
    /// Show two pages side by side whenever the reader is wider than it is tall.
    let spreads: Bool
    @Binding var position: Int?
    let onTapCenter: () -> Void
    @ViewBuilder let end: () -> End

    var body: some View {
        GeometryReader { geo in
            let twoUp = spreads && geo.size.width > geo.size.height
            let groups = Self.groups(pages.count, twoUp: twoUp)
            // RTL reverses the physical order instead of flipping layout direction, so
            // geometry stays left-to-right; ids are still the first page number of each sheet.
            let order = rtl ? Array(groups.reversed()) : groups

            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(order, id: \.[0]) { group in
                        sheet(group)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .modifier(GlassTurn(enabled: glass, rtl: rtl))
                            .zIndex(Double(group[0]))
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: scrollID(groups))
            .scrollIndicators(.hidden)
            .id(twoUp)
        }
        .ignoresSafeArea()
    }

    /// Pages grouped into sheets. Spreads keep the cover on its own, like a printed volume.
    /// The last sheet is the end card, `[pages.count]`.
    static func groups(_ count: Int, twoUp: Bool) -> [[Int]] {
        guard twoUp, count > 1 else { return (0...count).map { [$0] } }
        let pairs = stride(from: 1, to: count, by: 2).map { Array($0..<min($0 + 2, count)) }
        return [[0]] + pairs + [[count]]
    }

    @ViewBuilder
    private func sheet(_ group: [Int]) -> some View {
        if group[0] >= pages.count {
            end()
        } else if group.count == 1 {
            ZoomableImage(url: pages[group[0]], headers: headers) { x in tap(x, group: group) }
        } else {
            // In RTL the first page of the pair sits on the right. Both hug the spine.
            let left = rtl ? group[1] : group[0]
            let right = rtl ? group[0] : group[1]
            HStack(spacing: 0) {
                ZoomableImage(url: pages[left], headers: headers, align: 1) { x in tap(x / 2, group: group) }
                ZoomableImage(url: pages[right], headers: headers, align: 0) { x in tap(0.5 + x / 2, group: group) }
            }
        }
    }

    /// Maps the reader's page position to the sheet showing it. A settled sheet reports its last page.
    private func scrollID(_ groups: [[Int]]) -> Binding<Int?> {
        Binding(
            get: { position.map { p in groups.last { $0[0] <= p }?[0] ?? 0 } },
            set: { id in position = id.flatMap { id in groups.first { $0[0] == id }?.last } }
        )
    }

    private func tap(_ x: CGFloat, group: [Int]) {
        let forward = rtl ? x < 0.3 : x > 0.7
        let backward = rtl ? x > 0.7 : x < 0.3
        if forward, let last = group.last, last < pages.count {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { position = last + 1 }
        } else if backward, group[0] > 0 {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.9)) { position = group[0] - 1 }
        } else {
            onTapCenter()
        }
    }
}

struct WebtoonReader<End: View>: View {
    let pages: [URL]
    let headers: [String: String]
    @Binding var position: Int?
    let onTap: () -> Void
    @ViewBuilder let end: () -> End

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(pages.indices, id: \.self) { i in
                    LazyImage(request: Img.request(pages[i], headers: headers, kind: .strip)) { state in
                        if let image = state.image {
                            image.resizable().aspectRatio(contentMode: .fit)
                        } else if state.error != nil {
                            Text("Couldn't load page").foregroundStyle(.white).frame(height: 300)
                        } else {
                            LoadingDots(color: .white.opacity(0.7)).frame(maxWidth: .infinity).frame(height: 520)
                        }
                    }
                }
                end().containerRelativeFrame(.vertical) { h, _ in h * 0.7 }
            }
            .scrollTargetLayout()
        }
        .scrollPosition(id: $position)
        .scrollIndicators(.hidden)
        .ignoresSafeArea()
        .onTapGesture(perform: onTap)
    }
}

struct ChapterEnd: View {
    let name: String
    let next: Chapter?
    let onNext: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            InkLabel("End of chapter", color: .white.opacity(0.6))
            Text(name).font(.title3.weight(.semibold)).foregroundStyle(.white).multilineTextAlignment(.center)
            Spacer().frame(height: 20)
            if let next {
                InkLabel("Up next", color: .inkRed)
                Text(next.name).foregroundStyle(.white).multilineTextAlignment(.center)
                Pill(title: "Next chapter", systemImage: "chevron.right", action: onNext).padding(.top, 8)
            } else {
                Text("You're all caught up").foregroundStyle(.white.opacity(0.7))
                Pill(title: "Close reader", filled: false, action: onClose).padding(.top, 8)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}
