package com.tymed.app.ai

class FakeAiClient : AiClient {
    private val queue = ArrayDeque<String>()
    private var default: String? = null
    val prompts = mutableListOf<String>()
    val callCount: Int get() = prompts.size

    fun willReturnOnce(response: String) {
        queue.addLast(response)
    }

    fun willAlwaysReturn(response: String) {
        default = response
    }

    override suspend fun generate(prompt: String): String {
        prompts += prompt
        return queue.removeFirstOrNull() ?: default ?: error("FakeAiClient: no scripted response left")
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
