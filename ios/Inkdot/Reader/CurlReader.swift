import SwiftUI
import UIKit

/// Apple Books-style page curl, using UIPageViewController's native curl so the
/// page lifts off with its own shadow. With spreads on and the screen wider than
/// tall, two pages sit either side of a centre spine like an open book.
struct CurlReader<End: View>: View {
    let pages: [URL]
    let headers: [String: String]
    let rtl: Bool
    let spreads: Bool
    @Binding var position: Int?
    let onTapCenter: () -> Void
    @ViewBuilder let end: () -> End

    var body: some View {
        GeometryReader { geo in
            let twoUp = spreads && geo.size.width > geo.size.height
            // The spine location is fixed when the controller is made, so rotating rebuilds it.
            CurlController(pages: pages, headers: headers, rtl: rtl, twoUp: twoUp, position: $position, onTapCenter: onTapCenter, end: end)
                .id(twoUp)
        }
        .ignoresSafeArea()
    }
}

/// What sits on one side of a sheet.
private enum Leaf: Equatable {
    case page(Int), end, blank
}

private struct CurlController<End: View>: UIViewControllerRepresentable {
    let pages: [URL]
    let headers: [String: String]
    let rtl: Bool
    let twoUp: Bool
    @Binding var position: Int?
    let onTapCenter: () -> Void
    let end: () -> End

    /// Leaves in physical left-to-right order. Spreads pair (0,1), (2,3)…, with a blank
    /// first so the cover sits alone on the right. RTL is the same list reversed, which
    /// keeps the pairs intact because the list has an even length.
    static func leaves(_ count: Int, twoUp: Bool, rtl: Bool) -> [Leaf] {
        var list = (0..<count).map(Leaf.page) + [.end]
        if twoUp {
            list.insert(.blank, at: 0)
            if list.count.isMultiple(of: 2) == false { list.append(.blank) }
        }
        return rtl ? list.reversed() : list
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pvc = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [.spineLocation: NSNumber(value: (twoUp ? UIPageViewController.SpineLocation.mid : .min).rawValue)]
        )
        pvc.isDoubleSided = twoUp
        pvc.dataSource = context.coordinator
        pvc.delegate = context.coordinator
        pvc.view.backgroundColor = .black
        // Taps are ours: edges turn pages, the middle shows the controls.
        pvc.gestureRecognizers.filter { $0 is UITapGestureRecognizer }.forEach { $0.isEnabled = false }
        context.coordinator.pvc = pvc
        context.coordinator.show(page: position ?? 0, animated: false)
        return pvc
    }

    func updateUIViewController(_ pvc: UIPageViewController, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        // A jump from outside, such as the slider.
        if let position, position != coordinator.reported {
            coordinator.show(page: position, animated: false)
        }
    }

    @MainActor
    final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate {
        var parent: CurlController
        weak var pvc: UIPageViewController?
        let leaves: [Leaf]
        /// The page last written to `position`, so our own updates don't bounce back as jumps.
        var reported: Int?
        /// A tap-driven turn is animating; further taps are dropped instead of queueing up.
        private var turning = false

        init(_ parent: CurlController) {
            self.parent = parent
            leaves = CurlController.leaves(parent.pages.count, twoUp: parent.twoUp, rtl: parent.rtl)
        }

        private var step: Int { parent.twoUp ? 2 : 1 }

        func show(page: Int, animated: Bool, direction: UIPageViewController.NavigationDirection = .forward) {
            let target: Leaf = page >= parent.pages.count ? .end : .page(max(page, 0))
            guard let index = leaves.firstIndex(of: target) else { return }
            show(index: index, animated: animated, direction: direction)
        }

        private func show(index: Int, animated: Bool, direction: UIPageViewController.NavigationDirection) {
            guard let pvc else { return }
            let first = index - index % step
            let controllers = (first..<first + step).map(leaf)
            turning = animated
            pvc.setViewControllers(controllers, direction: direction, animated: animated) { [weak self] done in
                self?.turning = false
                if done { self?.report() }
            }
            if !animated { report() }
        }

        /// Writes the furthest page on screen back to the reader's position.
        private func report() {
            // A reader that has been swapped out (new chapter, mode change) must not move the new one.
            guard let pvc, pvc.view.window != nil else { return }
            let visible = (pvc.viewControllers ?? []).compactMap { ($0 as? LeafController)?.index }.map { leaves[$0] }
            let page = visible.contains(.end) ? parent.pages.count : visible.compactMap { leaf -> Int? in
                if case .page(let i) = leaf { return i } else { return nil }
            }.max()
            guard let page, page != reported else { return }
            reported = page
            parent.position = page
        }

        private func leaf(_ index: Int) -> UIViewController {
            let controller = LeafController(index: index)
            controller.view.backgroundColor = .black
            switch leaves[index] {
            case .page(let page):
                let view = ZoomingPageView()
                // In a spread both pages hug the spine: left page right-aligned, right page left-aligned.
                let isLeft = index.isMultiple(of: 2)
                view.align = parent.twoUp ? (isLeft ? 1 : 0) : 0.5
                view.onTap = { [weak self] x in
                    guard let self else { return }
                    self.tap(self.parent.twoUp ? (isLeft ? x / 2 : 0.5 + x / 2) : x)
                }
                view.load(parent.pages[page], headers: parent.headers)
                controller.embed(view)
            case .end:
                let host = UIHostingController(rootView: parent.end())
                host.view.backgroundColor = .black
                controller.addChild(host)
                controller.embed(host.view)
                host.didMove(toParent: controller)
            case .blank:
                break
            }
            return controller
        }

        /// `x` is the tap across the whole screen, 0 at the left edge.
        /// Like a paper book, the right edge turns the right-hand page whatever the reading direction.
        private func tap(_ x: CGFloat) {
            if x > 0.7 {
                turn(by: 1)
            } else if x < 0.3 {
                turn(by: -1)
            } else {
                parent.onTapCenter()
            }
        }

        /// Turns one sheet in physical direction: +1 reveals what's to the right.
        private func turn(by delta: Int) {
            guard !turning, let pvc, let current = (pvc.viewControllers?.first as? LeafController)?.index else { return }
            let target = current - current % step + delta * step
            guard leaves.indices.contains(target) else { return }
            show(index: target, animated: true, direction: delta > 0 ? .forward : .reverse)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerBefore vc: UIViewController) -> UIViewController? {
            guard let index = (vc as? LeafController)?.index, index > 0 else { return nil }
            return leaf(index - 1)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerAfter vc: UIViewController) -> UIViewController? {
            guard let index = (vc as? LeafController)?.index, index + 1 < leaves.count else { return nil }
            return leaf(index + 1)
        }

        func pageViewController(_ pvc: UIPageViewController, didFinishAnimating finished: Bool, previousViewControllers: [UIViewController], transitionCompleted completed: Bool) {
            if completed { report() }
        }
    }
}

/// One side of a sheet, remembering where it sits in the leaf list.
private final class LeafController: UIViewController {
    let index: Int

    init(index: Int) {
        self.index = index
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    func embed(_ child: UIView) {
        child.frame = view.bounds
        child.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child)
    }
}
