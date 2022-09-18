import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentFilter
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream

plugins {
    kotlin("jvm") version "1.7.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
    `maven-publish`
}

group = "com.github.ekenstein"
version = "0.1.1"
val kotlinJvmTarget = "1.8"
val junitVersion by extra("5.9.0")
val kotlinVersion by extra("1.7.10")

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.github.microutils", "kotlin-logging-jvm", "2.1.23")
    implementation("ch.qos.logback", "logback-classic", "1.4.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

tasks {
    dependencyUpdates {
        rejectVersionIf {
            UpgradeToUnstableFilter().reject(this) || IgnoredDependencyFilter().reject(this)
        }
    }

    val dependencyUpdateSentinel = register<DependencyUpdateSentinel>("dependencyUpdateSentinel", buildDir)
    dependencyUpdateSentinel.configure {
        dependsOn(dependencyUpdates)
    }

    withType<KotlinCompile>() {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = kotlinJvmTarget
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }

    compileTestKotlin {
        kotlinOptions {
            jvmTarget = kotlinJvmTarget
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }

    compileJava {
        sourceCompatibility = kotlinJvmTarget
        targetCompatibility = kotlinJvmTarget
    }

    compileTestJava {
        sourceCompatibility = kotlinJvmTarget
        targetCompatibility = kotlinJvmTarget
    }

    check {
        dependsOn(test)
        dependsOn(ktlintCheck)
        dependsOn(dependencyUpdateSentinel)
    }

    test {
        useJUnitPlatform()
    }
}

ktlint {
    version.set("0.45.2")
}

publishing {
    publications {
        create<MavenPublication>("ktgtp") {
            groupId = project.group.toString()
            artifactId = "ktgtp"
            version = project.version.toString()
            from(components["kotlin"])
            artifact(tasks.kotlinSourcesJar)

            pom {
                name.set("ktgtp")
                description.set("GTP engine communication DSL")
                url.set("https://github.com/Ekenstein/ktgtp")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/Ekenstein/ktgtp/blob/main/LICENSE")
                    }
                }
            }
        }
    }
}

class IgnoredDependencyFilter : ComponentFilter {
    private val ignoredDependencies = mapOf(
            "ktlint" to listOf("0.46.0", "0.46.1", "0.46.2", "0.47.0", "0.47.1") // doesn't currently work.
    )

    override fun reject(p0: ComponentSelectionWithCurrent): Boolean {
        return ignoredDependencies[p0.candidate.module].orEmpty().contains(p0.candidate.version)
    }
}

class UpgradeToUnstableFilter : ComponentFilter {
    override fun reject(cs: ComponentSelectionWithCurrent) = reject(cs.currentVersion, cs.candidate.version)

    private fun reject(old: String, new: String): Boolean {
        return !isStable(new) && isStable(old) // no unstable proposals for stable dependencies
    }

    private fun isStable(version: String): Boolean {
        val stableKeyword = setOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
        val stablePattern = version.matches(Regex("""^[0-9,.v-]+(-r)?$"""))
        return stableKeyword || stablePattern
    }
}

abstract class DependencyUpdateSentinel @Inject constructor(private val buildDir: File) : DefaultTask() {
    @ExperimentalPathApi
    @TaskAction
    fun check() {
        val updateIndicator = "The following dependencies have later milestone versions:"
        val report = Paths.get(buildDir.toString(), "dependencyUpdates", "report.txt")

        report.inputStream().bufferedReader().use { reader ->
            if (reader.lines().anyMatch { it == updateIndicator }) {
                throw GradleException("Dependency updates are available.")
            }
        }
    }
}
