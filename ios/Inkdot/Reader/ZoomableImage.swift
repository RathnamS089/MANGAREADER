import Nuke
import SwiftUI
import UIKit

/// A page image in a native zooming UIScrollView: pinch, double-tap and pan feel
/// like Photos, and swipes pass through to the pager at 1x or at the zoomed edge.
struct ZoomableImage: UIViewRepresentable {
    let url: URL
    let headers: [String: String]
    /// Where the page sits horizontally when it doesn't fill the width: 0 left, 0.5 centre, 1 right.
    var align: CGFloat = 0.5
    let onTap: (CGFloat) -> Void

    func makeUIView(context: Context) -> ZoomingPageView {
        let view = ZoomingPageView()
        view.onTap = onTap
        view.align = align
        view.load(url, headers: headers)
        return view
    }

    func updateUIView(_ view: ZoomingPageView, context: Context) {
        view.onTap = onTap
        view.align = align
        if view.url != url { view.load(url, headers: headers) }
    }

    static func dismantleUIView(_ view: ZoomingPageView, coordinator: ()) {
        view.cancel()
    }
}

final class ZoomingPageView: UIScrollView, UIScrollViewDelegate {
    private let imageView = UIImageView()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private let retryLabel = UILabel()
    private var task: ImageTask?
    private var headers: [String: String] = [:]
    private(set) var url: URL?
    var onTap: ((CGFloat) -> Void)?
    var align: CGFloat = 0.5 {
        didSet { if align != oldValue { setNeedsLayout() } }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 5
        bouncesZoom = true
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        decelerationRate = .fast
        backgroundColor = .black

        imageView.contentMode = .scaleAspectFit
        addSubview(imageView)

        spinner.color = .white
        addSubview(spinner)

        retryLabel.text = "Tap to reload page"
        retryLabel.textColor = .white
        retryLabel.font = .preferredFont(forTextStyle: .subheadline)
        retryLabel.isHidden = true
        addSubview(retryLabel)

        let doubleTap = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        addGestureRecognizer(doubleTap)
        let singleTap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        singleTap.require(toFail: doubleTap)
        addGestureRecognizer(singleTap)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func load(_ url: URL, headers: [String: String]) {
        cancel()
        self.url = url
        self.headers = headers
        setZoomScale(1, animated: false)
        retryLabel.isHidden = true

        let request = Img.request(url, headers: headers, kind: .page)
        if let cached = ImagePipeline.shared.cache[request] {
            imageView.image = cached.image
            spinner.stopAnimating()
            setNeedsLayout()
            return
        }
        imageView.image = nil
        setNeedsLayout()
        spinner.startAnimating()
        task = ImagePipeline.shared.loadImage(with: request) { [weak self] result in
            guard let self else { return }
            self.spinner.stopAnimating()
            switch result {
            case .success(let response):
                self.imageView.image = response.image
                self.setNeedsLayout()
                self.imageView.alpha = 0
                UIView.animate(withDuration: 0.15) { self.imageView.alpha = 1 }
            case .failure:
                self.retryLabel.isHidden = false
            }
        }
    }

    func cancel() {
        task?.cancel()
        task = nil
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if zoomScale == 1 {
            let size = fittedSize()
            imageView.frame = CGRect(origin: .zero, size: size)
            contentSize = size
        }
        placeImage()
        spinner.center = CGPoint(x: bounds.midX, y: bounds.midY)
        retryLabel.sizeToFit()
        retryLabel.center = spinner.center
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }

    func scrollViewDidZoom(_ scrollView: UIScrollView) { placeImage() }

    /// The image's aspect-fit size at 1x, or the whole view before it has loaded.
    private func fittedSize() -> CGSize {
        guard let size = imageView.image?.size, size.width > 0, size.height > 0, bounds.width > 0, bounds.height > 0 else { return bounds.size }
        let scale = min(bounds.width / size.width, bounds.height / size.height)
        return CGSize(width: size.width * scale, height: size.height * scale)
    }

    /// Pads the content so a page smaller than the view sits at `align` and stays centred vertically.
    private func placeImage() {
        let spareX = max(0, bounds.width - contentSize.width)
        let spareY = max(0, bounds.height - contentSize.height)
        let inset = UIEdgeInsets(top: spareY / 2, left: spareX * align, bottom: spareY / 2, right: spareX * (1 - align))
        guard inset != contentInset else { return }
        contentInset = inset
        if zoomScale == 1 { contentOffset = CGPoint(x: -inset.left, y: -inset.top) }
    }

    @objc private func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
        if zoomScale > 1.05 {
            setZoomScale(1, animated: true)
        } else {
            let point = gesture.location(in: imageView)
            let scale: CGFloat = 2.5
            let size = CGSize(width: bounds.width / scale, height: bounds.height / scale)
            zoom(to: CGRect(origin: CGPoint(x: point.x - size.width / 2, y: point.y - size.height / 2), size: size), animated: true)
        }
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        if !retryLabel.isHidden, let url {
            load(url, headers: headers)
            return
        }
        // location(in: self) is in content coordinates; make it relative to the visible page.
        onTap?((gesture.location(in: self).x - contentOffset.x) / max(bounds.width, 1))
    }
}
