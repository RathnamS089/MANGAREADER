import SwiftUI
import UniformTypeIdentifiers

struct ExtensionsView: View {
    @Environment(AppModel.self) private var app
    @State private var input = ""
    @State private var indexes: [String: Result<RepoIndex, Error>] = [:]
    @State private var busy: String?
    @State private var importing = false
    @State private var message: String?

    var body: some View {
        let sources = app.sources
        List {
            Section {
                TextField("user/repo or link to index.json", text: $input)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .onSubmit(addRepo)
                Button("Add repo", systemImage: "plus", action: addRepo).disabled(input.isEmpty)
                Button("Import source file", systemImage: "doc.badge.plus") { importing = true }
            } header: {
                ScreenHeader(title: "Extensions", subtitle: "\(sources.installed.count) installed")
                    .foregroundStyle(.primary)
                    .textCase(nil)
                    .listRowInsets(EdgeInsets())
            } footer: {
                Text("A repo is any GitHub repository with an index.json. Sources are plain JSON files — nothing gets downloaded and run as code.")
            }

            Section("Installed") {
                if sources.installed.isEmpty {
                    Text("Nothing installed yet.").foregroundStyle(.secondary)
                }
                ForEach(sources.installed, id: \.id) { source in
                    row(source.name, "\(source.lang) · v\(source.def.version)") { EmptyView() }
                }
                .onDelete { offsets in
                    offsets.map { sources.installed[$0].id }.forEach(sources.uninstall)
                }
            }

            ForEach(sources.repos, id: \.self) { url in
                Section {
                    switch indexes[url] {
                    case nil:
                        LoadingDots().frame(maxWidth: .infinity)
                    case .failure(let error)?:
                        Text("Couldn't read index.json — \(error.localizedDescription)").font(.footnote).foregroundStyle(Color.inkRed)
                    case .success(let index)?:
                        ForEach(index.sources) { entry in
                            let have = sources.installed.first { $0.id == entry.id }
                            row(entry.name, [entry.lang, "v\(entry.version)", entry.description].compactMap { $0 }.joined(separator: " · ")) {
                                if busy == entry.id {
                                    ProgressView()
                                } else if have == nil {
                                    Button("Install") { install(url, entry) }.buttonStyle(.glassProminent)
                                } else if let have, have.def.version < entry.version {
                                    Button("Update") { install(url, entry) }.buttonStyle(.glassProminent)
                                } else {
                                    InkLabel("Installed")
                                }
                            }
                        }
                    }
                } header: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text((try? indexes[url]?.get())?.name ?? "Repository")
                            Text(url).font(.caption2).lineLimit(1).textCase(nil)
                        }
                        Spacer()
                        Button("Remove", systemImage: "xmark") {
                            sources.removeRepo(url)
                            indexes[url] = nil
                        }
                        .labelStyle(.iconOnly)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.inkBackground)
        .toolbar(.hidden, for: .navigationBar)
        .task(id: sources.repos) { await fetchAll(force: false) }
        .refreshable { await fetchAll(force: true) }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText]) { result in
            do {
                let def = try sources.installFile(try result.get())
                message = "Installed \(def.name)"
            } catch {
                message = error.localizedDescription
            }
        }
        .alert(message ?? "", isPresented: .constant(message != nil)) {
            Button("OK") { message = nil }
        }
    }

    private func row<Trailing: View>(_ title: String, _ subtitle: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(subtitle).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
            }
            Spacer()
            trailing()
        }
        .padding(.vertical, 2)
    }

    private func addRepo() {
        guard !input.isEmpty else { return }
        app.sources.addRepo(app.sources.normalizeRepo(input))
        input = ""
    }

    private func install(_ repo: String, _ entry: RepoEntry) {
        busy = entry.id
        Task {
            do { try await app.sources.install(repo: repo, entry: entry) } catch { message = error.localizedDescription }
            busy = nil
        }
    }

    private func fetchAll(force: Bool) async {
        await withTaskGroup(of: (String, Result<RepoIndex, Error>).self) { group in
            for url in app.sources.repos where force || indexes[url] == nil {
                group.addTask {
                    do { return (url, .success(try await app.sources.fetchRepo(url))) } catch { return (url, .failure(error)) }
                }
            }
            for await (url, result) in group { indexes[url] = result }
        }
    }
}
