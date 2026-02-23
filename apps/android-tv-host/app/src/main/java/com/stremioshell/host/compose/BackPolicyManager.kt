package com.stremioshell.host.compose

import androidx.navigation.NavHostController

class BackPolicyManager {
  enum class BackDecision {
    CLOSE_OVERLAY,
    POP_ROUTE,
    EXIT_APP
  }

  fun resolveDecision(hasOpenOverlay: Boolean, canPopRoute: Boolean): BackDecision {
    return when {
      hasOpenOverlay -> BackDecision.CLOSE_OVERLAY
      canPopRoute -> BackDecision.POP_ROUTE
      else -> BackDecision.EXIT_APP
    }
  }

  fun handleBack(
    navController: NavHostController,
    hasOpenOverlay: Boolean,
    closeOverlay: () -> Unit,
    exitApp: () -> Unit,
    onDecision: (String) -> Unit
  ) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route ?: "unknown"
    when (resolveDecision(hasOpenOverlay, navController.previousBackStackEntry != null)) {
      BackDecision.CLOSE_OVERLAY -> {
        onDecision("overlay.close route=$currentRoute")
        closeOverlay()
      }

      BackDecision.POP_ROUTE -> {
        if (navController.popBackStack()) {
          val nextRoute = navController.currentBackStackEntry?.destination?.route ?: "unknown"
          onDecision("route.back from=$currentRoute to=$nextRoute")
        } else {
          onDecision("app.exit route=$currentRoute popFailed=true")
          exitApp()
        }
      }

      BackDecision.EXIT_APP -> {
        onDecision("app.exit route=$currentRoute")
        exitApp()
      }
    }
  }
}
