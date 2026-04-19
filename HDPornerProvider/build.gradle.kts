cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/senkuboy0-cyber/WowUncutProvider")
    version = 1
    description = "HDPorner - Watch premium porn videos for free"
    authors = listOf("senkuboy0-cyber")
    language = "en"
    tvTypes = listOf("NSFW")
}

android {
    namespace = "com.hdporner"
}
