import SwiftUI
import Shared

struct ParlamentariView: View {
    @ObservedObject var osservato: Osservato<ParlamentariState>
    private var store: ParlamentariStore { osservato.store as! ParlamentariStore }

    var body: some View {
        let stato = osservato.stato
        List {
            Section {
                SelettoreRamo(ramo: stato.ramo) { store.selezionaRamo(ramo: $0) }
                    .listRowSeparator(.hidden)
            }
            if stato.caricamento && stato.tutti.isEmpty {
                StatoCaricamento(messaggio: testi.attesaParlamentari(ramo: stato.ramo))
            } else if let errore = stato.errore, stato.tutti.isEmpty {
                StatoErrore(messaggio: errore) { store.ricarica() }
            } else {
                if stato.conCambi > 0 {
                    Toggle(testi.hannoCambiatoGruppo(n: stato.conCambi), isOn: Binding(
                        get: { stato.soloCambi },
                        set: { store.mostraSoloCambi(attivo: $0) }
                    ))
                    .font(.callout)
                    .tint(Colori.primario)
                    .listRowSeparator(.hidden)
                }
                Section(testi.inCarica(n: Int32(stato.visibili.count))) {
                    ForEach(stato.visibili, id: \.uri) { p in
                        NavigationLink(value: p) {
                            HStack(spacing: 12) {
                                Avatar(fotoUrl: p.fotoUrl, iniziali: p.iniziali)
                                VStack(alignment: .leading) {
                                    Text(p.nomeCompleto)
                                    if !p.gruppo.isEmpty {
                                        Text(p.gruppo).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                    if p.cambiDiGruppo > 0 {
                                        Text(testi.cambiDiGruppo(n: p.cambiDiGruppo))
                                            .font(.caption)
                                            .foregroundStyle(Colori.suTerziario)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(testi.parlamentari)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PulsanteTema() } }
        .searchable(
            text: Binding(get: { stato.ricerca }, set: { store.cerca(testo: $0) }),
            prompt: testi.cercaParlamentari
        )
        .refreshable { store.ricarica() }
    }
}
