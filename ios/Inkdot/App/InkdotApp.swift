import Nuke
import Observation
import SwiftUI

@MainActor @Observable
final class AppModel {
    let library = Library()
    let sources = SourceManager()

    func headers(_ sourceId: String) -> [String: String] {
        sources.source(sourceId)?.headers ?? [:]
    }
}

@main
struct InkdotApp: App {
    @State private var app = AppModel()
    @AppStorage("theme") private var theme: ThemeMode = .dark

    init() {
        ImagePipeline.shared = ImagePipeline(configuration: .withDataCache(sizeLimit: 512 << 20))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .tint(.inkRed)
                .preferredColorScheme(theme == .system ? nil : (theme == .dark ? .dark : .light))
        }
    }
}

struct RootView: View {
    var body: some View {
        TabView {
            Tab("Library", systemImage: "books.vertical") {
                NavigationStack { LibraryView().inkDestinations() }
            }
            Tab("Browse", systemImage: "safari") {
                NavigationStack { BrowseView().inkDestinations() }
            }
            Tab("Extensions", systemImage: "puzzlepiece.extension") {
                NavigationStack { ExtensionsView() }
            }
            Tab("Settings", systemImage: "gearshape") {
                NavigationStack { SettingsView() }
            }
        }
    }
}
