import NukeUI
import SwiftUI

/// Dot-grid texture drawn with Canvas.
struct DotGrid: View {
    var color: Color = .primary.opacity(0.12)
    var spacing: CGFloat = 12
    var radius: CGFloat = 1

    var body: some View {
        Canvas { ctx, size in
            var path = Path()
            var y = spacing / 2
            while y < size.height {
                var x = spacing / 2
                while x < size.width {
                    path.addEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
                    x += spacing
                }
                y += spacing
            }
            ctx.fill(path, with: .color(color))
        }
        .allowsHitTesting(false)
    }
}

struct InkLabel: View {
    let text: String
    var color: Color = .secondary

    init(_ text: String, color: Color = .secondary) {
        self.text = text
        self.color = color
    }

    var body: some View {
        Text(text.uppercased()).font(.inkLabel).tracking(1.6).foregroundStyle(color)
    }
}

struct RedDot: View {
    var size: CGFloat = 6
    var body: some View { Circle().fill(Color.inkRed).frame(width: size, height: size) }
}

struct ScreenHeader<Trailing: View>: View {
    let title: String
    var subtitle: String?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title.uppercased()).font(.dot(36)).tracking(1)
                if let subtitle {
                    HStack(spacing: 8) { RedDot(); InkLabel(subtitle) }
                }
            }
            Spacer()
            trailing
        }
        .padding(.top, 8)
        .padding(.bottom, 16)
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.init(title: title, subtitle: subtitle) { EmptyView() }
    }
}

/// Springy press feedback instead of a highlight.
struct BouncyButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.95
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.3, dampingFraction: 0.55), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == BouncyButtonStyle {
    static var bouncy: BouncyButtonStyle { BouncyButtonStyle() }
}

struct Pill: View {
    let title: String
    var systemImage: String?
    var filled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage).font(.subheadline.weight(.semibold)) }
                Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
            }
            .padding(.horizontal, 20)
            .frame(height: 46)
            .foregroundStyle(filled ? Color.inkBackground : Color.primary)
            .background {
                if filled { Capsule().fill(Color.primary) } else { Capsule().strokeBorder(Color.primary.opacity(0.25), lineWidth: 1) }
            }
        }
        .buttonStyle(.bouncy)
    }
}

struct LoadingDots: View {
    var color: Color = .primary
    @State private var on = false

    var body: some View {
        HStack(spacing: 7) {
            ForEach(0..<3, id: \.self) { i in
                Circle()
                    .fill(color)
                    .frame(width: 8, height: 8)
                    .opacity(on ? 1 : 0.25)
                    .scaleEffect(on ? 1.1 : 0.8)
                    .animation(.easeInOut(duration: 0.5).repeatForever().delay(Double(i) * 0.15), value: on)
            }
        }
        .onAppear { on = true }
    }
}

struct EmptyMessage<Action: View>: View {
    let title: String
    var message: String?
    @ViewBuilder var action: Action

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                DotGrid(color: .primary.opacity(0.18), spacing: 10, radius: 1.5)
                RedDot(size: 10)
            }
            .frame(width: 120, height: 72)
            .padding(.bottom, 12)
            Text(title).font(.headline)
            if let message {
                Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            action.padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
        .padding(.horizontal, 24)
    }
}

extension EmptyMessage where Action == EmptyView {
    init(title: String, message: String? = nil) {
        self.init(title: title, message: message) { EmptyView() }
    }
}

struct CoverView: View {
    let url: String?
    let title: String
    let headers: [String: String]
    var cornerRadius: CGFloat = 18

    var body: some View {
        ZStack {
            Color.inkCardHigh
            DotGrid(color: .primary.opacity(0.1), spacing: 9)
            Text(initials).font(.dot(30)).foregroundStyle(.secondary)
            if let url = url.flatMap(URL.init(string:)) {
                LazyImage(request: Img.request(url, headers: headers, kind: .thumb)) { state in
                    if let image = state.image { image.resizable().scaledToFill() }
                }
                .transition(.opacity)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }

    private var initials: String {
        title.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}

struct MangaCard: View {
    let title: String
    let cover: String?
    let headers: [String: String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            CoverView(url: cover, title: title, headers: headers)
                .aspectRatio(0.69, contentMode: .fit)
            Text(title).font(.subheadline).lineLimit(2).foregroundStyle(.primary).multilineTextAlignment(.leading)
                .padding(.horizontal, 2)
        }
    }
}

struct MissingSourceView: View {
    var body: some View {
        EmptyMessage(title: "Source not installed", message: "Install it again from Extensions.")
    }
}

/// Shared navigation destinations for every tab's stack.
struct SourceRoute: Hashable { let id: String }

extension View {
    func inkDestinations() -> some View {
        navigationDestination(for: MangaItem.self) { DetailsView(manga: $0) }
            .navigationDestination(for: SourceRoute.self) { SourceView(sourceId: $0.id) }
    }
}
