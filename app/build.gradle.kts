
plugins {
    alias(libs.plugins.android.application)
    alias(wings.plugins.compose)
    alias(libs.plugins.ksp)
}


ksp {
    arg("NetResult", "com.learn.uikit.dto.NetResult")
}

android {
    namespace = "com.learn.replace"
}

dependencies {
    ksp(project(":net-repository"))
    implementation(project(":net-repository-anno"))
    implementation(project(":feature:home"))
//    implementation(replace(":feature:media"))
    implementation(project(":feature:media"))
//    compileOnly(replace(":feature:profile"))
//    implementation(replace(":feature:profile"))
    implementation(project(":feature:profile"))
}