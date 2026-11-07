// Alternatif Maven: ./gradlew build  -> build/libs/LegendaryForge-1.0.0.jar
plugins { java }

group = "com.legendaryforge"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.auramc.dev/releases/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    // Paper API 26.2 (cek build lebih baru di repo.papermc.io)
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.0")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.processResources { filesMatching("plugin.yml") { expand("project" to mapOf("version" to project.version)) } }
