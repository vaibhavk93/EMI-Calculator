// Plugins are declared per-module rather than aliased here with `apply false`, because
// even `apply false` resolves the plugin artifact at configuration time. Declaring the
// Android plugins only inside :app keeps the pure-Kotlin modules buildable in an
// environment that cannot reach Google's Maven repository.
