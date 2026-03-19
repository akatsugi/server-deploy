import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val pluginGroup = providers.gradleProperty("pluginGroup").get()
val pluginName = providers.gradleProperty("pluginName").get()
val pluginId = providers.gradleProperty("pluginId").get()
val pluginVersion = providers.gradleProperty("pluginVersion").get()
val platformType = providers.gradleProperty("platformType").get()
val platformVersion = providers.gradleProperty("platformVersion").get()
val bundledPlugins = providers.gradleProperty("platformBundledPlugins").orElse("").get()
val jvmTarget = providers.gradleProperty("jvmTarget").get().toInt()

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.20")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        create(platformType, platformVersion)
        if (bundledPlugins.isNotBlank()) {
            bundledPlugins(bundledPlugins.split(',').map(String::trim).filter(String::isNotEmpty))
        }
        testFramework(TestFrameworkType.Platform)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmTarget))
    }
}

intellijPlatform {
    projectName = project.name
    buildSearchableOptions = false
    autoReload = true

    pluginConfiguration {
        id = pluginId
        name = pluginName
        version = pluginVersion
        description = "Plugin for server configuration, local-to-remote directory mapping, and right-click upload from the IDEA project tree."
        changeNotes = "Improve settings UI, add mapping action, and support JSON import/export."
        ideaVersion {
            sinceBuild = "241"
        }
        vendor {
            name = "Akatsugi"
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(jvmTarget)
    }

    test {
        useJUnitPlatform()
    }

    runIde {
        jvmArgs("-Dfile.encoding=UTF-8")
    }
}