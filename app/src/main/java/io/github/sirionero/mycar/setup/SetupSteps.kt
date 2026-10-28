package io.github.sirionero.mycar.setup

/**
 * The steps of setting up myCar, worked out from how the phone is right now: what's done gets a
 * tick, what's left is a step to do, and looking never undoes anything. The same list serves the
 * first run and "Take me back to the initial setup". No Android here ([SetupSignals] reads the phone).
 */
object SetupSteps {

    enum class Id { CERTIFICATE, MYCAR_ON, ANDROID_AUTO, PERMISSIONS, MESSAGES, CALENDAR, NOTIFICATIONS, SCREENING }

    /** Needed to connect at all; for calls, map and music in the car; and the rest, all optional. */
    enum class Group { CONNECT, DRIVE, MORE }

    enum class Status { DONE, TODO, SKIPPED, BLOCKED, HIDDEN }

    /** Google's Android Auto on the phone: not there, there but disabled, or there and on. */
    enum class AndroidAuto { NONE, OFF, ON }

    data class Signals(
        val hasCertificate: Boolean,
        val myCarOn: Boolean,
        val androidAuto: AndroidAuto,
        /** The driver keeps Android Auto, and picks myCar when Android asks which app to open. */
        val keepAndroidAuto: Boolean,
        /** The permission groups for the drive still off, by name ("Location", "Microphone"...). */
        val driveMissing: List<String>,
        /** Of those, the ones Android won't ask for again. */
        val driveBlocked: List<String>,
        val messages: Boolean,
        val calendar: Boolean,
        val notifications: Boolean,
        val screening: Boolean,
        /** Passed over by the driver ("Done" with something left): not offered first again. */
        val skipped: Set<Id> = emptySet(),
    )

    data class Step(val id: Id, val group: Group, val status: Status)

    fun compute(s: Signals): List<Step> = listOf(
        Step(Id.CERTIFICATE, Group.CONNECT, if (s.hasCertificate) Status.DONE else Status.TODO),
        // Only when it's switched off in Settings: nothing to do otherwise.
        Step(Id.MYCAR_ON, Group.CONNECT, if (s.myCarOn) Status.HIDDEN else Status.TODO),
        Step(
            Id.ANDROID_AUTO,
            Group.CONNECT,
            when {
                s.androidAuto == AndroidAuto.NONE -> Status.HIDDEN
                s.androidAuto == AndroidAuto.OFF || s.keepAndroidAuto -> Status.DONE
                else -> Status.TODO
            },
        ),
        Step(
            Id.PERMISSIONS,
            Group.DRIVE,
            when {
                s.driveMissing.isEmpty() -> Status.DONE
                s.driveBlocked.isNotEmpty() -> Status.BLOCKED
                Id.PERMISSIONS in s.skipped -> Status.SKIPPED
                else -> Status.TODO
            },
        ),
        Step(Id.MESSAGES, Group.MORE, optional(s.messages)),
        Step(Id.CALENDAR, Group.MORE, optional(s.calendar)),
        Step(Id.NOTIFICATIONS, Group.MORE, optional(s.notifications)),
        Step(Id.SCREENING, Group.MORE, optional(s.screening)),
    )

    private fun optional(done: Boolean) = if (done) Status.DONE else Status.TODO

    /** The step the big button does next: what the car needs first, then calls, map and music. */
    fun next(steps: List<Step>): Step? =
        steps.firstOrNull { it.group == Group.CONNECT && it.status == Status.TODO }
            ?: steps.firstOrNull { it.group == Group.DRIVE && it.status == Status.TODO }

    /** What's still needed before myCar can connect to a car at all. */
    fun leftToConnect(steps: List<Step>): List<Step> =
        steps.filter { it.group == Group.CONNECT && it.status == Status.TODO }

    /** The optional ones left, for "More (N)". */
    fun moreLeft(steps: List<Step>): Int = steps.count { it.group == Group.MORE && it.status == Status.TODO }
}
