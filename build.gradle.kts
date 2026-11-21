// fterm's build, in Gradle.
//
// Why not the Android Gradle Plugin: AGP wants a full Android SDK installation (platform-tools,
// build-tools, cmdline-tools) and a network round trip to resolve it, and this project is built
// on the phone it runs on. What it actually needs is three things the SDK ships - android.jar,
// aapt2 and d8 - plus apksigner and zipalign. So this file drives those directly, exactly as
// build.sh did, but with incremental inputs so a second build after touching one Java file does
// not repeat the whole pipeline.
//
// The one thing Gradle buys us here that build.sh could not: `inputs`/`outputs` on every task.
// That is also what makes the build reproducible enough to describe to F-Droid - the tool
// versions are read from the tools themselves and printed, not assumed.
//
//   ./gradlew buildApk                 # build/fterm.apk
//   ./gradlew buildApk -PandroidJar=/path/to/android.jar
//   ./gradlew test                     # the JVM suites (tests/run_core.sh equivalent)
//   ./gradlew printToolchain           # what versions this build used

plugins {
    java
}

// ---------------------------------------------------------------- toolchain

val sdkRoot: String = (findProperty("androidSdk") as String?)
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "$rootDir/../android-sdk"

val androidJarFile: File = run {
    val explicit = findProperty("androidJar") as String?
    when {
        explicit != null && explicit.isNotBlank() -> file(explicit)
        else -> {
            // The SDK layout is platforms/android-<n>/android.jar; pick the highest.
            val platforms = File(sdkRoot, "platforms")
            val best = platforms.listFiles()
                ?.filter { it.isDirectory && File(it, "android.jar").isFile }
                ?.maxByOrNull { it.name }
            best?.let { File(it, "android.jar") }
                ?: File(System.getenv("HOME") ?: "", "build/android/android.jar")
        }
    }
}

val minSdk = 26
val targetSdk = 36
val appId = "io.github.crashedsystem.terminal"

// Version comes from the manifest so there is exactly one place to bump it.
val manifestText = layout.projectDirectory.file("AndroidManifest.xml").asFile.readText()
fun manifestAttr(name: String): String =
    Regex("""android:$name="([^"]+)"""").find(manifestText)?.groupValues?.get(1) ?: "0"
val versionCode = manifestAttr("versionCode")
val versionName = manifestAttr("versionName")

val tool = { name: String ->
    val which = providers.exec { commandLine("sh", "-c", "command -v $name") }.standardOutput.asText.get().trim()
    which
}

// ---------------------------------------------------------------- sources

// The vendored Termux engine lives beside the app code, in the same package tree the app uses.
// Both are compiled together: the app calls into TerminalSession, and splitting them would only
// add a jar step to a build that already fits in one javac invocation.
val javaSources = fileTree("app/src") { include("**/*.java") }

sourceSets {
    main {
        java.setSrcDirs(listOf("app/src"))
    }
}

// ---------------------------------------------------------------- tasks

val compileResources = tasks.register<Exec>("compileResources") {
    group = "build"
    description = "aapt2 compile: res/ -> compiled resources"

    inputs.dir("res")
    // aapt2 compile --dir writes one zip of flat files; that zip is the link input.
    outputs.file(layout.buildDirectory.file("compiled/res.zip"))

    doFirst { layout.buildDirectory.dir("compiled").get().asFile.mkdirs() }
    commandLine(
        "aapt2", "compile", "--dir", "res",
        "-o", layout.buildDirectory.file("compiled/res.zip").get().asFile.absolutePath
    )
}

val linkResources = tasks.register<Exec>("linkResources") {
    group = "build"
    description = "aapt2 link: produces the APK shell with resources and the R class"

    dependsOn(compileResources)

    // The fonts are assets, not resources: aapt2 would store them compressed and gain nothing.
    val assetsStage = layout.buildDirectory.dir("assets-stage")
    inputs.files(fileTree("assets") { include("fonts/*.ttf") })
    inputs.files(fileTree("licenses") { include("*.txt") })
    inputs.files("NOTICE.md", "LICENSE")
    // The manifest is an input of the link step, not just a build-file read: without this,
    // bumping versionCode left linkResources UP-TO-DATE and the APK kept the old version.
    inputs.file("AndroidManifest.xml")
    inputs.dir("res")
    outputs.dir(layout.buildDirectory.dir("gen"))
    outputs.file(layout.buildDirectory.file("base.apk"))

    doFirst {
        val stage = assetsStage.get().asFile
        stage.deleteRecursively()
        File(stage, "fonts").mkdirs()
        copy {
            from("assets/fonts")
            into(File(stage, "fonts"))
            include("*.ttf")
        }
        // The notices must be readable on a device that never sees the repository.
        copy { from("NOTICE.md", "LICENSE"); into(stage) }
        copy { from("licenses"); into(File(stage, "licenses")) }
        layout.buildDirectory.dir("gen").get().asFile.mkdirs()
    }
    commandLine(
        "aapt2", "link",
        "-o", layout.buildDirectory.file("base.apk").get().asFile.absolutePath,
        "-I", androidJarFile.absolutePath,
        "--manifest", "AndroidManifest.xml",
        "--java", layout.buildDirectory.dir("gen").get().asFile.absolutePath,
        "--min-sdk-version", minSdk.toString(),
        "--target-sdk-version", targetSdk.toString(),
        "-A", assetsStage.get().asFile.absolutePath,
        layout.buildDirectory.file("compiled/res.zip").get().asFile.absolutePath
    )
}

val compileJavaApp = tasks.register<JavaCompile>("compileJavaApp") {
    group = "build"
    description = "javac against android.jar plus the generated R class"

    dependsOn(linkResources)

    source(javaSources)
    source(layout.buildDirectory.dir("gen"))
    classpath = files(androidJarFile)
    destinationDirectory.set(layout.buildDirectory.dir("obj"))
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "--release", "11", "-Xlint:-options"))
}

