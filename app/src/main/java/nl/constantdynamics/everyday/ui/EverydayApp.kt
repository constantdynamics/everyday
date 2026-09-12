package nl.constantdynamics.everyday.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.ui.galerij.GalerijScherm
import nl.constantdynamics.everyday.ui.opname.OpnameScherm
import nl.constantdynamics.everyday.ui.start.StartScherm

object Routes {
    const val START = "start"
    const val SERIE_ID = "serieId"
    const val OPNAME = "serie/{$SERIE_ID}/opname"
    const val GALERIJ = "serie/{$SERIE_ID}/galerij"

    fun opname(serieId: Long) = "serie/$serieId/opname"
    fun galerij(serieId: Long) = "serie/$serieId/galerij"
}

@Composable
fun EverydayApp(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.START) {
        composable(Routes.START) {
            StartScherm(
                container = container,
                naarOpname = { navController.navigate(Routes.opname(it)) },
                naarGalerij = { navController.navigate(Routes.galerij(it)) },
            )
        }
        composable(
            route = Routes.OPNAME,
            arguments = listOf(navArgument(Routes.SERIE_ID) { type = NavType.LongType }),
        ) { ingang ->
            OpnameScherm(
                container = container,
                serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L,
                terug = { navController.popBackStack() },
                naarGalerij = { navController.navigate(Routes.galerij(it)) },
            )
        }
        composable(
            route = Routes.GALERIJ,
            arguments = listOf(navArgument(Routes.SERIE_ID) { type = NavType.LongType }),
        ) { ingang ->
            GalerijScherm(
                container = container,
                serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L,
                terug = { navController.popBackStack() },
                naarOpname = { navController.navigate(Routes.opname(it)) },
            )
        }
    }
}
