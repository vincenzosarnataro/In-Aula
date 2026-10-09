import SwiftUI
import Shared

struct VotazioneView: View {
    @StateObject private var osservato: Osservato<VotazioneState>
    private var store: VotazioneStore { osservato.store as! VotazioneStore }

    init(votazione: Votazione) {
        // L'autoclosure di StateObject viene valutata una sola volta: lo store nasce
        // con la schermata e viene chiuso quando la schermata sparisce.
        _osservato = StateObject(wrappedValue: Osservato(
            AppGraph.shared.votazioneStore(votazione: votazione),
            chiudiAllaFine: true
        ))
    }

    var body: some View {
        let stato = osservato.stato
        let v = stato.votazione
        List {
            Section { Intestazione(v: v) }

            if stato.caricamento && stato.dettaglio == nil {
                StatoCaricamento(messaggio: stato.messaggioAttesa)
            } else if let errore = stato.errore, stato.dettaglio == nil {
                StatoErrore(messaggio: errore) { store.ricarica() }
            } else if let dettaglio = stato.dettaglio {
                if let assenze = stato.assenzeDecisive {
                    Section { RiquadroAssenze(assenze: assenze, ramo: v.ramo) }
                }
                if !dettaglio.voti.isEmpty {
                    Section(testi.distribuzioneVoto) { Distribuzione(stato: stato, store: store) }
                }
                if let nota = dettaglio.nota {
                    Text(nota).font(.footnote).foregroundStyle(.secondary)
                }
                if !dettaglio.ripartizione.isEmpty {
                    Section {
                        ForEach(dettaglio.ripartizione, id: \.gruppo) { g in
                            Button { store.filtraGruppo(gruppo: g.gruppo) } label: { RigaGruppo(g: g) }
                                .buttonStyle(.plain)
                                .listRowBackground(stato.filtroGruppo == g.gruppo ? Colori.primario.opacity(0.12) : nil)
                        }
                    } header: {
                        Text(testi.comeHannoVotatoIGruppi)
                    } footer: {
                        Text(testi.spiegazioneCompattezza)
                    }
                }
                Section(testi.comeHannoVotato(voti: Int32(stato.votiFiltrati.count))) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            if let gruppo = stato.filtroGruppo {
                                Button { store.filtraGruppo(gruppo: nil) } label: {
                                    Label(gruppo, systemImage: "xmark")
                                }
                                .buttonStyle(.bordered)
                                .tint(Colori.primario)
                                .font(.caption)
                            }
                            if !stato.ribelli.isEmpty {
                                Button(testi.soloRibelli(ribelli: Int32(stato.ribelli.count))) {
                                    store.mostraSoloRibelli(attivo: !stato.soloRibelli)
                                }
                                .buttonStyle(.bordered)
                                .tint(stato.soloRibelli ? Colori.primario : .gray)
                                .font(.caption)
                            }
                            ForEach(stato.tipiPresenti, id: \.self) { tipo in
                                let attivo = stato.filtro == tipo
                                Button(tipo.etichetta) { store.filtra(tipo: tipo) }
                                    .buttonStyle(.bordered)
                                    .tint(attivo ? Colori.primario : .gray)
                                    .font(.caption)
                            }
                        }
                    }
                    ForEach(stato.votiFiltrati, id: \.parlamentareUri) { voto in
                        NavigationLink(value: parlamentare(da: voto, ramo: v.ramo)) {
                            RigaVoto(voto: voto, ribelle: stato.ribelli.contains(voto.parlamentareUri))
                        }
                    }
                }
            }
        }
        .navigationTitle(testi.titoloVotazione(numero: v.numero))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func parlamentare(da voto: VotoIndividuale, ramo: Ramo) -> Parlamentare {
        Parlamentare(
            uri: voto.parlamentareUri,
            ramo: ramo,
            nome: voto.nome,
            cognome: "",
            gruppo: voto.gruppo,
            fotoUrl: nil,
            cambiDiGruppo: 0
        )
    }
}

private struct Intestazione: View {
    let v: Votazione

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(testi.sottotitoloVotazione(v: v))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Colori.primario)
            Text(v.oggetto).font(.title3.weight(.semibold))
            if let atto = v.atto {
                NavigationLink(value: atto) { SchedaAtto(atto: atto) }
            }
            HStack(spacing: 6) {
                ChipEsito(esito: v.esito)
                ForEach(v.etichette, id: \.self) { Etichetta(testo: $0) }
            }
            if v.haConteggi {
                HStack(alignment: .firstTextBaseline) {
                    if let presenti = v.presenti { Numero(valore: "\(presenti.valore)", etichetta: testi.presenti) }
                    Numero(valore: "\(v.favorevoliN)", etichetta: testi.favorevoli, colore: Colori.favorevole)
                    Numero(valore: "\(v.contrariN)", etichetta: testi.contrari, colore: Colori.contrario)
                    Numero(valore: "\(v.astenutiN)", etichetta: testi.astenuti, colore: Colori.astenuto)
                }
                BarraVoti(
                    favorevoli: Int(v.favorevoliN),
                    contrari: Int(v.contrariN),
                    astenuti: Int(v.astenutiN),
                    altezza: 10
                )
            }
        }
        .padding(.vertical, 6)
    }
}

