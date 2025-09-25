import org.zaproxy.gradle.addon.AddOnStatus

description = (
    "Includes request and response data in XML reports and provides the ability " +
        "to upload reports directly to a Software Risk Manager server"
)

zapAddOn {
    addOnName.set("Software Risk Manager Extension")
    addOnStatus.set(AddOnStatus.ALPHA)

    manifest {
        author.set("Black Duck, Inc.")
        url.set("https://www.zaproxy.org/docs/desktop/addons/srm/")
    }
}

dependencies {
    implementation("org.apache.httpcomponents:httpmime:4.5.13")
    implementation("com.googlecode.json-simple:json-simple:1.1.1") {
        // Not needed.
        exclude(group = "junit")
    }
}

spotless {
    java {
        // Don't check license nor format/style, 3rd-party add-on.
        clearSteps()
    }
}
