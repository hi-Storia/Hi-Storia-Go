package it.historia.player

import android.app.Application
import android.util.Log
import it.historia.player.data.project.ProjectTransferManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Prima che qualsiasi schermata legga gli store: completa il rollback di
        // un'importazione di progetto interrotta e rimuove le aree temporanee rimaste.
        runCatching { ProjectTransferManager.create(this).recoverInterruptedImport() }
            .onSuccess { recovered ->
                if (recovered) Log.w(TAG, "Importazione interrotta: progetto precedente ripristinato")
            }
            .onFailure { error -> Log.e(TAG, "Ripristino importazione non riuscito", error) }
    }

    private companion object {
        const val TAG = "HiStoriaGo"
    }
}