/// Il provvedimento su cui si vota, con l'avviso se l'abbiamo dedotto noi.
private struct SchedaAtto: View {
    let atto: Atto

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text([atto.numero, atto.natura].compactMap { $0 }.joined(separator: " · "))
                .font(.caption.weight(.semibold))
                .foregroundStyle(Colori.primario)
            Text(atto.titoloBreve ?? atto.titolo).font(.callout.weight(.semibold))
            if atto.titoloBreve != nil && atto.natura == nil {
                Text(atto.titolo).font(.caption).foregroundStyle(.secondary)
            }
            if let proponenti = atto.proponenti {
                Text(testi.presentatoDa(proponenti: proponenti)).font(.caption).foregroundStyle(.secondary)
            }
            if atto.dedotto {
                Label(testi.attoDedotto, systemImage: "info.circle")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.top, 4)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct Numero: View {
    let valore: String
    let etichetta: String
    var colore: Color = .primary

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(valore).font(.title2.weight(.semibold)).foregroundStyle(colore)
            Text(etichetta).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct Distribuzione: View {
    let stato: VotazioneState
    let store: VotazioneStore

    var body: some View {
        let v = stato.votazione
        VStack(spacing: 12) {
            Picker(testi.vista, selection: Binding(get: { stato.vista }, set: { store.cambiaVista(vista: $0) })) {
                Text(VistaEmiciclo.gruppo.etichetta).tag(VistaEmiciclo.gruppo)
                Text(VistaEmiciclo.voto.etichetta).tag(VistaEmiciclo.voto)
            }
            .pickerStyle(.segmented)

            EmicicloVoti(seggi: stato.seggi, disposizione: stato.disposizione, gruppoEvidenziato: stato.filtroGruppo) {
                VStack(spacing: 0) {
                    if v.esito != .sconosciuto {
                        Text(v.esito.etichetta)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(v.esito == .approvata ? Colori.favorevole : Colori.contrario)
                    }
                    if let scarto = v.scartoLabel {
                        Text(scarto).font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .padding(.bottom, 2)
            }
            .accessibilityElement()
            .accessibilityLabel(testi.descrizioneEmiciclo(conteggi: stato.conteggi))

            if let gruppo = stato.filtroGruppo {
                Text(testi.inEvidenza(gruppo: gruppo)).font(.caption.weight(.medium)).foregroundStyle(Colori.primario)
            }

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), alignment: .leading)], alignment: .leading, spacing: 6) {
                ForEach(stato.conteggi, id: \.tipo) { c in
                    VoceLegenda(colore: Colori.seggio(c.tipo), etichetta: c.tipo.etichetta, valore: "\(c.numero)")
                }
            }
        }
        .padding(.vertical, 6)
    }
}

private struct RigaGruppo: View {
    let g: RipartizioneGruppo

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(g.gruppo).font(.callout.weight(.semibold)).lineLimit(1)
                Spacer()
                if let linea = g.votoGruppo {
                    Text(linea.etichetta).font(.caption.weight(.semibold)).foregroundStyle(Colori.per(linea))
                }
            }
            BarraSegmenti(
                valori: [
                    (Int(g.favorevoli), Colori.favorevole),
                    (Int(g.astenuti), Colori.astenuto),
                    (Int(g.contrari), Colori.contrario),
                    (Int(g.altri), Colori.altro),
                ],
                altezza: 8
            )
            HStack(alignment: .top) {
                Statistica(etichetta: testi.ribelli, valore: g.ribelliLabel)
                Statistica(etichetta: testi.nonPartecipanti, valore: g.nonPartecipantiLabel)
                Statistica(etichetta: testi.compattezza, valore: g.compattezzaLabel)
            }
        }
        .contentShape(Rectangle())
    }
}

private struct Statistica: View {
    let etichetta: String
    let valore: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(etichetta).font(.caption2).foregroundStyle(.secondary)
            Text(valore).font(.caption.weight(.semibold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct RigaVoto: View {
    let voto: VotoIndividuale
    let ribelle: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(voto.nome).font(.callout)
                Text(voto.gruppo).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            if ribelle { Etichetta(testo: testi.ribelle) }
            Text(voto.voto.etichetta)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Colori.per(voto.voto))
        }
    }
}

/// Gli assenti dei gruppi schierati con chi ha perso avrebbero potuto ribaltare l'esito.
private struct RiquadroAssenze: View {
    let assenze: AssenzeDecisive
    let ramo: Ramo

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(testi.assenzeDecisive, systemImage: "exclamationmark.triangle")
                .font(.headline)
                .foregroundStyle(Colori.contrario)
            Text(testi.spiegazioneAssenze(assenze: assenze, ramo: ramo))
                .font(.callout)
            Text(testi.assentiPerGruppo(assenze: assenze))
                .font(.caption.weight(.semibold))
            if !assenze.decisiviDaSoli.isEmpty {
                Text(testi.decisiviDaSoli(gruppi: assenze.decisiviDaSoli))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

