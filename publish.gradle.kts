configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["release"])
        }
    }
}