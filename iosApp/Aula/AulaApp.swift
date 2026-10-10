import SwiftUI
import Shared

@main
struct AulaApp: App {
    init() {
        #if DEBUG
        AppGraph.shared.logChiamate = true
        #endif
        AppGraph.shared.ripristinaLegislatura(numero: PreferenzaLegislatura.salvata.map { KotlinInt(int: Int32($0)) })
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
    @StateObject private var governo = Osservato<GovernoState>(AppGraph.shared.governoStore())
    @StateObject private var legislatura = Osservato<LegislaturaState>(AppGraph.shared.legislaturaStore())
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

            NavigationStack {
                GovernoView(osservato: governo)
                    .navigationDestination(for: Parlamentare.self) { ParlamentareView(parlamentare: $0) }
            }
            .tabItem { Label(testi.governo, systemImage: "building.columns") }
        }
        .environmentObject(legislatura)
        .onAppear { tema.applica() }
        .onChange(of: tema) { _, nuovo in nuovo.applica() }
    }
}
