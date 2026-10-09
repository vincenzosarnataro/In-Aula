import SwiftUI
import Shared

struct ParlamentareView: View {
    @StateObject private var osservato: Osservato<ParlamentareState>
    private var store: ParlamentareStore { osservato.store as! ParlamentareStore }

    init(parlamentare: Parlamentare) {
        _osservato = StateObject(wrappedValue: Osservato(
            AppGraph.shared.parlamentareStore(parlamentare: parlamentare),
            chiudiAllaFine: true
        ))
    }

    var body: some View {
        let stato = osservato.stato
        let p = stato.parlamentare
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 16) {
                    Avatar(fotoUrl: p.fotoUrl, iniziali: p.iniziali, dimensione: 72)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(p.nomeCompleto).font(.title2.weight(.semibold))
                        if !p.gruppo.isEmpty {
                            Text(p.gruppo).font(.callout).foregroundStyle(.secondary)
                        }
                    }
                }

                if let storia = stato.storiaGruppi, !storia.isEmpty {
                    StoriaGruppi(storia: storia)
                }

                if stato.caricamento && stato.presenze == nil {
                    StatoCaricamento(messaggio: testi.attesaPresenze(ramo: p.ramo))
                } else if let errore = stato.errore, stato.presenze == nil {
                    StatoErrore(messaggio: errore) { store.ricarica() }
                } else if let pr = stato.presenze {
                    SchedaPresenze(pr: pr)
                }
            }
            .padding()
        }
        .navigationTitle(p.ramo.etichetta)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SchedaPresenze: View {
    let pr: Presenze

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(testi.partecipazioneAlVoto).font(.headline)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(pr.presenzeLabel).font(.system(size: 40, weight: .semibold))
                Text(testi.presenzeSu(votazioni: pr.votazioniTotali)).font(.callout).foregroundStyle(.secondary)
            }
            BarraSegmenti(
                valori: [
                    (Int(pr.presenze), Colori.primario),
                    (Int(pr.missioni), Colori.astenuto),
                    (Int(pr.assenze), Colori.contrario),
                ],
                altezza: 14
            )
            HStack(spacing: 14) {
                VoceLegenda(colore: Colori.primario, etichetta: testi.presenze, valore: pr.presenzeLabel)
                VoceLegenda(colore: Colori.astenuto, etichetta: testi.missioni, valore: pr.missioniLabel)
                VoceLegenda(colore: Colori.contrario, etichetta: testi.assenze, valore: pr.assenzeLabel)
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))

        VStack(alignment: .leading, spacing: 12) {
            Text(testi.votiEspressi).font(.headline)
            BarraVoti(favorevoli: Int(pr.favorevoli), contrari: Int(pr.contrari), astenuti: Int(pr.astenuti))
            HStack(spacing: 14) {
                VoceLegenda(colore: Colori.favorevole, etichetta: testi.favorevole, valore: "\(pr.favorevoli)")
                VoceLegenda(colore: Colori.contrario, etichetta: testi.contrario, valore: "\(pr.contrari)")
                VoceLegenda(colore: Colori.astenuto, etichetta: testi.astenuto, valore: "\(pr.astenuti)")
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))

        Text(([testi.formulaPresenze] + [pr.nota].compactMap { $0 }).joined(separator: "\n"))
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}

/// I gruppi della legislatura, dal primo all'attuale.
private struct StoriaGruppi: View {
    let storia: [Adesione]

    var body: some View {
        let cambi = storia.count - 1
        VStack(alignment: .leading, spacing: 10) {
            Text(testi.storiaGruppi(cambi: Int32(cambi)))
                .font(.headline)
            ForEach(Array(storia.enumerated()), id: \.offset) { _, a in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(a.al == nil ? Colori.primario : Color(.systemGray4))
                        .frame(width: 10, height: 10)
                        .padding(.top, 5)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(a.gruppo)
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(a.al == nil ? Colori.primario : .primary)
                        Text(a.periodo).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}

