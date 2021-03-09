package lang.taxi.lsp

import com.google.common.base.Stopwatch
import lang.taxi.CompilationException
import lang.taxi.Compiler
import lang.taxi.CompilerConfig
import lang.taxi.CompilerTokenCache
import lang.taxi.lsp.completion.TypeProvider
import lang.taxi.types.SourceNames
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class TaxiCompilerService(val config: CompilerConfig = CompilerConfig()) {
    private lateinit var workspaceSourceService: WorkspaceSourceService
    private val sources: MutableMap<URI, String> = mutableMapOf()
    private val charStreams: MutableMap<URI, CharStream> = mutableMapOf()

    val lastSuccessfulCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    val lastCompilationResult: AtomicReference<CompilationResult> = AtomicReference();
    private val tokenCache: CompilerTokenCache = CompilerTokenCache()
    val typeProvider = TypeProvider(lastSuccessfulCompilationResult, lastCompilationResult)

    val sourceCount: Int
        get() {
            return sources.size
        }

    fun source(uri: URI): String {
        return this.sources[uri] ?: error("Could not find source with url ${uri.toASCIIString()}")
    }

    fun source(path: String): String {
        val uri = URI.create(SourceNames.normalize(path))
        return source(uri)
    }

    fun source(identifier: TextDocumentIdentifier): String {
        return source(identifier.uri)
    }

    fun reloadSourcesWithoutCompiling() {
        this.sources.clear()
        this.charStreams.clear()
        this.workspaceSourceService.loadSources()
            .forEach { sourceCode ->
                // Prefer operating on the path - less chances to screw up
                // the normalization of the URI, which seems to be getting
                // messed up somewhere
                if (sourceCode.path != null) {
                    updateSource(sourceCode.path!!.toUri(), sourceCode.content)
                } else {
                    updateSource(sourceCode.normalizedSourceName, sourceCode.content)
                }
            }
    }

    fun reloadSourcesAndCompile(): CompilationResult {
        reloadSourcesWithoutCompiling()
        return this.compile()
    }

    private fun updateSource(uri: URI, content: String) {
        this.sources[uri] = content
        this.charStreams[uri] = CharStreams.fromString(content, uri.toASCIIString())
    }

    fun updateSource(path: String, content: String) {
        updateSource(URI.create(SourceNames.normalize(path)), content)
    }

    fun updateSource(identifier: TextDocumentIdentifier, content: String) {
        updateSource(identifier.uri, content)
    }

    fun compile(): CompilationResult {
        val charStreams = this.charStreams.values.toList()

        val compiler = Compiler(charStreams, tokenCache = tokenCache, config = config)
        val stopwatch = Stopwatch.createStarted()
        val compilationResult = try {

            val (messages, compiled) = compiler.compileWithMessages()

            CompilationResult(compiler, compiled, charStreams.size, stopwatch.elapsed(), messages)
        } catch (e: CompilationException) {
            CompilationResult(compiler, null, charStreams.size, stopwatch.elapsed(), e.errors)
        }
        lastCompilationResult.set(compilationResult)
        if (compilationResult.successful) {
            lastSuccessfulCompilationResult.set(compilationResult)
        }
        return compilationResult
    }

    fun getOrComputeLastCompilationResult(): CompilationResult {
        if (lastCompilationResult.get() == null) {
            compile()
        }
        return lastCompilationResult.get()
    }

    fun initialize(rootUri: String, client: LanguageClient) {
        val root = File(URI.create(SourceNames.normalize(rootUri)))
        require(root.exists()) { "Fatal error - the workspace root location doesn't appear to exist" }

        workspaceSourceService = WorkspaceSourceService(root.toPath(), LspClientPackageManagerMessageLogger(client))
        reloadSourcesWithoutCompiling()
    }

}