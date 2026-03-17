package org.vonderheidt.hips.utils

import org.vonderheidt.hips.data.Message
import org.vonderheidt.hips.data.Settings
import org.vonderheidt.hips.data.User

/**
 * Object (i.e. singleton class) to declare Kotlin external functions corresponding to llama.cpp functions.
 */
object LlamaCpp {
    private val path = LLM.getPath()

    @Volatile
    private var model = 0L

    @Volatile
    private var ctx = 0L

    @Volatile
    private var smpl = 0L

    fun isInMemory(): Boolean {
        return model != 0L
                && ctx != 0L
                && smpl != 0L
    }

    fun startInstance() {
        if (isInMemory()) {
            return
        }

        synchronized(lock = this) {
            if (!isInMemory()) {
                model = loadModel()
                ctx = loadCtx()
                smpl = loadSmpl()
            }
        }
    }

    fun stopInstance() {
        if (!isInMemory()) {
            return
        }

        synchronized(lock = this) {
            if (isInMemory()) {
                unloadSmpl()
                smpl = 0L

                unloadCtx()
                ctx = 0L

                unloadModel()
                model = 0L
            }
        }
    }

    fun resetInstance() {
        if (ctx == 0L) {
            return
        }

        synchronized(lock = this) {
            if (ctx != 0L) {
                unloadCtx()
                ctx = 0L

                ctx = loadCtx()
            }
        }
    }

    fun getCtx(): Long {
        return ctx
    }

    fun detokenize(tokens: IntArray): String {
        val byteArray = detokenize(tokens, ctx)
        val string = String(bytes = byteArray, charset = Charsets.UTF_8)
        return string
    }

    fun suppressSpecialTokens(probabilities: FloatArray) {
        for (token in probabilities.indices) {
            if (isSpecial(token)) {
                probabilities[token] = 0f
            }
        }
    }

    fun isEndOfSentence(token: Int): Boolean {
        val detokenization = detokenize(intArrayOf(token))
        val isSentenceFinished = detokenization.endsWith(".")
                || detokenization.endsWith("!")
                || detokenization.endsWith("?")
        return isSentenceFinished
    }

    fun getEndOfGeneration(): Int {
        var eogTokens = intArrayOf()
        for (token in 0 until getVocabSize()) {
            if (isEndOfGeneration(token)) {
                eogTokens += token
            }
        }
        return eogTokens.first()
    }

    fun getAsciiNul(): Int {
        for (token in 0 until getVocabSize()) {
            if (detokenize(intArrayOf(token)) == "\u0000") {
                return token
            }
        }
        throw NoSuchElementException("LLM vocabulary doesn't contain ASCII NUL character")
    }

    fun formatChat(priorMessages: List<Message>, isAlice: Boolean, numberOfMessages: Int = if (Settings.numberOfMessages > 0) Settings.numberOfMessages else priorMessages.size): String {
        var context = addMessage(role = Role.System.name, content = Settings.systemPrompt, appendAssistant = priorMessages.isEmpty())

        for (priorMessage in priorMessages.takeLast(numberOfMessages)) {
            val priorMessageRole = if (isAlice) {
                if (priorMessage.senderID == User.Alice.id) { Role.Assistant.name }
                else { Role.User.name }
            }
            else {
                if (priorMessage.senderID == User.Alice.id) { Role.User.name }
                else { Role.Assistant.name }
            }
            context += addMessage(role = priorMessageRole, content = priorMessage.content, appendAssistant = priorMessage == priorMessages.last())
        }

        return context
    }

    private external fun loadModel(path: String = this.path): Long
    private external fun unloadModel(model: Long = this.model)
    private external fun loadCtx(model: Long = this.model): Long
    private external fun unloadCtx(ctx: Long = this.ctx)
    private external fun loadSmpl(): Long
    private external fun unloadSmpl(smpl: Long = this.smpl)

    external fun getVocabSize(model: Long = this.model): Int
    external fun tokenize(string: String, ctx: Long = this.ctx): IntArray
    private external fun detokenize(tokens: IntArray, ctx: Long = this.ctx): ByteArray
    private external fun isSpecial(token: Int, model: Long = this.model): Boolean
    private external fun isEndOfGeneration(token: Int, model: Long = this.model): Boolean

    /**
     * Updated to accept nPast to support proper context building during scoring.
     */
    external fun getLogits(tokens: IntArray, nPast: Int, ctx: Long = this.ctx): Array<FloatArray>

    external fun sample(lastToken: Int, ctx: Long = this.ctx, smpl: Long = this.smpl): Int
    private external fun addMessage(role: String, content: String, appendAssistant: Boolean, model: Long = this.model): String
}
