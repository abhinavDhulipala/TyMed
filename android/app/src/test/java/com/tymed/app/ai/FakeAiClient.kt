package com.tymed.app.ai

class FakeAiClient : AiClient {
    // A null entry means "fail this call", the way a safety-filtered Gemini Nano request does.
    private val queue = ArrayDeque<String?>()
    private var default: String? = null
    val prompts = mutableListOf<String>()
    val callCount: Int get() = prompts.size

    fun willReturnOnce(response: String) {
        queue.addLast(response)
    }

    fun willFailOnce() {
        queue.addLast(null)
    }

    fun willAlwaysReturn(response: String) {
        default = response
    }

    override suspend fun generate(prompt: String): String {
        prompts += prompt
        if (queue.isNotEmpty()) {
            return queue.removeFirst() ?: throw AiGenerationException(4, RuntimeException("blocked"))
        }
        return default ?: error("FakeAiClient: no scripted response left")
    }
}

class FakeToolRunner : ToolRunner {
    private val queue = ArrayDeque<ToolResult>()
    val calls = mutableListOf<Pair<String, Map<String, Any?>>>()

    fun willReturnOnce(result: ToolResult) {
        queue.addLast(result)
    }

    override suspend fun runTool(name: String, args: Map<String, Any?>): ToolResult {
        calls += name to args
        return queue.removeFirstOrNull() ?: error("FakeToolRunner: no scripted result left for $name")
    }
}
