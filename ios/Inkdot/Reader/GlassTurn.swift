import SwiftUI

/// Liquid-glass page turn for a page inside a horizontal paging ScrollView.
///
/// The page being left sinks back (parallax, scale, dim, rounded corners) while
/// the incoming page slides over it with a real Liquid Glass edge that refracts
/// the artwork under it, a sweeping specular sheen and a slight lean.
/// Everything is driven by `visualEffect`, so scrolling never re-runs view bodies.
/// The overlays never take touches: they'd swallow taps meant for the page underneath.
struct GlassTurn: ViewModifier {
    let enabled: Bool
    let rtl: Bool

    func body(content: Content) -> some View {
        if enabled {
            content
                .overlay { CornerMask(radius: 30).fill(.black, style: FillStyle(eoFill: true)).visualEffect { c, p in c.opacity(sinking(p, rtl)) }.allowsHitTesting(false) }
                .overlay { Color.black.visualEffect { c, p in c.opacity(0.6 * sinking(p, rtl)) }.allowsHitTesting(false) }
                .overlay(alignment: rtl ? .trailing : .leading) { GlassEdge(rtl: rtl).visualEffect { c, p in c.opacity(incoming(p, rtl)) } }
                .overlay { Sheen(rtl: rtl) }
                .overlay { CornerMask(radius: 30).fill(.black, style: FillStyle(eoFill: true)).visualEffect { c, p in c.opacity(incoming(p, rtl)) }.allowsHitTesting(false) }
                .visualEffect { c, p in
                    let t = Self.turn(p, rtl)
                    let width = p.size.width
                    let dir: Double = rtl ? -1 : 1
                    let sink = max(0, -t)
                    let rise = max(0, t)
                    let wobble = sin(Double.pi * rise)
                    let shift = -Self.raw(p) * width * 0.72
                    return c
                        // Sinking page moves at ~28% of the finger and recedes into the stack.
                        .offset(x: sink > 0 ? shift : 0)
                        .scaleEffect(1 - 0.09 * sink)
                        // Incoming sheet leans in from its leading edge and squashes slightly mid-turn.
                        .rotation3DEffect(.degrees(-dir * 5 * rise), axis: (0, 1, 0), anchor: rtl ? .trailing : .leading, perspective: 0.6)
                        .scaleEffect(x: 1, y: 1 - 0.025 * wobble)
                }
        } else {
            content
        }
    }

    /// Page offset as a fraction of its width, physical left-to-right.
    nonisolated static func raw(_ p: GeometryProxy) -> Double {
        let w = p.size.width
        guard w > 0 else { return 0 }
        return max(-1, min(1, p.frame(in: .scrollView(axis: .horizontal)).minX / w))
    }

    /// > 0: incoming sheet sliding over, < 0: page sinking under, in reading direction.
    nonisolated static func turn(_ p: GeometryProxy, _ rtl: Bool) -> Double {
        rtl ? -raw(p) : raw(p)
    }
}

private nonisolated func sinking(_ p: GeometryProxy, _ rtl: Bool) -> Double { max(0, -GlassTurn.turn(p, rtl)) }
private nonisolated func incoming(_ p: GeometryProxy, _ rtl: Bool) -> Double { max(0, GlassTurn.turn(p, rtl)) }

/// Black corners that fake rounded clipping on a black background, without a per-frame clip.
private struct CornerMask: Shape {
    let radius: CGFloat
    func path(in rect: CGRect) -> Path {
        var path = Path(rect)
        path.addRoundedRect(in: rect, cornerSize: CGSize(width: radius, height: radius), style: .continuous)
        return path
    }
}

/// A sliver of real Liquid Glass on the leading edge, plus a crisp highlight and a red dispersion line.
private struct GlassEdge: View {
    let rtl: Bool

    var body: some View {
        HStack(spacing: 0) {
            if rtl { Spacer(minLength: 0) }
            ZStack(alignment: rtl ? .trailing : .leading) {
                LinearGradient(colors: [.white.opacity(0.28), .clear], startPoint: rtl ? .trailing : .leading, endPoint: rtl ? .leading : .trailing)
                    .frame(width: 48)
                Rectangle()
                    .fill(.clear)
                    .frame(width: 22)
                    .glassEffect(.clear, in: Rectangle())
                HStack(spacing: 0) {
                    if rtl { Color.inkRed.opacity(0.4).frame(width: 1.5) }
                    Color.white.opacity(0.8).frame(width: 1.5)
                    if !rtl { Color.inkRed.opacity(0.4).frame(width: 1.5) }
                }
            }
            if !rtl { Spacer(minLength: 0) }
        }
        .allowsHitTesting(false)
    }
}

/// Diagonal specular band that sweeps across the incoming sheet as it settles.
private struct Sheen: View {
    let rtl: Bool

    var body: some View {
        LinearGradient(
            stops: [.init(color: .clear, location: 0), .init(color: .white.opacity(0.18), location: 0.5), .init(color: .clear, location: 1)],
            startPoint: UnitPoint(x: 0, y: 0.1),
            endPoint: UnitPoint(x: 1, y: 0.35)
        )
        .frame(width: 280)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: rtl ? .trailing : .leading)
        .visualEffect { c, p in
            let f = incoming(p, rtl)
            let dir: Double = rtl ? -1 : 1
            return c
                .offset(x: dir * (p.size.width * (1 - f) * 1.25 - 140))
                .opacity(sin(Double.pi * f))
        }
        .allowsHitTesting(false)
    }
}
