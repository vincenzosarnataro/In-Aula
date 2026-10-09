import SwiftUI
import Shared

/// Ponte tra gli store Kotlin e SwiftUI: riceve ogni nuovo stato via `observe`
/// (sul main thread, perché gli store usano Dispatchers.Main) e lo pubblica.
final class Osservato<S: AnyObject>: ObservableObject {
    @Published private(set) var stato: S
    let store: Store<S>
    private let chiudiAllaFine: Bool
    private var osservazione: Osservazione?

    /// `chiudiAllaFine`: lo store appartiene a questa schermata e va chiuso quando sparisce.
    init(_ store: Store<S>, chiudiAllaFine: Bool = false) {
        self.store = store
        self.chiudiAllaFine = chiudiAllaFine
        self.stato = store.current()
        self.osservazione = store.observe { [weak self] nuovo in
            self?.stato = nuovo
        }
    }

    deinit {
        osservazione?.cancel()
        if chiudiAllaFine { store.close() }
    }
}

extension KotlinInt {
    var valore: Int { Int(truncating: self) }
}
