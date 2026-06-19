package io.shubham0204.smollmandroid.modelmarket

data class ModelInfo(
    val name: String,
    val description: String = "",
    val sizeMB: Long = 0,
    val ramRequiredGB: Int = 0,
    val hfRepo: String = "",
    val filename: String,
    val mirrorUrl: String? = null,
    val tags: List<String> = emptyList(),
    val isRecommended: Boolean = false,
) {
    fun hfResolveUrl(): String =
        "https://huggingface.co/$hfRepo/resolve/main/$filename"
}
