package com.stremioshell.host.compose

import androidx.lifecycle.LifecycleCoroutineScope
import com.stremioshell.host.core.CoreRuntimeClient
import com.stremioshell.host.core.repo.AddonsRepository
import com.stremioshell.host.core.repo.AppRepositories
import com.stremioshell.host.core.repo.CatalogRepository
import com.stremioshell.host.core.repo.LibraryRepository
import com.stremioshell.host.core.repo.MetaRepository
import com.stremioshell.host.core.repo.PlaybackRepository
import com.stremioshell.host.core.repo.SearchRepository
import com.stremioshell.host.core.repo.SessionRepository
import com.stremioshell.host.core.repo.SettingsRepository

class AppContainer(
  val runtimeClient: CoreRuntimeClient,
  val repositories: AppRepositories,
  val diagnosticsStore: DiagnosticsStore,
  val backPolicyManager: BackPolicyManager
) {
  companion object {
    fun create(
      runtimeClient: CoreRuntimeClient,
      lifecycleScope: LifecycleCoroutineScope,
      diagnosticsStore: DiagnosticsStore = DiagnosticsStore(),
      backPolicyManager: BackPolicyManager = BackPolicyManager()
    ): AppContainer {
      val repositories = AppRepositories(
        session = SessionRepository(runtimeClient, lifecycleScope),
        catalog = CatalogRepository(runtimeClient, lifecycleScope),
        meta = MetaRepository(runtimeClient, lifecycleScope),
        search = SearchRepository(runtimeClient, lifecycleScope),
        library = LibraryRepository(runtimeClient, lifecycleScope),
        addons = AddonsRepository(runtimeClient, lifecycleScope),
        settings = SettingsRepository(runtimeClient, lifecycleScope),
        playback = PlaybackRepository(runtimeClient, lifecycleScope)
      )

      return AppContainer(
        runtimeClient = runtimeClient,
        repositories = repositories,
        diagnosticsStore = diagnosticsStore,
        backPolicyManager = backPolicyManager
      )
    }
  }
}
