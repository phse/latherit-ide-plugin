import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// ./gradlew runIde -Pscreenshots=<ordner> baut den Screenshot-Helfer mit ein (nie für Releases verwenden).
val screenshotDir: String? = providers.gradleProperty("screenshots").orNull

// Rechnerspezifische Einstellungen (nicht eingecheckt), z. B. platformLocalPath=/Applications/PhpStorm.app
val localProperties = Properties().apply {
    file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
val platformLocalPath: String? = localProperties.getProperty("platformLocalPath")?.takeIf { file(it).exists() }

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (platformLocalPath != null) {
            local(platformLocalPath)
        } else {
            create(IntelliJPlatformType.PhpStorm, providers.gradleProperty("platformVersion").get())
        }
        bundledPlugin("com.intellij.modules.jcef")
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
        changeNotes = """
            <h3>0.2.1</h3>
            <ul>
              <li>No more deprecated API when opening a new or imported project file.</li>
            </ul>
            <h3>0.2.0</h3>
            <ul>
              <li>Editing a request no longer reloads the view - selection and expanded tree stay as they were.</li>
              <li>XML in requests and responses can be folded by tag (also comments and CDATA).</li>
              <li>Much smoother trackpad and mouse wheel scrolling in the editors.</li>
            </ul>
            <h3>0.1.0</h3>
            <ul>
              <li>First preview: LatherIt as an editor for <code>*.latherit.yaml</code> project files.</li>
              <li>New project and SoapUI import from <i>File | New</i>.</li>
              <li>SOAP and REST requests, tests, property expansion; projects are saved automatically.</li>
              <li>Mock services and Basic, Bearer, Digest and NTLM authentication.</li>
            </ul>
        """.trimIndent()
    }

    // Optional: Plugin signieren (Zertifikat wie beim Runners Bar). Ohne Umgebungsvariablen wird nicht signiert.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Für ./gradlew publishPlugin. Token aus der Umgebung oder global aus ~/.gradle/gradle.properties
    // (jetbrainsMarketplaceToken=…, wie beim Runners Bar).
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("jetbrainsMarketplaceToken"))
    }

    pluginVerification {
        ides {
            platformLocalPath?.let { local(it) }
        }
    }
}

// Die Oberfläche ist dieselbe wie in der Desktop-App: Vite-Build aus dem LatherIt-Checkout (latheritDir),
// als Ressource unter /latherit-web/ ins Plugin gepackt.
val appDir = layout.projectDirectory.dir(
    localProperties.getProperty("latheritDir") ?: providers.gradleProperty("latheritDir").get()
)

val buildWeb = tasks.register<Exec>("buildWeb") {
    workingDir = appDir.asFile
    commandLine(providers.gradleProperty("npmCommand").get(), "run", "build")
    inputs.dir(appDir.dir("src"))
    inputs.files(appDir.file("index.html"), appDir.file("package.json"), appDir.file("package-lock.json"), appDir.file("vite.config.ts"))
    outputs.dir(appDir.dir("dist"))
}

val webResources = layout.buildDirectory.dir("web")

val copyWeb = tasks.register<Sync>("copyWeb") {
    dependsOn(buildWeb)
    from(appDir.dir("dist"))
    into(webResources.map { it.dir("latherit-web") })
}

sourceSets {
    main {
        resources.srcDir(webResources)
        if (screenshotDir != null) kotlin.srcDir("src/screenshot/kotlin")
    }
}

// Für Screenshots wird das Demo-Projekt an einen neutralen Ort kopiert, damit kein lokaler
// Benutzerpfad im Bild erscheint. Jeder Lauf startet mit dem Zustand aus marketplace/demo-project.
val screenshotProjectDir = "/Users/Shared/latherit-demo"

val prepareScreenshotProject = tasks.register<Sync>("prepareScreenshotProject") {
    from("marketplace/demo-project")
    into(screenshotProjectDir)
}

// Ohne -Pscreenshots bleibt aus dem Build-Cache sonst ein leeres screenshot/-Verzeichnis in den Jars.
tasks.withType<Zip>().configureEach {
    includeEmptyDirs = false
}

tasks.processResources {
    dependsOn(copyWeb)
}

// Apache 2.0 verlangt, dass LICENSE und NOTICE mit dem Plugin verteilt werden.
tasks.jar {
    from(files("LICENSE", "NOTICE")) { into("META-INF") }
}

tasks.runIde {
    if (screenshotDir != null) {
        dependsOn(prepareScreenshotProject)
        args(screenshotProjectDir, "$screenshotProjectDir/shop-api.latherit.yaml")
        jvmArgs("-Dlatherit.screenshots=${file(screenshotDir).absolutePath}")
    } else {
        // ./gradlew runIde -PopenProject=/pfad/zum/projekt öffnet direkt ein Projekt in der Sandbox.
        providers.gradleProperty("openProject").orNull?.let { args(it) }
    }
    // Chromium-DevTools der Sandbox unter http://localhost:9222 (nur zum Entwickeln)
    providers.gradleProperty("cefDebugPort").orNull?.let { jvmArgs("-Dide.browser.jcef.debug.port=$it") }
}
