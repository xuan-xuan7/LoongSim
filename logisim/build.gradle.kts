
plugins {
    id("com.github.ben-manes.versions") version "0.28.0"
    java
    application
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("edu.sc.seis.macAppBundle") version "2.3.0"
}

repositories {
    mavenCentral()
}

application {
    mainClassName = "com.cburch.logisim.Main"
}

dependencies {
    implementation(fileTree("lib") {
        include("**/*.jar")
    })
    implementation("org.hamcrest:hamcrest:2.2")
    implementation("javax.help:javahelp:2.0.05")
    implementation("com.fifesoft:rsyntaxtextarea:3.1.0")
    implementation("net.sf.nimrod:nimrod-laf:1.2")
    implementation("org.drjekyll:colorpicker:1.3")
    implementation("org.drjekyll:fontchooser:2.4")
    implementation("at.swimmesberger:swingx-core:1.6.8")
    implementation("eu.essilab:lablib-checkboxtree:4.0")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation("com.google.code.gson:gson:2.15.0")

    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation("ch.qos.logback:logback-core:1.2.3")
    testImplementation("junit:junit:4.13")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

task<Jar>("sourcesJar") {
    group = "build"
    description = "Creates a source jar archive."
    dependsOn.add("classes")
    classifier = "src"

    from(sourceSets.main.get().allSource)
}

tasks {
    jar {
        manifest {
            attributes.putAll(mapOf(
                    "Implementation-Title" to name,
                    "Implementation-Version" to archiveVersion
            ))
        }

        from(".") {
            include("LICENSE")
            include("README.md")
        }
    }
    shadowJar {
        from(".") {
            include("LICENSE")
            include("README.md")
        }
    }
    macAppBundle {
        mainClassName = "com.cburch.logisim.Main"
        runtimeConfigurationName = "shadow"
        jarTask = "shadowJar"
        bundleJRE = false
        highResolutionCapable = true
        appName = "LoongSim"
        appStyle = "universalJavaApplicationStub"
        bundleIdentifier = "com.loongson.loongsim"
        creatorCode = "????"
        icon = "src/main/resources/resources/logisim/img/LoongSim.icns"
        // backgroundImage = "src/main/resources/resources/logisim/img/logisim-icon-128.png"
        // javaProperties.put("apple.laf.useScreenMenuBar", "true")
        bundleExtras.put("CFBundleDisplayName", "LoongSim")
        bundleExtras.put(
            "CFBundleDocumentTypes",
            arrayOf(
                mapOf(
                    "LSItemContentTypes" to arrayOf("com.loongson.loongsim.circ"),
                    "CFBundleTypeName" to "LoongSim circuit file",
                    "LSHandlerRank" to "Owner",
                    "CFBundleTypeRole" to "Editor",
                    "LSIsAppleDefaultForType" to true
                )
            )
        )
        bundleExtras.put(
            "UTExportedTypeDeclarations",
            arrayOf(
                mapOf(
                    "UTTypeIdentifier" to "com.loongson.loongsim.circ",
                    "UTTypeDescription" to "LoongSim circuit file",
                    "UTTypeConformsTo" to arrayOf("public.data"),
                    "UTTypeIconFile" to "LoongSim.icns",
                    "UTTypeTagSpecification" to
                    mapOf(
                        "public.filename-extension" to arrayOf("circ"),
                        "public.mime-type" to arrayOf("application-prs.cburch.logisim")
                    )
                )
            )
        )
        bundleExtras.put("LSApplicationCategoryType", "public.app-category.education")
        bundleExtras.put("NSHumanReadableCopyright", "Copyright © 2001–2020 Carl Burch, BFH, HEIG-VD, HEPIA, Holy Cross, et al.")
        bundleExtras.put("NSSupportsAutomaticGraphicsSwitching", "true")
    }
}
