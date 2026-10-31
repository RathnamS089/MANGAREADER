import Nuke
import SwiftUI

struct SettingsView: View {
    @AppStorage("theme") private var theme: ThemeMode = .dark
    @AppStorage("readerMode") private var readerMode: ReaderMode = .ltr
    @AppStorage("turnStyle") private var turnStyle: TurnStyle = .curl
    @AppStorage("spreads") private var spreads = true
    @State private var cleared = false

    var body: some View {
        List {
            Section {
                Picker("Theme", selection: $theme) {
                    Text("System").tag(ThemeMode.system)
                    Text("Dark").tag(ThemeMode.dark)
                    Text("Light").tag(ThemeMode.light)
                }
                .pickerStyle(.segmented)
            } header: {
                ScreenHeader(title: "Settings", subtitle: "No ads · no tracking")
                    .foregroundStyle(.primary)
                    .textCase(nil)
                    .listRowInsets(EdgeInsets())
            }

            Section("Reader") {
                Picker("Direction", selection: $readerMode) {
                    Text("Left → right").tag(ReaderMode.ltr)
                    Text("Manga ←").tag(ReaderMode.rtl)
                    Text("Webtoon").tag(ReaderMode.vertical)
                }
                .pickerStyle(.segmented)
                VStack(alignment: .leading, spacing: 8) {
                    Picker("Page turn", selection: $turnStyle) {
                        Text("Curl").tag(TurnStyle.curl)
                        Text("Glass").tag(TurnStyle.glass)
                        Text("Slide").tag(TurnStyle.slide)
                    }
                    .pickerStyle(.segmented)
                    Text("Curl lifts the page like Apple Books. Glass slides it under a Liquid Glass edge.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Toggle(isOn: $spreads) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Two-page spreads")
                        Text("Pages side by side in landscape, like an open book. The cover stays on its own.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }

            Section("Storage") {
                Button(cleared ? "Image cache cleared" : "Clear image cache") {
                    ImagePipeline.shared.cache.removeAll()
                    try? FileManager.default.removeItem(at: URL.cachesDirectory.appending(path: "local"))
                    cleared = true
                }
            }

            Section {
                VStack(spacing: 4) {
                    Text("INKDOT").font(.dot(28)).foregroundStyle(.secondary)
                    InkLabel("v0.1.0 · open source")
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.inkBackground)
        .toolbar(.hidden, for: .navigationBar)
    }
}
