package com.stremioshell.host.update

data class SemVer(
  val major: Int,
  val minor: Int,
  val patch: Int
) : Comparable<SemVer> {
  override fun compareTo(other: SemVer): Int {
    if (major != other.major) return major.compareTo(other.major)
    if (minor != other.minor) return minor.compareTo(other.minor)
    return patch.compareTo(other.patch)
  }

  companion object {
    private val VERSION_RE = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?.*$""")

    fun parseOrNull(raw: String): SemVer? {
      val input = raw.trim()
      val match = VERSION_RE.matchEntire(input) ?: return null
      val major = match.groupValues[1].toIntOrNull() ?: return null
      val minor = match.groupValues[2].toIntOrNull() ?: 0
      val patch = match.groupValues[3].toIntOrNull() ?: 0
      return SemVer(major = major, minor = minor, patch = patch)
    }
  }
}
