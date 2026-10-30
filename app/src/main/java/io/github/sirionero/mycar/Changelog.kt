package io.github.sirionero.mycar

import androidx.annotation.ArrayRes

/**
 * What changed in each release, newest first, shown on the car screen after an update (and on
 * the phone). Only the changes a driver would notice. Plain words, short lines: this is read in
 * a parked car. The lines are in res/values/strings_changelog.xml, one array per release, and
 * translated like every other text.
 */
object Changelog {

    class Release(val versionCode: Int, val versionName: String, @ArrayRes private val notes: Int) {
        /** The changes in myCar's language. */
        val changes: List<String> get() = Words.resources.getStringArray(notes).toList()
    }

    val releases = listOf(
        Release(39, "0.6.3-alpha", R.array.changelog_39),
        Release(38, "0.6.2-alpha", R.array.changelog_38),
        Release(37, "0.6.1-alpha", R.array.changelog_37),
        Release(36, "0.6.0-alpha", R.array.changelog_36),
        Release(35, "0.5.22-alpha", R.array.changelog_35),
        Release(34, "0.5.21-alpha", R.array.changelog_34),
        Release(33, "0.5.20-alpha", R.array.changelog_33),
        Release(32, "0.5.19-alpha", R.array.changelog_32),
        Release(31, "0.5.18-alpha", R.array.changelog_31),
        Release(30, "0.5.17-alpha", R.array.changelog_30),
        Release(29, "0.5.16-alpha", R.array.changelog_29),
        Release(28, "0.5.15-alpha", R.array.changelog_28),
        Release(27, "0.5.14-alpha", R.array.changelog_27),
        Release(26, "0.5.13-alpha", R.array.changelog_26),
        Release(25, "0.5.12-alpha", R.array.changelog_25),
        Release(24, "0.5.11-alpha", R.array.changelog_24),
        Release(23, "0.5.10-alpha", R.array.changelog_23),
        Release(22, "0.5.09-alpha", R.array.changelog_22),
        Release(21, "0.5.08-alpha", R.array.changelog_21),
        Release(20, "0.5.07-alpha", R.array.changelog_20),
        Release(19, "0.5.06-alpha", R.array.changelog_19),
        Release(18, "0.5.05-alpha", R.array.changelog_18),
        Release(17, "0.5.04-alpha", R.array.changelog_17),
        Release(16, "0.5.03-alpha", R.array.changelog_16),
        Release(15, "0.5.02-alpha", R.array.changelog_15),
        Release(14, "0.5.01-alpha", R.array.changelog_14),
        Release(13, "0.5.00-alpha", R.array.changelog_13),
        Release(12, "0.4.00-alpha", R.array.changelog_12),
        Release(11, "0.3.02-alpha", R.array.changelog_11),
        Release(9, "0.3.00-alpha", R.array.changelog_9),
        Release(8, "0.2.00-alpha", R.array.changelog_8),
        Release(7, "0.1.10-alpha", R.array.changelog_7),
        Release(6, "0.1.0-alpha", R.array.changelog_6),
    )

    /** The release that matches this build, if it has notes. */
    val current: Release? get() = releases.firstOrNull { it.versionCode == BuildConfig.VERSION_CODE }
}
