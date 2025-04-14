plugins {
    id("java")
}

group = "com.domaincord"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.11")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}