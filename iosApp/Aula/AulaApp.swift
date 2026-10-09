import SwiftUI
import Shared

@main
struct AulaApp: App {
    init() {
        #if DEBUG
        AppGraph.shared.logChiamate = true
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RadiceView()
                .tint(Colori.primario)
        }
    }
}

struct RadiceView: View {
    // Gli store delle tab vivono quanto l'app.
    @StateObject private var aula = Osservato<AulaState>(AppGraph.shared.aulaStore())
    @StateObject private var parlamentari = Osservato<ParlamentariState>(AppGraph.shared.parlamentariStore())
    @StateObject private var gruppi = Osservato<GruppiState>(AppGraph.shared.gruppiStore())
    @AppStorage(SceltaTema.chiave) private var tema: SceltaTema = .sistema

    var body: some View {
        TabView {
            NavigationStack {
                AulaView(osservato: aula)
                    .navigationDestination(for: Votazione.self) { VotazioneView(votazione: $0) }
                    .navigationDestination(for: Parlamentare.self) { ParlamentareView(parlamentare: $0) }
                    .navigationDestination(for: Atto.self) { SchedaAttoView(atto: $0) }
            }
            .tabItem { Label(testi.aula, systemImage: "checkmark.rectangle.stack") }

            NavigationStack {
                ParlamentariView(osservato: parlamentari)
                    .navigationDestination(for: Parlamentare.self) { ParlamentareView(parlamentare: $0) }
            }
            .tabItem { Label(testi.parlamentari, systemImage: "person.3") }

            NavigationStack {
                GruppiView(osservato: gruppi)
                    .navigationDestination(for: GruppoParlamentare.self) { GruppoView(gruppo: $0) }
                    .navigationDestination(for: Parlamentare.self) { ParlamentareView(parlamentare: $0) }
            }
            .tabItem { Label(testi.gruppi, systemImage: "chart.pie") }
        }
        .onAppear { tema.applica() }
        .onChange(of: tema) { _, nuovo in nuovo.applica() }
    }
}
