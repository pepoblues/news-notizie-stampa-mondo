package it.mondofeed.app

data class FeedSource(
    val id: String,
    val name: String,
    val country: String,
    val territory: String,
    val language: String,
    val category: String,
    val feedUrl: String,
    val websiteUrl: String,
    val enabled: Boolean = true
)

data class Article(
    val title: String,
    val link: String,
    val description: String,
    val published: String,
    val displayDateTime: String,
    val publishedEpochMillis: Long,
    val source: String,
    val country: String,
    val territory: String
)

data class FeedStatus(val source: FeedSource, val ok: Boolean, val message: String)