val dex = tasks.register<Exec>("dex") {
    group = "build"
    description = "d8: class files -> classes.dex"

    dependsOn(compileJavaApp)

    val classes = layout.buildDirectory.dir("obj")
    inputs.dir(classes)
    outputs.dir(layout.buildDirectory.dir("dex"))

    doFirst {
        layout.buildDirectory.dir("dex").get().asFile.mkdirs()
        val list = layout.buildDirectory.file("classes.txt").get().asFile
        val classFiles = classes.get().asFile.walkTopDown().filter { it.extension == "class" }.toList()
        check(classFiles.isNotEmpty()) { "no class files were produced" }
        list.writeText(classFiles.joinToString("\n") { it.absolutePath })
    }
    commandLine(
        "sh", "-c",
        "d8 --lib '${androidJarFile.absolutePath}' --min-api $minSdk --release " +
            "--output '${layout.buildDirectory.dir("dex").get().asFile.absolutePath}' " +
            "\$(cat '${layout.buildDirectory.file("classes.txt").get().asFile.absolutePath}')"
    )
}

// classes.dex has to be stored uncompressed, so it is appended with python rather than by a
// zip tool. This is the same step build.sh did and the reason is the same: an APK whose dex is
// deflated is rejected by the platform's dex loader.
val packageApk = tasks.register("packageApk") {
    group = "build"
    description = "put classes.dex into the linked APK and align it"

    dependsOn(dex)

    inputs.file(layout.buildDirectory.file("base.apk"))
    inputs.file(layout.buildDirectory.dir("dex").map { File(it.asFile, "classes.dex") })
    outputs.file(layout.buildDirectory.file("app-aligned.apk"))

    doLast {
        val base = layout.buildDirectory.file("base.apk").get().asFile
        val dexFile = File(layout.buildDirectory.dir("dex").get().asFile, "classes.dex")
        check(dexFile.isFile) { "classes.dex missing at ${dexFile.absolutePath}" }
        // A fresh copy every time: appending to base.apk in place would add a second
        // classes.dex on the next build, and apksigner rejects a zip with duplicates.
        val app = layout.buildDirectory.file("app.apk").get().asFile
        app.delete()
        base.copyTo(app, overwrite = true)
        providers.exec {
            commandLine(
                "python3", "-c",
                """
                import sys, zipfile
                apk, dex = sys.argv[1], sys.argv[2]
                with zipfile.ZipFile(apk, 'a', zipfile.ZIP_STORED) as z:
                    z.write(dex, 'classes.dex')
                with zipfile.ZipFile(apk) as z:
                    names = z.namelist()
                assert 'classes.dex' in names, "classes.dex missing: %s" % names
                print("  dex %d bytes, %d entries" % (z.getinfo('classes.dex').file_size, len(names)))
                """.trimIndent(),
                app.absolutePath, dexFile.absolutePath
            )
        }.standardOutput.asText.get().lines().filter { it.isNotBlank() }.forEach { logger.lifecycle(it) }
        providers.exec {
            commandLine(
                "zipalign", "-f", "4", app.absolutePath,
                layout.buildDirectory.file("app-aligned.apk").get().asFile.absolutePath
            )
        }.standardOutput.asText.get()
    }
}

