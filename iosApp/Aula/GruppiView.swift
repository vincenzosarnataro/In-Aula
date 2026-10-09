import Charts
import SwiftUI
import Shared

/// Il colore fisso del gruppo, definito nel modulo condiviso (ColoriGruppi): lo stesso di Android.
func coloreGruppo(_ gruppo: GruppoParlamentare) -> Color {
    let hex = UInt32(truncatingIfNeeded: gruppo.colore)
    return Color(chiaro: hex, scuro: hex)
}

/// I gruppi parlamentari di un ramo, dal più numeroso, con la composizione del ramo.
struct GruppiView: View {
    @ObservedObject var osservato: Osservato<GruppiState>
    private var store: GruppiStore { osservato.store as! GruppiStore }

    var body: some View {
        let stato = osservato.stato
        List {
            Section {
                SelettoreRamo(ramo: stato.ramo) { store.selezionaRamo(ramo: $0) }
                    .listRowSeparator(.hidden)
            }
            if stato.caricamento && stato.gruppi.isEmpty {
                StatoCaricamento(messaggio: testi.attesaParlamentari(ramo: stato.ramo))
            } else if let errore = stato.errore, stato.gruppi.isEmpty {
                StatoErrore(messaggio: errore) { store.ricarica() }
            } else {
                Section {
                    VStack(spacing: 12) {
                        TortaComposizione(gruppi: stato.gruppi, totale: Int(stato.seggiTotali))
                        Text(testi.notaGruppi)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, 4)
                    .listRowSeparator(.hidden)
                } header: {
                    Text(testi.composizione)
                }
                Section(testi.numeroGruppi(n: Int32(stato.gruppi.count)) + " · " + testi.seggi(n: stato.seggiTotali)) {
                    ForEach(stato.gruppi, id: \.nome) { g in
                        NavigationLink(value: g) {
                            HStack(spacing: 12) {
                                Circle().fill(coloreGruppo(g)).frame(width: 14, height: 14)
                                VStack(alignment: .leading) {
                                    Text(g.nome).lineLimit(2)
                                    Text(testi.seggi(n: g.seggi) + " · " + g.quotaLabel)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(testi.gruppiParlamentari)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PulsanteTema() } }
        .refreshable { store.ricarica() }
    }
}

/// Ciambella dei seggi per gruppo, dal più numeroso in senso orario dalle 12, con il totale al
/// centro. Lo stacco tra le fette separa i gruppi; la legenda è l'elenco dei gruppi sotto.
private struct TortaComposizione: View {
    let gruppi: [GruppoParlamentare]
    let totale: Int

    var body: some View {
        Chart(gruppi, id: \.nome) { g in
            SectorMark(
                angle: .value(testi.seggiEtichetta, Int(g.seggi)),
                innerRadius: .ratio(0.6),
                angularInset: 1
            )
            .foregroundStyle(coloreGruppo(g))
            .accessibilityLabel(g.nome)
            .accessibilityValue(testi.seggi(n: g.seggi))
        }
        .chartLegend(.hidden)
        .frame(width: 200, height: 200)
        .overlay {
            VStack(spacing: 0) {
                Text("\(totale)").font(.system(size: 36, weight: .semibold))
                Text(testi.seggiEtichetta).font(.callout).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// Un gruppo: seggi, quota sul ramo e membri in carica.
struct GruppoView: View {
    let gruppo: GruppoParlamentare

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text("\(gruppo.seggi)").font(.system(size: 40, weight: .semibold))
                        Text(testi.quotaSeggi(quota: gruppo.quotaLabel)).font(.callout).foregroundStyle(.secondary)
                    }
                    BarraSegmenti(
                        valori: [
                            (Int(gruppo.seggi), coloreGruppo(gruppo)),
                            (Int(gruppo.seggiTotali - gruppo.seggi), Color(.systemGray5)),
                        ],
                        altezza: 14
                    )
                    if gruppo.conCambi > 0 {
                        Text(testi.membriConCambi(n: gruppo.conCambi))
                            .font(.footnote)
                            .foregroundStyle(Colori.suTerziario)
                    }
                }
                .padding(.vertical, 4)
            }
            Section(testi.membri(n: gruppo.seggi)) {
                ForEach(gruppo.membri, id: \.uri) { p in
                    NavigationLink(value: p) {
                        HStack(spacing: 12) {
                            Avatar(fotoUrl: p.fotoUrl, iniziali: p.iniziali)
                            VStack(alignment: .leading) {
                                Text(p.nomeCompleto)
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
        .navigationTitle(gruppo.nome)
        .navigationBarTitleDisplayMode(.inline)
    }
}
