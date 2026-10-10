import SwiftUI
import Shared

struct AulaView: View {
    @ObservedObject var osservato: Osservato<AulaState>
    private var store: AulaStore { osservato.store as! AulaStore }

    var body: some View {
        let stato = osservato.stato
        List {
            Section {
                SelettoreRamo(ramo: stato.ramo) { store.selezionaRamo(ramo: $0) }
                    .listRowSeparator(.hidden)
            }

            if stato.caricamento && stato.sedute.isEmpty {
                StatoCaricamento(messaggio: testi.attesaVotazioni(ramo: stato.ramo))
                    .listRowSeparator(.hidden)
            } else if let errore = stato.errore, stato.sedute.isEmpty {
                StatoErrore(messaggio: errore) { store.ricarica() }
                    .listRowSeparator(.hidden)
            } else {
                Section { Filtri(stato: stato, store: store) }
                    .listRowSeparator(.hidden)
                if stato.filtriAttivi && stato.seduteVisibili.isEmpty {
                    Text(testi.nessunaVotazione(caricate: votazioniCaricate(stato)))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .listRowSeparator(.hidden)
                }
                ForEach(stato.seduteVisibili, id: \.uri) { seduta in
                    Section(seduta.titolo) {
                        ForEach(seduta.votazioni, id: \.uri) { v in
                            NavigationLink(value: v) { RigaVotazione(v: v) }
                        }
                    }
                }
                if stato.altreDisponibili && !stato.sedute.isEmpty {
                    HStack {
                        Spacer()
                        if stato.caricamentoAltre {
                            ProgressView()
                        } else if stato.filtriAttivi {
                            // Con i filtri la lista può restare corta: niente caricamento a catena.
                            Button(testi.votazioniPrecedenti) { store.caricaAltre() }
                                .buttonStyle(.bordered)
                        }
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                    .onAppear { if !stato.filtriAttivi { store.caricaAltre() } }
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(testi.inAula)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { PulsanteLegislatura() }
            ToolbarItem(placement: .topBarTrailing) { PulsanteTema() }
        }
        .searchable(
            text: Binding(get: { stato.ricerca }, set: { store.cerca(testo: $0) }),
            prompt: testi.cercaVotazioni
        )
        .refreshable { store.ricarica() }
    }
}

struct RigaVotazione: View {
    let v: Votazione

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                if let numero = v.numero {
                    Text(testi.numeroVotazione(numero: numero.int32Value)).font(.caption).foregroundStyle(.secondary)
                }
                ChipEsito(esito: v.esito)
                ForEach(v.etichette, id: \.self) { Etichetta(testo: $0) }
            }
            Text(v.intestazione).font(.body.weight(.medium)).lineLimit(2)
            // Se in testa c'è il provvedimento, qui si dice cosa se ne votava.
            if v.atto != nil {
                Text(v.oggetto).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            if v.haConteggi {
                BarraVoti(
                    favorevoli: Int(v.favorevoliN),
                    contrari: Int(v.contrariN),
                    astenuti: Int(v.astenutiN),
                    altezza: 6
                )
                Text(testi.conteggioVoti(favorevoli: v.favorevoliN, contrari: v.contrariN, astenuti: v.astenutiN))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

/// Filtri rapidi e, al Senato, temi.
private struct Filtri: View {
    let stato: AulaState
    let store: AulaStore

    private let filtri: [FiltroVotazioni] = [.fiducia, .finali, .respinte, .sulFilo]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(filtri, id: \.self) { f in
                        Button(f.etichetta) { store.filtra(filtro: stato.filtro == f ? .tutte : f) }
                            .buttonStyle(.bordered)
                            .tint(stato.filtro == f ? Colori.primario : .gray)
                            .font(.caption)
                    }
                }
            }
            if !stato.temiDisponibili.isEmpty {
                Text(testi.temi).font(.caption).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(stato.temiDisponibili, id: \.self) { tema in
                            Button(tema) { store.scegliTema(tema: tema) }
                                .buttonStyle(.bordered)
                                .tint(stato.tema == tema ? Colori.primario : .gray)
                                .font(.caption)
                        }
                    }
                }
            }
            if stato.filtriAttivi {
                Text(testi.votazioniFiltrate(visibili: stato.votazioniVisibili, caricate: votazioniCaricate(stato))).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}


/// Tutte le votazioni caricate, prima di filtri e ricerca.
private func votazioniCaricate(_ stato: AulaState) -> Int32 {
    Int32(stato.sedute.reduce(0) { $0 + $1.votazioni.count })
}
