package io.github.sirionero.mycar.xonos

import android.content.Context
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.xonos.data.Connection
import io.github.sirionero.mycar.xonos.data.MaException
import io.github.sirionero.mycar.xonos.data.Problem

/**
 * Che cosa non va, detto con le parole di Xonos nella lingua di [context]. Un Music Assistant che non
 * risponde perché Tailscale lascia fuori myCar, o perché Android 17 tiene myCar fuori dalla rete di
 * casa, è quello: lo si dice (Tailnet.why).
 */
fun describe(context: Context, problem: Problem, detail: String?): String =
    Tailnet.why(context, Xonos.settings.load().urls, networkFailure(problem, detail), plainly(context, problem, detail))

private fun plainly(context: Context, problem: Problem, detail: String?): String = when (problem) {
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

/** Come è andato storto il collegamento, per quel che riguarda la rete; OTHER se la rete non c'entra. */
fun networkFailure(problem: Problem, detail: String?): Tailnet.Failure = when (problem) {
    Problem.NO_ANSWER -> Tailnet.Failure.TIMEOUT
    Problem.NETWORK, Problem.NOT_CONNECTED ->
        Tailnet.failureOf(detail).let { if (it == Tailnet.Failure.OTHER) Tailnet.Failure.UNREACHABLE else it }
    else -> Tailnet.Failure.OTHER
}

fun networkFailure(error: Throwable): Tailnet.Failure =
    if (error is MaException) networkFailure(error.problem, error.detail) else Tailnet.failureOf(error)

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
