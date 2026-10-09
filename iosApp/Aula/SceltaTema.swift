import SwiftUI
import UIKit
import Shared

/// Tema scelto dall'utente, salvato in UserDefaults con la stessa chiave dell'app Android.
enum SceltaTema: String, CaseIterable, Identifiable {
    case sistema = "SISTEMA"
    case chiaro = "CHIARO"
    case scuro = "SCURO"

    static let chiave = "tema"

    var id: String { rawValue }

    var etichetta: String {
        switch self {
        case .sistema: return testi.temaSistema
        case .chiaro: return testi.temaChiaro
        case .scuro: return testi.temaScuro
        }
    }

    var icona: String {
        switch self {
        case .sistema: return "circle.lefthalf.filled"
        case .chiaro: return "sun.max"
        case .scuro: return "moon"
        }
    }

    var stile: UIUserInterfaceStyle {
        switch self {
        case .sistema: return .unspecified
        case .chiaro: return .light
        case .scuro: return .dark
        }
    }

    /// Applica il tema a tutte le finestre: anche fogli, menu e barre di sistema lo seguono.
    /// `.preferredColorScheme(nil)` su iOS 17 non sempre torna al tema di sistema.
    func applica() {
        for scena in UIApplication.shared.connectedScenes {
            guard let scena = scena as? UIWindowScene else { continue }
            for finestra in scena.windows {
                finestra.overrideUserInterfaceStyle = stile
            }
        }
    }
}

/// Voce della toolbar: l'icona mostra il tema attuale, il menu permette di cambiarlo.
struct PulsanteTema: View {
    @AppStorage(SceltaTema.chiave) private var scelta: SceltaTema = .sistema

    var body: some View {
        Menu {
            Picker(testi.tema, selection: $scelta) {
                ForEach(SceltaTema.allCases) { s in
                    Label(s.etichetta, systemImage: s.icona).tag(s)
                }
            }
        } label: {
            Label(testi.temaAttuale(etichetta: scelta.etichetta), systemImage: scelta.icona)
        }
    }
}
