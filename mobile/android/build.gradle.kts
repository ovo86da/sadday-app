allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

// Workaround: plugins sin namespace declarado fallan con AGP 9+.
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            if (namespace == null) {
                namespace = project.group.toString()
            }
        }
    }
}

// El módulo :app usa JVM 21 (definido en android/app/build.gradle.kts).
// Los plugins de terceros mantienen su propio Java target (11/17 según cada uno
// hardcoded en sus build.gradle dentro de .pub-cache — no podemos modificarlo
// desde aquí), pero alineamos Kotlin a ese target para evitar "Inconsistent
// JVM Target". El `provider {}` difiere la lectura hasta después de que AGP
// configure el JavaCompile, momento en que ya sabemos el target real.
subprojects {
    if (name != "app") {
        plugins.withId("org.jetbrains.kotlin.android") {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.jvmTarget.set(provider {
                    val javaTarget = project.tasks.withType<JavaCompile>()
                        .firstOrNull()?.targetCompatibility ?: "11"
                    when (javaTarget) {
                        "21" -> org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
                        "17" -> org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                        else -> org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
                    }
                })
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