val buildApk = tasks.register<Exec>("buildApk") {
    group = "build"
    description = "sign the aligned APK: build/fterm.apk"

    dependsOn(packageApk)

    // The signing key. `KS` overrides the path because CI decodes the release key into a temp
    // file: without it the build looked only at $HOME/build/android/ks.jks, found nothing on a
    // runner, and generated a *new* key on every release - so every release was signed by a
    // different certificate and Android refused to update an installed copy. F-Droid signs with
    // its own key, so the local default is only for local installs.
    val keystore = System.getenv("KS")?.let { File(it) }
            ?: File(System.getenv("HOME") ?: ".", "build/android/ks.jks")
    val storePass = System.getenv("KS_PASS")
            ?: (findProperty("keystorePassword") as String?) ?: "openclaw"
    val out = layout.buildDirectory.file("fterm.apk")

    inputs.file(layout.buildDirectory.file("app-aligned.apk"))
    outputs.file(out)

    doFirst {
        if (!keystore.isFile) {
            // Generating one is right for a developer's first local build and wrong for a
            // release: a runner must not invent an identity. CI sets KS, so reaching here with
            // KS set means the secret was decoded to nothing.
            if (System.getenv("KS") != null) {
                throw GradleException("KS is set to ${keystore.absolutePath} but no keystore is there")
            }
            keystore.parentFile.mkdirs()
            providers.exec {
                commandLine(
                    "keytool", "-genkeypair", "-keystore", keystore.absolutePath,
                    "-alias", "gw", "-storepass", storePass, "-keypass", storePass,
                    "-dname", "CN=fterm", "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "10000"
                )
            }.standardOutput.asText.get()
        }
        logger.lifecycle("signing with ${keystore.name}")
    }
    commandLine(
        "apksigner", "sign",
        "--ks", keystore.absolutePath,
        "--ks-pass", "pass:$storePass",
        "--key-pass", "pass:$storePass",
        "--out", out.get().asFile.absolutePath,
        layout.buildDirectory.file("app-aligned.apk").get().asFile.absolutePath
    )

    doLast {
        val f = out.get().asFile
        logger.lifecycle("BUILD OK: ${f.absolutePath} (${f.length() / 1024} KiB)")
    }
}

// ---------------------------------------------------------------- reporting

tasks.register("printToolchain") {
    group = "help"
    description = "the exact tool versions this build used, for a bug report or an F-Droid recipe"
    doLast {
        fun run(vararg cmd: String): String = try {
            providers.exec { commandLine(*cmd) }.standardOutput.asText.get().trim().lines().first()
        } catch (e: Exception) {
            "not found"
        }
        println("android.jar   ${androidJarFile.absolutePath}")
        println("aapt2         ${run("aapt2", "version")}")
        println("d8            ${run("d8", "--version")}")
        println("apksigner     ${run("apksigner", "version")}")
        println("zipalign      ${run("zipalign", "-h")}")
        println("javac         ${run("javac", "-version")}")
        println("minSdk        $minSdk")
        println("targetSdk     $targetSdk")
        println("appId         $appId")
        println("version       $versionName ($versionCode)")
    }
}

tasks.named("build") { dependsOn(buildApk) }

defaultTasks("buildApk")
