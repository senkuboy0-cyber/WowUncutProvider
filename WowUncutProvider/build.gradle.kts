cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/senkuboy0-cyber/WowUncutProvider")
    version = 1
    description = "WowUncut - Hindi, Bengali, Tamil & More Web Series"
    authors = listOf("senkuboy0-cyber")
    language = "hi"
    tvTypes = listOf("TvSeries", "Movie", "Others")
}

android {
    namespace = "com.wowuncut"
}
