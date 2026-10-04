import org.gradle.api.JavaVersion

/** Platform levels shared by every module. Change them here and nowhere else. */
object SiderPlatform {
    /** Android 17 (API 37), the latest stable platform at the time of writing. */
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 29
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17
}
