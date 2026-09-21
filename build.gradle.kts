import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("io.izzel.taboolib") version "2.0.38"
    `maven-publish`
}

taboolib {

    version {
        taboolib = "6.3.0-75b18a2"
    }

    env {
        // 镜像中央仓库
        repoCentral = "https://repo.huaweicloud.com/repository/maven/"
        // 安装模块
        install(
            Basic,
            Bukkit,
            Kether,
            BukkitHook,
            BukkitNMS,
            BukkitNMSUtil,
            DatabasePlayer,
            Metrics,
            I18n,
            BukkitUI,
            JavaScript,
        )
        install("platform-bukkit-impl")
    }

    description {
        name(rootProject.name)
        desc("灵活强大的多功能容器 GUI 解决方案")
    }

    // 重定向
    relocate("kotlinx.serialization.", "kotlinx.serialization190.")
    relocate("org.slf4j", "cc.trixey.invero.libs.slf4j")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.oraxen.com/releases")
    maven("https://repo.momirealms.net/releases/")
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
    maven("https://repo.auxilor.io/repository/maven-public/")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    // Kotlin 序列化
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")

    // Adventure API
    compileOnly("net.kyori:adventure-api:4.24.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.24.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.24.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.24.0")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")

    // Minecraft Core
    compileOnly("ink.ptms.core:v12105:12105:mapped")
    compileOnly("ink.ptms.core:v12105:12105:universal")
    compileOnly("ink.ptms.core:v260100:260100")
    compileOnly("ink.ptms.core:v260100:260100-minimize")
    compileOnly("ink.ptms:nms-all:1.0.0")

    compileOnly("io.netty:netty-all:4.1.106.Final")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly("com.google.guava:guava:32.0.0-android")
    compileOnly("com.mojang:brigadier:1.0.18")
    compileOnly("com.mojang:authlib:5.0.51")

    // 添加 SLF4J 依赖
    taboo("org.slf4j:slf4j-api:1.7.36")
    taboo("org.slf4j:slf4j-simple:1.7.36")

    // Compatible Plugins
    compileOnly("ink.ptms.adyeshach:all:2.0.0-snapshot-36")
    compileOnly("org.black_ixx:playerpoints:3.1.1")
    compileOnly("io.th0rgal:oraxen:1.189.0")
    compileOnly("ink.ptms:Zaphkiel:2.0.14")
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.2")
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.3-beta-14")

    // CraftEngine
    compileOnly("net.momirealms:craft-engine-core:26.6.1") { isTransitive = false }
    compileOnly("net.momirealms:craft-engine-bukkit:26.6.1") { isTransitive = false }

    // MMOItems
    compileOnly("io.lumine:MythicLib-dist:1.6.2-SNAPSHOT") { isTransitive = false } // Required by MMOItems API
    compileOnly("net.Indyuce:MMOItems-API:6.10-SNAPSHOT") { isTransitive = false }

    // EcoItems
    compileOnly("com.willfp:eco:6.71.3") { isTransitive = false }
    compileOnly("com.willfp:libreforge:4.60.0") { isTransitive = false }
    compileOnly("com.willfp:EcoItems:5.49.1") { isTransitive = false }

    // MagicCosmetics
    compileOnly("com.github.FrancoBM12:API-MagicCosmetics:2.2.7") { isTransitive = false }

    // Slimefun
    compileOnly("io.github.Slimefun:Slimefun4:RC-32") { isTransitive = false }

    // 本地依赖库
    compileOnly(fileTree("libs"))
}

// 资源处理
tasks.processResources {
    filesMatching("**/*.json") {
        expand(
            "serialization" to "1.9.0",
            "adventureApi" to "4.24.0",
            "adventurePlatform" to "4.4.1",
            "kr" to "220", // Kotlin Version Escaped
            "krx" to "190", // Kotlin Serialization Version Escaped
        )
    }
}

// Kotlin 构建设置
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs = listOf(
            "-Xjvm-default=all",
            "-Xextended-compiler-checks",
            "-Xskip-prerelease-check",
            "-Xallow-unstable-dependencies"
        )
    }
}

// Java 构建设置
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// 编码设置
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Maven 发布配置
publishing {
    repositories {
        maven {
            name = "hiusers"
            url = uri("https://repo.hiusers.com/releases")
            credentials {
                username = project.findProperty("MAVEN_USERNAME") as String? ?: System.getenv("MAVEN_USERNAME") ?: ""
                password = project.findProperty("MAVEN_PASSWORD") as String? ?: System.getenv("MAVEN_PASSWORD") ?: ""
            }
        }
    }
    
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.name
            version = "${project.version as String}-api"
            
            // 发布 API JAR
            val apiJarFile = file("${project.buildDir}/libs/${project.name}-${project.version as String}-api.jar")
            artifact(apiJarFile) {
                extension = "jar"
                builtBy(tasks.named("taboolibBuildApi"))
            }
        }
    }
}
