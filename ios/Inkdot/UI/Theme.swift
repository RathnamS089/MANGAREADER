import CoreText
import Nuke
import SwiftUI
import UIKit

extension Color {
    static let inkRed = Color(red: 215 / 255, green: 25 / 255, blue: 33 / 255)
    static let inkBackground = Color(UIColor { $0.userInterfaceStyle == .dark ? .black : UIColor(white: 0.953, alpha: 1) })
    static let inkCard = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.08, alpha: 1) : .white })
    static let inkCardHigh = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.11, alpha: 1) : UIColor(white: 0.91, alpha: 1) })
}

/// Dot-matrix display face (Doto, OFL) at black weight with fully rounded dots.
enum DotFont {
    private static let descriptor: CTFontDescriptor? = {
        guard let url = Bundle.main.url(forResource: "doto", withExtension: "ttf") else { return nil }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        guard let base = (CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor])?.first else { return nil }
        let wght = 0x7767_6874 // 'wght'
        let rond = 0x524F_4E44 // 'ROND'
        let variations: [NSNumber: NSNumber] = [NSNumber(value: wght): 900, NSNumber(value: rond): 100]
        return CTFontDescriptorCreateCopyWithAttributes(base, [kCTFontVariationAttribute: variations] as CFDictionary)
    }()

    static func font(_ size: CGFloat) -> Font {
        guard let descriptor else { return .system(size: size, weight: .black, design: .monospaced) }
        return Font(CTFontCreateWithFontDescriptor(descriptor, size, nil))
    }
}

extension Font {
    static func dot(_ size: CGFloat) -> Font { DotFont.font(size) }
    static let inkLabel = Font.system(size: 11, weight: .medium, design: .monospaced)
}

/// All image loads go through here so prefetching and display share cache keys.
enum Img {
    enum Kind { case thumb, page, strip }

    static func request(_ url: URL, headers: [String: String], kind: Kind) -> ImageRequest {
        let screen = UIScreen.main.bounds.size
        let scale = UIScreen.main.scale
        let processors: [any ImageProcessing]
        switch kind {
        case .thumb: processors = [ImageProcessors.Resize(width: 220)]
        case .page:
            // ~1.5x screen pixels keeps zoom sharp without huge bitmaps.
            let size = CGSize(width: screen.width * scale * 1.5, height: screen.height * scale * 1.5)
            processors = [ImageProcessors.Resize(size: size, unit: .pixels, contentMode: .aspectFit, upscale: false)]
        case .strip: processors = [ImageProcessors.Resize(width: screen.width * scale, unit: .pixels, upscale: false)]
        }
        return ImageRequest(urlRequest: Http.request(url, headers: headers), processors: processors)
    }
}
