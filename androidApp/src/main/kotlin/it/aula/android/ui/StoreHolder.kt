package it.aula.android.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import it.aula.presentation.Store

/** ViewModel che possiede uno store e lo chiude quando il suo ViewModelStore viene liberato. */
class StoreHolder(val store: Store<*>) : ViewModel() {
    override fun onCleared() = store.close()
}

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Store<*>> rememberTabStore(key: String, factory: () -> T): T {
    val holder: StoreHolder = viewModel(key = key) { StoreHolder(factory()) }
    return holder.store as T
}

/**
 * Store di una schermata di Navigation 3. Con il decoratore dei ViewModel ogni voce del back
 * stack ha il proprio ViewModelStore: lo store vive finché la voce resta nella pila (anche
 * coperta da altre schermate, o durante una rotazione) e si chiude quando viene tolta.
 */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Store<*>> rememberEntryStore(factory: () -> T): T {
    val holder: StoreHolder = viewModel { StoreHolder(factory()) }
    return holder.store as T
}
