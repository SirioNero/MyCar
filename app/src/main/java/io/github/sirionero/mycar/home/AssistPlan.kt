package io.github.sirionero.mycar.home

/** A voice assistant set up in Home Assistant: the [language] its agent speaks, and the [agent]. */
class AssistPipeline(val id: String, val language: String, val agent: String?)

/**
 * How myCar asks Home Assistant's Assist (home/Assist) something the driver said. Without Android,
 * so every case can be tried on the JVM.
 */
sealed interface AssistPlan {
    /** That pipeline, as Home Assistant runs it for its own voice devices. */
    data class Run(val pipeline: String) : AssistPlan

    /** No pipeline speaks the driver's language: its [agent] asked straight, in [language]. */
    data class Process(val agent: String?, val language: String) : AssistPlan

    companion object {
        /**
         * For a driver speaking [language] ("it"): the [preferred] pipeline if it speaks it, else
         * another one that does, as set up there (an Italian one with Claude, say). With none, the
         * preferred pipeline's agent is asked in the driver's language: Home Assistant's own agent
         * knows dozens, while its English pipeline understood no Italian at all.
         */
        fun of(pipelines: List<AssistPipeline>, preferred: String?, language: String): AssistPlan {
            fun speaks(p: AssistPipeline) = p.language.substringBefore('-').equals(language, ignoreCase = true)
            val speaking = pipelines.filter(::speaks)
            (speaking.firstOrNull { it.id == preferred } ?: speaking.firstOrNull())?.let { return Run(it.id) }
            val main = pipelines.firstOrNull { it.id == preferred } ?: pipelines.firstOrNull()
            return Process(main?.agent, language)
        }
    }
}
