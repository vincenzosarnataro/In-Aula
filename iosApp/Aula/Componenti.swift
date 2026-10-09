import SwiftUI
import Shared

/// I testi dell'interfaccia, condivisi con Android (`Testi.kt` nel modulo shared).
let testi = Testi.shared

enum Colori {
    /// Blu istituzionale, lo stesso primario dello schema Material di Android:
    /// tono 40 in chiaro, tono 80 in scuro.
    static let primario = Color(chiaro: 0x285EA7, scuro: 0xA9C7FF)
    /// Bronzo delle etichette (Fiducia, Segreta…): tertiaryContainer / onTertiaryContainer di Android.
    static let terziario = Color(chiaro: 0xFFDCC0, scuro: 0x683D0E)
    static let suTerziario = Color(chiaro: 0x683D0E, scuro: 0xFFDCC0)
    static let favorevole = Color(red: 0.18, green: 0.55, blue: 0.34)
    static let contrario = Color(red: 0.75, green: 0.22, blue: 0.17)
    static let astenuto = Color(red: 0.83, green: 0.63, blue: 0.09)
    static let altro = Color(red: 0x9A / 255, green: 0x9E / 255, blue: 0xA8 / 255)

    static func per(_ tipo: TipoVoto) -> Color {
        switch tipo {
        case .favorevole: return favorevole
        case .contrario: return contrario
        case .astenuto: return astenuto
        default: return altro
        }
    }

    /// Nell'emiciclo chi non era in Aula resta visibile ma attenuato.
    static func seggio(_ tipo: TipoVoto) -> Color {
        tipo.presente ? per(tipo) : altro.opacity(0.3)
    }
}

/// Emiciclo a pallini, uno per parlamentare, colorati secondo il voto. Se è selezionato
/// un gruppo, gli altri seggi si attenuano. Il contenuto viene posato al centro dell'arco.
struct EmicicloVoti<Centro: View>: View {
    let seggi: [VotoIndividuale]
    let disposizione: Disposizione
    var gruppoEvidenziato: String? = nil
    @ViewBuilder var centro: () -> Centro

    var body: some View {
        Canvas { ctx, size in
            for (s, voto) in zip(disposizione.seggi, seggi) {
                let r = s.raggio * size.width
                let rect = CGRect(x: s.x * size.width - r, y: s.y * size.height - r, width: 2 * r, height: 2 * r)
                let attenuato = gruppoEvidenziato != nil && voto.gruppo != gruppoEvidenziato
                ctx.opacity = attenuato ? 0.15 : 1
                ctx.fill(Path(ellipseIn: rect), with: .color(Colori.seggio(voto.voto)))
            }
        }
        .aspectRatio(disposizione.proporzione, contentMode: .fit)
        .overlay(alignment: .bottom) { centro() }
    }
}

extension Color {
    /// Colore che segue la modalità chiara/scura del sistema, da valori esadecimali RGB.
    init(chiaro: UInt32, scuro: UInt32) {
        func rgb(_ hex: UInt32) -> UIColor {
            UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        }
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? rgb(scuro) : rgb(chiaro) })
    }
}

/// Barra impilata con segmenti proporzionali e 2pt di stacco solo tra un segmento e l'altro.
struct BarraSegmenti: View {
    let valori: [(Int, Color)]
    var altezza: CGFloat = 10

    var body: some View {
        let totale = max(valori.map(\.0).reduce(0, +), 1)
        let segmenti = valori.filter { $0.0 > 0 }
        GeometryReader { geo in
            // Lo stacco va solo tra un segmento e l'altro: l'ultimo arriva fino al bordo.
            let utile = geo.size.width - 2 * CGFloat(max(segmenti.count - 1, 0))
            HStack(spacing: 2) {
                ForEach(Array(segmenti.enumerated()), id: \.offset) { _, voce in
                    Rectangle()
                        .fill(voce.1)
                        .frame(width: max(utile * CGFloat(voce.0) / CGFloat(totale), 1))
                }
                Spacer(minLength: 0)
            }
        }
        .frame(height: altezza)
        .background(Color(.systemGray5))
        .clipShape(Capsule())
    }
}

struct BarraVoti: View {
    let favorevoli: Int
    let contrari: Int
    let astenuti: Int
    var altezza: CGFloat = 10

    var body: some View {
        BarraSegmenti(
            valori: [(favorevoli, Colori.favorevole), (contrari, Colori.contrario), (astenuti, Colori.astenuto)],
            altezza: altezza
        )
    }
}

struct VoceLegenda: View {
    let colore: Color
    let etichetta: String
    let valore: String

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(colore).frame(width: 9, height: 9)
            Text(etichetta).font(.caption)
            Text(valore).font(.caption.weight(.semibold))
        }
    }
}

struct ChipEsito: View {
    let esito: Esito

    var body: some View {
        if esito != .sconosciuto {
            let colore = esito == .approvata ? Colori.favorevole : Colori.contrario
            Text(esito.etichetta)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(colore)
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(colore.opacity(0.14), in: Capsule())
        }
    }
}

struct Etichetta: View {
    let testo: String

    var body: some View {
        Text(testo)
            .font(.caption2)
            .foregroundStyle(Colori.suTerziario)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Colori.terziario, in: Capsule())
    }
}

struct SelettoreRamo: View {
    let ramo: Ramo
    let onCambia: (Ramo) -> Void

    var body: some View {
        Picker(testi.ramo, selection: Binding(get: { ramo }, set: onCambia)) {
            Text(Ramo.camera.etichetta).tag(Ramo.camera)
            Text(Ramo.senato.etichetta).tag(Ramo.senato)
        }
        .pickerStyle(.segmented)
    }
}

struct Avatar: View {
    let fotoUrl: String?
    let iniziali: String
    var dimensione: CGFloat = 44

    var body: some View {
        ZStack {
            Circle().fill(Colori.primario.opacity(0.15))
            Text(iniziali).font(.subheadline.weight(.medium)).foregroundStyle(Colori.primario)
            // Le foto sono pubblicate su http: ATS le bloccherebbe, quindi forziamo https.
            if let foto = fotoUrl, let url = URL(string: foto.replacingOccurrences(of: "http://", with: "https://")) {
                AsyncImage(url: url) { img in
                    img.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
            }
        }
        .frame(width: dimensione, height: dimensione)
        .clipShape(Circle())
    }
}

struct StatoCaricamento: View {
    var messaggio: String? = nil

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
            if let messaggio {
                Text(messaggio)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 220)
        .padding()
    }
}

struct StatoErrore: View {
    let messaggio: String
    let onRiprova: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(testi.erroreTitolo).font(.headline)
            Text(messaggio).font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
            Button(testi.riprova, action: onRiprova).buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 240)
        .padding()
    }
}
