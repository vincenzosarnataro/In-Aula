import SwiftUI
import Shared

struct GovernoView: View {
    @ObservedObject var osservato: Osservato<GovernoState>
    private var store: GovernoStore { osservato.store as! GovernoStore }

    var body: some View {
        let stato = osservato.stato
        let composizione = stato.composizioneScelta
        List {
            // Più governi nella legislatura: si sceglie quale mostrare, dal più recente.
            if stato.governi.count > 1 {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(stato.governi, id: \.uri) { g in
                                let scelto = g.uri == stato.scelto?.uri
                                Button(g.nome) { store.scegli(governo: g) }
                                    .buttonStyle(.bordered)
                                    .tint(scelto ? Colori.primario : .secondary)
                                    .fontWeight(scelto ? .semibold : .regular)
                            }
                        }
                    }
                    .listRowSeparator(.hidden)
                }
            }
            if let composizione {
                if let p = composizione.presidente {
                    Section {
                        RigaMembro(membro: p, etichetta: testi.presidenteDelConsiglio, dimensione: 64)
                    }
                }
                SezioneMembri(titolo: testi.vicepresidenti, membri: composizione.vicepresidenti)
                SezioneMembri(titolo: testi.ministri(n: Int32(composizione.ministri.count)), membri: composizione.ministri)
                SezioneMembri(
                    titolo: testi.ministriSenzaPortafoglio(n: Int32(composizione.senzaPortafoglio.count)),
                    membri: composizione.senzaPortafoglio
                )
                SezioneMembri(
                    titolo: testi.avvicendamenti(n: Int32(composizione.avvicendamenti.count)),
                    membri: composizione.avvicendamenti,
                    conPeriodo: true
                )
                Text(testi.notaGoverno)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .listRowSeparator(.hidden)
            } else if stato.caricamento {
                StatoCaricamento(messaggio: testi.attesaGoverno)
                    .listRowSeparator(.hidden)
            } else if let errore = stato.errore {
                StatoErrore(messaggio: errore) { store.ricarica() }
                    .listRowSeparator(.hidden)
            } else if stato.governi.isEmpty {
                Text(testi.nessunGoverno)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(stato.scelto?.nome ?? testi.governo)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { PulsanteLegislatura() }
            ToolbarItem(placement: .topBarTrailing) { PulsanteTema() }
        }
        .safeAreaInset(edge: .top) {
            if let periodo = stato.scelto?.periodo {
                Text(periodo)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 4)
                    .background(.bar)
            }
        }
        .refreshable { store.ricarica() }
    }
}

private struct SezioneMembri: View {
    let titolo: String
    let membri: [MembroGoverno]
    var conPeriodo = false

    var body: some View {
        if !membri.isEmpty {
            Section(titolo) {
                ForEach(Array(membri.enumerated()), id: \.offset) { _, m in
                    RigaMembro(membro: m, conPeriodo: conPeriodo)
                }
            }
        }
    }
}

/// Chi è deputato nella legislatura apre la propria scheda; gli altri sono righe semplici.
private struct RigaMembro: View {
    let membro: MembroGoverno
    var etichetta: String? = nil
    var dimensione: CGFloat = 44
    var conPeriodo = false

    var body: some View {
        if let deputato = membro.deputato {
            NavigationLink(value: deputato) { contenuto }
        } else {
            contenuto
        }
    }

    private var contenuto: some View {
        HStack(spacing: 12) {
            Avatar(fotoUrl: membro.fotoUrl, iniziali: membro.iniziali, dimensione: dimensione)
            VStack(alignment: .leading, spacing: 2) {
                if let etichetta {
                    Text(etichetta).font(.caption).foregroundStyle(.secondary)
                }
                Text(membro.nomeCompleto).font(etichetta != nil ? .title3.weight(.semibold) : .body)
                if etichetta == nil {
                    Text(membro.incarico).font(.caption).foregroundStyle(.secondary)
                }
                if let delega = membro.delega {
                    Text(delega).font(.caption).foregroundStyle(.secondary)
                }
                if conPeriodo || etichetta != nil, let periodo = membro.periodo {
                    Text(periodo).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            if membro.interim {
                Etichetta(testo: testi.adInterim)
            }
        }
    }
}
