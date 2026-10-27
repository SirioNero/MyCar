package io.github.sirionero.mycar.xonos

import android.content.Context
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.xonos.data.Connection
import io.github.sirionero.mycar.xonos.data.MaException
import io.github.sirionero.mycar.xonos.data.Problem

/**
 * Che cosa non va, detto con le parole di Xonos nella lingua di [context]. Un Music Assistant che non
 * risponde a casa, con Android 17 che tiene myCar fuori dalla rete di casa, è quello: lo si dice.
 */
fun describe(context: Context, problem: Problem, detail: String?): String =
    if (problem in UNREACHABLE && LocalNetwork.blocks(context, Xonos.settings.load().urls)) LocalNetwork.HINT
    else when (problem) {
    Problem.BAD_ADDRESS -> context.getString(R.string.xonos_problem_bad_address, detail.orEmpty())
    Problem.NO_ANSWER -> if (detail != null) context.getString(R.string.xonos_problem_no_answer_from, detail) else context.getString(R.string.xonos_problem_no_answer)
    Problem.NOT_MUSIC_ASSISTANT -> context.getString(R.string.xonos_problem_not_ma)
    Problem.TOO_OLD -> context.getString(R.string.xonos_problem_too_old, detail.orEmpty())
    Problem.BAD_TOKEN -> context.getString(R.string.xonos_problem_bad_token)
    Problem.DENIED -> context.getString(R.string.xonos_problem_denied)
    Problem.HTTP -> context.getString(R.string.xonos_problem_http, detail.orEmpty())
    Problem.NETWORK -> if (detail != null) context.getString(R.string.xonos_problem_network, detail) else context.getString(R.string.xonos_problem_unreachable)
    Problem.LOST -> context.getString(R.string.xonos_problem_lost)
    Problem.NOT_CONNECTED -> context.getString(R.string.xonos_problem_not_connected)
    Problem.BAD_LOGIN -> context.getString(R.string.xonos_problem_bad_login)
    Problem.SETUP_REQUIRED -> context.getString(R.string.xonos_problem_setup_required)
    Problem.REFUSED -> if (detail != null) context.getString(R.string.xonos_problem_refused, detail) else context.getString(R.string.xonos_error_unknown)
}

private val UNREACHABLE = setOf(Problem.NO_ANSWER, Problem.NETWORK, Problem.NOT_CONNECTED)

/** Un errore qualsiasi: quelli di Music Assistant con le parole di Xonos, gli altri come vengono. */
fun describe(context: Context, error: Throwable): String = when (error) {
    is MaException -> describe(context, error.problem, error.detail)
    else -> error.message ?: context.getString(R.string.xonos_error_unknown)
}

/** Il problema da mostrare per lo stato della connessione; null se va tutto bene. */
fun describe(context: Context, connection: Connection): String? = when (connection) {
    Connection.NotConfigured -> context.getString(R.string.xonos_problem_not_configured)
    is Connection.Failed -> describe(context, connection.problem, connection.detail)
    is Connection.Connecting, is Connection.Connected -> null
}
