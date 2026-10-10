import SwiftUI
import Shared

/// Legislatura scelta dall'utente, salvata in UserDefaults con la stessa chiave dell'app Android.
/// Assente = quella in corso.
enum PreferenzaLegislatura {
    static let chiave = "legislatura"

    static var salvata: Int? {
        let n = UserDefaults.standard.integer(forKey: chiave)
        return n > 0 ? n : nil
    }

    static func salva(_ numero: KotlinInt?) {
        if let numero { UserDefaults.standard.set(numero.valore, forKey: chiave) }
        else { UserDefaults.standard.removeObject(forKey: chiave) }
    }
}

/// Voce della toolbar: mostra la legislatura in numeri romani, il menu permette di cambiarla.
struct PulsanteLegislatura: View {
    @EnvironmentObject private var osservato: Osservato<LegislaturaState>
    private var store: LegislaturaStore { osservato.store as! LegislaturaStore }

    var body: some View {
        if let attiva = osservato.stato.attiva {
            Menu {
                Picker(testi.sceltaLegislatura, selection: Binding(
                    get: { attiva.numero },
                    set: { PreferenzaLegislatura.salva(store.scegli(numero: $0)) }
                )) {
                    ForEach(osservato.stato.disponibili, id: \.numero) { l in
                        Text([l.etichetta, l.periodo].compactMap { $0 }.joined(separator: " · ")).tag(l.numero)
                    }
                }
            } label: {
                Text(attiva.romano)
                    .accessibilityLabel(testi.legislaturaAttuale(romano: attiva.romano))
            }
        }
    }
}
