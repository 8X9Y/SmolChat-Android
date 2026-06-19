package io.shubham0204.smollmandroid.modelmarket

object ModelCatalog {
    val models: List<ModelInfo> = listOf(
        ModelInfo(
            name = "Qwen3-1.7B Q8_0",
            description = "阿里最新1.7B模型，中英双语，推荐首选",
            sizeMB = 2150,
            ramRequiredGB = 4,
            hfRepo = "Qwen/Qwen3-1.7B-GGUF",
            filename = "qwen3-1.7b-q8_0.gguf",
            mirrorUrl = "https://hf-mirror.com/Qwen/Qwen3-1.7B-GGUF/resolve/main/qwen3-1.7b-q8_0.gguf",
            tags = listOf("chat", "chinese", "small", "recommended"),
            isRecommended = true,
        ),
        ModelInfo(
            name = "Qwen2.5-1.5B Q8_0",
            description = "轻量级中文模型，低配手机可用",
            sizeMB = 1700,
            ramRequiredGB = 4,
            hfRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            filename = "qwen2.5-1.5b-instruct-q8_0.gguf",
            mirrorUrl = "https://hf-mirror.com/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
            tags = listOf("chat", "chinese", "small"),
        ),
        ModelInfo(
            name = "Qwen2.5-3B Q5_K_M",
            description = "平衡性能和质量，中等手机推荐",
            sizeMB = 2200,
            ramRequiredGB = 6,
            hfRepo = "Qwen/Qwen2.5-3B-Instruct-GGUF",
            filename = "qwen2.5-3b-instruct-q5_k_m.gguf",
            mirrorUrl = "https://hf-mirror.com/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q5_k_m.gguf",
            tags = listOf("chat", "chinese"),
        ),
    )

    fun getRecommendedForRam(totalRamGB: Int): List<ModelInfo> =
        models.filter { it.ramRequiredGB <= totalRamGB - 2 }
}
