package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.update.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test fun parsesTagPrefix() = assertEquals(listOf(1, 2, 3), SemVer.parse("v1.2.3"))
    @Test fun ignoresSuffix() = assertEquals(listOf(1, 2, 0), SemVer.parse("1.2.0-beta+5"))
    @Test fun newerPatch() = assertTrue(SemVer.isNewer("1.0.1", "1.0.0"))
    @Test fun newerMinorBeatsPatch() = assertTrue(SemVer.isNewer("1.1", "1.0.9"))
    @Test fun sameVersionIsNotNewer() = assertFalse(SemVer.isNewer("v1.0.0", "1.0.0"))
    @Test fun olderIsNotNewer() = assertFalse(SemVer.isNewer("0.9.9", "1.0.0"))
}
