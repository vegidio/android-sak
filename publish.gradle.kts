configure<PublishingExtension> {
    publications {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/vegidio/android-sak")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
        create<MavenPublication>("maven") {
            groupId = "io.vinicius.sak"
            version = System.getenv("VERSION")
            from(components["release"])
        }
    }
}