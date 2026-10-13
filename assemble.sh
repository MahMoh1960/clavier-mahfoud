#!/bin/bash
# Assemble le projet Android à partir des fichiers à plat de la racine du dépôt.

# Licences du SDK Android (sans conséquence si déjà acceptées)
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

set -e
P=projet
J=$P/app/src/main/java/com/mahmoh/clavier
R=$P/app/src/main/res
mkdir -p "$J" "$R/xml" "$R/drawable"
cp ClavierService.java MainActivity.java "$J/"
cp AndroidManifest.xml "$P/app/src/main/"
cp method.xml "$R/xml/"
cp ic_*.xml "$R/drawable/"

cat > $P/settings.gradle <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = 'ClavierFrAr'
include ':app'
EOF

cat > $P/build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.5.2' apply false
}
EOF

cat > $P/gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
EOF

cat > $P/app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
}
android {
    namespace 'com.mahmoh.clavier'
    compileSdk 34
    defaultConfig {
        applicationId 'com.mahmoh.clavier'
        minSdk 23
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
        encoding 'UTF-8'
    }
}
EOF

echo "Projet assemblé :"
find $P -type f | sort
