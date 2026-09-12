package nl.constantdynamics.everyday.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.constantdynamics.everyday.AppContainer
import nl.constantdynamics.everyday.ui.bewerken.BewerkScherm
import nl.constantdynamics.everyday.ui.dag.DagScherm
import nl.constantdynamics.everyday.ui.galerij.GalerijScherm
import nl.constantdynamics.everyday.ui.importeren.ImportScherm
import nl.constantdynamics.everyday.ui.instellingen.InstellingenScherm
import nl.constantdynamics.everyday.ui.opname.OpnameScherm
import nl.constantdynamics.everyday.ui.start.StartScherm
import nl.constantdynamics.everyday.ui.timelapse.TimelapseScherm
import java.time.LocalDate

object Routes {
    const val START = "start"
    const val SERIE_ID = "serieId"
    const val DAG = "dag"
    const val OPNAME = "serie/{$SERIE_ID}/opname"
    const val GALERIJ = "serie/{$SERIE_ID}/galerij"
    const val IMPORTEREN = "serie/{$SERIE_ID}/importeren"
    const val TIMELAPSE = "serie/{$SERIE_ID}/timelapse"
    const val DAGDETAIL = "serie/{$SERIE_ID}/dag/{$DAG}"
    const val INSTELLINGEN = "instellingen"
    const val FOTO_ID = "fotoId"
    const val BEWERKEN = "foto/{$FOTO_ID}/bewerken"

    fun opname(serieId: Long) = "serie/$serieId/opname"
    fun galerij(serieId: Long) = "serie/$serieId/galerij"
    fun importeren(serieId: Long) = "serie/$serieId/importeren"
    fun timelapse(serieId: Long) = "serie/$serieId/timelapse"
    fun dagdetail(serieId: Long, dag: LocalDate) = "serie/$serieId/dag/$dag"
    fun bewerken(fotoId: Long) = "foto/$fotoId/bewerken"
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
                naarInstellingen = { navController.navigate(Routes.INSTELLINGEN) },
            )
        }
        composable(Routes.INSTELLINGEN) {
            InstellingenScherm(
                container = container,
                terug = { navController.popBackStack() },
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
            val serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L
            GalerijScherm(
                container = container,
                serieId = serieId,
                terug = { navController.popBackStack() },
                naarOpname = { navController.navigate(Routes.opname(it)) },
                naarDag = { dag -> navController.navigate(Routes.dagdetail(serieId, dag)) },
                naarImporteren = { navController.navigate(Routes.importeren(serieId)) },
                naarTimelapse = { navController.navigate(Routes.timelapse(serieId)) },
            )
        }
        composable(
            route = Routes.TIMELAPSE,
            arguments = listOf(navArgument(Routes.SERIE_ID) { type = NavType.LongType }),
        ) { ingang ->
            TimelapseScherm(
                container = container,
                serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L,
                terug = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.IMPORTEREN,
            arguments = listOf(navArgument(Routes.SERIE_ID) { type = NavType.LongType }),
        ) { ingang ->
            ImportScherm(
                container = container,
                serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L,
                terug = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.DAGDETAIL,
            arguments = listOf(
                navArgument(Routes.SERIE_ID) { type = NavType.LongType },
                navArgument(Routes.DAG) { type = NavType.StringType },
            ),
        ) { ingang ->
            val serieId = ingang.arguments?.getLong(Routes.SERIE_ID) ?: 0L
            val dagTekst = ingang.arguments?.getString(Routes.DAG)
            val dag = runCatching { LocalDate.parse(dagTekst) }.getOrNull()
            if (dag != null) {
                DagScherm(
                    container = container,
                    serieId = serieId,
                    dag = dag,
                    terug = { navController.popBackStack() },
                    naarOpname = { navController.navigate(Routes.opname(it)) },
                    naarBewerken = { fotoId -> navController.navigate(Routes.bewerken(fotoId)) },
                )
            }
        }
        composable(
            route = Routes.BEWERKEN,
            arguments = listOf(navArgument(Routes.FOTO_ID) { type = NavType.LongType }),
        ) { ingang ->
            BewerkScherm(
                container = container,
                fotoId = ingang.arguments?.getLong(Routes.FOTO_ID) ?: 0L,
                terug = { navController.popBackStack() },
            )
        }
    }
}
