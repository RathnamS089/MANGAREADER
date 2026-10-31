import Foundation
import SwiftSoup

/// HTML field selectors: `css@attr`, `css` (text), or `@attr` on the item itself.
/// `|` separates fallbacks. URL attributes are made absolute.
enum HtmlSelector {
    /// Attributes that hold links; anything else (ids, counts) is returned as written.
    private static let urlAttributes: Set<String> = ["href", "src", "data-src", "data-url", "data-original", "data-lazy-src", "poster"]

    static func get(_ element: Element, _ spec: String?) -> String? {
        guard let spec else { return nil }
        for alt in spec.split(separator: "|") {
            let s = alt.trimmingCharacters(in: .whitespaces)
            let css: String
            let attr: String
            if let at = s.lastIndex(of: "@") {
                css = String(s[..<at]).trimmingCharacters(in: .whitespaces)
                attr = String(s[s.index(after: at)...]).trimmingCharacters(in: .whitespaces)
            } else {
                css = s
                attr = "text"
            }
            guard let target = css.isEmpty ? element : (try? element.select(css).first()) else { continue }
            let value: String?
            switch attr {
            case "text": value = try? target.text()
            case "own": value = target.ownText()
            case "html": value = try? target.html()
            default:
                let abs = urlAttributes.contains(attr.lowercased()) ? ((try? target.absUrl(attr)) ?? "") : ""
                if abs.isEmpty { value = try? target.attr(attr) } else { value = abs }
            }
            if let v = value?.trimmingCharacters(in: .whitespacesAndNewlines), !v.isEmpty { return v }
        }
        return nil
    }
}

/// Minimal JSON paths: `a.b[0].c`, `[type=cover_art]` filters, `*` (first value),
/// `$` (current item) and `$root` (whole response). Field specs are templates such
/// as `Ch. {attributes.chapter}`; a spec without braces is a single path.
enum JsonPath {
    private enum Token { case name(String), index(Int), filter(String, String) }

    static func resolve(_ start: Any?, _ path: String, root: Any) -> Any? {
        var p = path.trimmingCharacters(in: .whitespaces)
        var cur = start
        if p == "$" { return start }
        if p.hasPrefix("$root") {
            cur = root
            p = String(p.dropFirst(5))
            if p.hasPrefix(".") { p.removeFirst() }
        } else if p.hasPrefix("$.") {
            p = String(p.dropFirst(2))
        }
        for token in tokenize(p) {
            guard let node = cur else { return nil }
            switch token {
            case .name("*"):
                if let dict = node as? [String: Any] {
                    // Prefer English, then a stable order (JSON objects arrive unordered).
                    cur = dict["en"] ?? dict.keys.sorted().first.flatMap { dict[$0] }
                } else {
                    cur = (node as? [Any])?.first
                }
            case .name(let name):
                cur = (node as? [String: Any])?[name]
            case .index(let i):
                let array = node as? [Any]
                cur = array.flatMap { i < $0.count ? $0[i] : nil }
            case .filter(let k, let v):
                cur = (node as? [Any])?.first { text(($0 as? [String: Any])?[k]) == v }
            }
        }
        return cur
    }

    static func text(_ value: Any?) -> String? {
        switch value {
        case let s as String: return s
        case let n as NSNumber:
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "true" : "false" }
            return n.stringValue
        default: return nil
        }
    }

    static func template(_ spec: String?, item: Any, root: Any) -> String? {
        guard let spec else { return nil }
        let t = spec.contains("{") ? spec : "{\(spec)}"
        var out = ""
        var i = t.startIndex
        while i < t.endIndex {
            if t[i] == "{", let close = t[i...].firstIndex(of: "}") {
                let inner = t[t.index(after: i)..<close]
                let value = inner.split(separator: "|").lazy
                    .compactMap { text(resolve(item, String($0), root: root)) }
                    .first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
                out += value ?? ""
                i = t.index(after: close)
            } else {
                out.append(t[i])
                i = t.index(after: i)
            }
        }
        let collapsed = out.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        return collapsed.isEmpty ? nil : collapsed
    }

    private static func tokenize(_ path: String) -> [Token] {
        var tokens: [Token] = []
        var name = ""
        var i = path.startIndex
        func flush() { if !name.isEmpty { tokens.append(.name(name)); name = "" } }
        while i < path.endIndex {
            let c = path[i]
            if c == "." {
                flush()
            } else if c == "[", let close = path[i...].firstIndex(of: "]") {
                flush()
                let inner = String(path[path.index(after: i)..<close])
                if let n = Int(inner) {
                    tokens.append(.index(n))
                } else if let eq = inner.firstIndex(of: "=") {
                    tokens.append(.filter(String(inner[..<eq]), String(inner[inner.index(after: eq)...])))
                } else {
                    tokens.append(.name(inner))
                }
                i = close
            } else {
                name.append(c)
            }
            i = path.index(after: i)
        }
        flush()
        return tokens
    }
}
