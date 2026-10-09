import SwiftUI
import Shared

/// Un provvedimento: iter nei due rami, chi l'ha presentato, temi e tutte le votazioni d'Aula.
struct SchedaAttoView: View {
    @StateObject private var osservato: Osservato<SchedaAttoState>
    private var store: SchedaAttoStore { osservato.store as! SchedaAttoStore }

    init(atto: Atto) {
        _osservato = StateObject(wrappedValue: Osservato(
            AppGraph.shared.schedaAttoStore(atto: atto),
            chiudiAllaFine: true
        ))
    }

    var body: some View {
        let stato = osservato.stato
        let atto = stato.scheda?.atto ?? stato.atto
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text([atto.numero, atto.natura].compactMap { $0 }.joined(separator: " · "))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Colori.primario)
                    Text(atto.titoloBreve ?? atto.titolo).font(.title3.weight(.semibold))
                    if let breve = atto.titoloBreve, breve != atto.titolo {
                        Text(atto.titolo).font(.callout).foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }

            if stato.caricamento && stato.scheda == nil {
                StatoCaricamento(messaggio: stato.messaggioAttesa)
            } else if let errore = stato.errore, stato.scheda == nil {
                StatoErrore(messaggio: errore) { store.ricarica() }
            } else if let scheda = stato.scheda {
                if !scheda.iter.isEmpty {
                    Section(testi.iter) {
                        ForEach(Array(scheda.iter.enumerated()), id: \.offset) { _, fase in
                            let corrente = fase.numero.replacingOccurrences(of: " ", with: "") == atto.numero.replacingOccurrences(of: " ", with: "")
                            HStack(alignment: .top, spacing: 10) {
                                Circle()
                                    .fill(corrente ? Colori.primario : Color(.systemGray4))
                                    .frame(width: 10, height: 10)
                                    .padding(.top, 5)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(fase.numero) · \(fase.ramo.etichetta)")
                                        .font(.callout.weight(.semibold))
                                        .foregroundStyle(corrente ? Colori.primario : .primary)
                                    Text([fase.statoEsteso, fase.dataEstesa].compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                Section(testi.dettagli) { Dettagli(scheda: scheda) }
                Section {
                    if let nota = scheda.nota {
                        Text(nota).font(.footnote).foregroundStyle(.secondary)
                    }
                } header: {
                    Text(testi.votazioniInAula(n: Int32(scheda.votazioni.count)))
                }
                ForEach(stato.sedute, id: \.uri) { seduta in
                    Section("\(seduta.ramo.etichetta) · \(seduta.titolo)") {
                        ForEach(seduta.votazioni, id: \.uri) { v in
                            NavigationLink(value: v) { RigaVotazione(v: v) }
                        }
                    }
                }
            }
        }
        .navigationTitle(atto.numero)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct Dettagli: View {
    let scheda: SchedaAtto
    @State private var tuttiIFirmatari = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Voce(etichetta: testi.iniziativa, valore: scheda.iniziativa)
            Voce(etichetta: testi.presentatoIl, valore: scheda.presentatoIlEsteso)
            Voce(etichetta: testi.primoFirmatario, valore: scheda.primoFirmatario)
            if !scheda.altriFirmatari.isEmpty {
                let mostrati = tuttiIFirmatari ? scheda.altriFirmatari : Array(scheda.altriFirmatari.prefix(6))
                Voce(
                    etichetta: testi.altriFirmatari(n: Int32(scheda.altriFirmatari.count)),
                    valore: mostrati.joined(separator: ", ") + (mostrati.count < scheda.altriFirmatari.count ? "…" : "")
                )
                if mostrati.count < scheda.altriFirmatari.count {
                    Button(testi.mostraTutti) { tuttiIFirmatari = true }.font(.caption)
                }
            }
            if !scheda.relatori.isEmpty {
                Voce(
                    etichetta: testi.relatori,
                    valore: scheda.relatori.map { r in
                        let extra = [r.organo, r.tipo == "relatore" ? "" : r.tipo].filter { !$0.isEmpty }.joined(separator: ", ")
                        return extra.isEmpty ? r.nome : "\(r.nome) (\(extra))"
                    }.joined(separator: "\n")
                )
            }
            if !scheda.temi.isEmpty {
                Text(testi.temi).font(.caption).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) { ForEach(scheda.temi, id: \.self) { Etichetta(testo: $0) } }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

private struct Voce: View {
    let etichetta: String
    let valore: String?

    var body: some View {
        if let valore, !valore.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                Text(etichetta).font(.caption).foregroundStyle(.secondary)
                Text(valore).font(.callout)
            }
        }
    }
}
