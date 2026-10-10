import SwiftUI
import Shared

/// I colori delle due parti, uguali nell'intestazione e nelle barre.
private enum ColoriVersus {
    static let a = Colori.primario
    static let b = Colori.suTerziario
}

struct VersusView: View {
    @ObservedObject var osservato: Osservato<VersusState>
    private var store: VersusStore { osservato.store as! VersusStore }

    var body: some View {
        let stato = osservato.stato
        List {
            Section {
                HStack(spacing: 8) {
                    SceltaParte(
                        legislatura: stato.legislaturaA,
                        disponibili: stato.disponibili,
                        colore: ColoriVersus.a
                    ) { store.scegliA(numero: $0) }
                    Button { store.inverti() } label: {
                        Image(systemName: "arrow.left.arrow.right")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(testi.inverti)
                    SceltaParte(
                        legislatura: stato.legislaturaB,
                        disponibili: stato.disponibili,
                        colore: ColoriVersus.b
                    ) { store.scegliB(numero: $0) }
                }
                Toggle(testi.perAnno, isOn: Binding(
                    get: { stato.perAnno },
                    set: { store.mostraPerAnno(attivo: $0) }
                ))
                .tint(Colori.primario)
                if stato.caricamento {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(testi.attesaVersus).font(.footnote).foregroundStyle(.secondary)
                    }
                } else if stato.errore {
                    HStack {
                        Text(testi.erroreVersus).font(.footnote).foregroundStyle(.red)
                        Spacer()
                        Button(testi.riprova) { store.ricarica() }
                    }
                }
            }
            ForEach(stato.sezioni, id: \.titolo) { sezione in
                Section(sezione.titolo) {
                    ForEach(sezione.voci, id: \.etichetta) { voce in
                        RigaVersus(voce: voce, caricamento: stato.caricamento)
                    }
                }
            }
            Text(testi.notaVersus)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
        }
        .listStyle(.insetGrouped)
        .navigationTitle(testi.versus)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { PulsanteTema() } }
    }
}

/// Una delle due legislature: numero romano grande, periodo sotto, menu per cambiarla.
private struct SceltaParte: View {
    let legislatura: Legislatura?
    let disponibili: [Legislatura]
    let colore: Color
    let onScegli: (Int32) -> Void

    var body: some View {
        Menu {
            ForEach(disponibili, id: \.numero) { l in
                Button {
                    onScegli(l.numero)
                } label: {
                    if l.numero == legislatura?.numero {
                        Label([l.etichetta, l.periodo].compactMap { $0 }.joined(separator: " · "), systemImage: "checkmark")
                    } else {
                        Text([l.etichetta, l.periodo].compactMap { $0 }.joined(separator: " · "))
                    }
                }
            }
        } label: {
            VStack(spacing: 4) {
                Capsule().fill(colore).frame(width: 24, height: 4)
                Text(legislatura?.romano ?? "…")
                    .font(.title.weight(.semibold))
                    .foregroundStyle(colore)
                Text(legislatura?.periodo ?? testi.sceltaLegislatura)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// Etichetta al centro, i due valori ai lati, sotto le barre che partono dal centro.
private struct RigaVersus: View {
    let voce: VoceVersus
    let caricamento: Bool

    var body: some View {
        let segnaposto = caricamento ? "…" : "—"
        VStack(spacing: 6) {
            Text(voce.etichetta)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
            HStack(alignment: .top) {
                valore(voce.a ?? segnaposto, prevale: voce.prevaleA)
                    .frame(maxWidth: .infinity, alignment: .leading)
                valore(voce.b ?? segnaposto, prevale: voce.prevaleB)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            if let qa = voce.quotaA, let qb = voce.quotaB {
                HStack(spacing: 4) {
                    Barra(quota: CGFloat(qa.floatValue), colore: ColoriVersus.a, daDestra: true)
                    Barra(quota: CGFloat(qb.floatValue), colore: ColoriVersus.b, daDestra: false)
                }
                .frame(height: 8)
            }
        }
        .padding(.vertical, 4)
    }

    private func valore(_ testo: String, prevale: Bool) -> some View {
        Text(testo)
            .font(testo.count > 14 ? .callout : .title3)
            .fontWeight(prevale ? .bold : .regular)
            .foregroundStyle(prevale ? .primary : .secondary)
    }
}

/// Mezza barra: quella di sinistra cresce verso sinistra, quella di destra verso destra.
private struct Barra: View {
    let quota: CGFloat
    let colore: Color
    let daDestra: Bool

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: daDestra ? .trailing : .leading) {
                Capsule().fill(Color.secondary.opacity(0.15))
                Capsule().fill(colore).frame(width: geo.size.width * min(max(quota, 0), 1))
            }
        }
        .animation(.easeOut, value: quota)
    }
}
