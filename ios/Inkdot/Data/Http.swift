import Foundation

enum Http {
    static let userAgent = "Inkdot/0.1 (iOS; open-source manga reader)"

    static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.urlCache = URLCache(memoryCapacity: 10 << 20, diskCapacity: 50 << 20)
        config.timeoutIntervalForRequest = 20
        config.httpAdditionalHeaders = ["User-Agent": userAgent]
        return URLSession(configuration: config)
    }()

    static func request(_ url: URL, headers: [String: String]) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        return request
    }

    static func text(_ urlString: String, headers: [String: String] = [:]) async throws -> String {
        guard let url = URL(string: urlString) else { throw InkError("Bad URL: \(urlString)") }
        let (data, response) = try await session.data(for: request(url, headers: headers))
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw InkError("HTTP \(http.statusCode) · \(url.host() ?? "")")
        }
        return String(decoding: data, as: UTF8.self)
    }
}

struct InkError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
