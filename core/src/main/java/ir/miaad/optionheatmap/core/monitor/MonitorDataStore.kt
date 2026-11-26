package ir.miaad.optionheatmap.core.monitor

import ir.miaad.optionheatmap.core.diagnostics.DiagnosticLog
import ir.miaad.optionheatmap.core.data.InstrumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DataFreshness { WAITING, FRESH, STALE, ERROR }

data class ApiHealth(
    val status: DataFreshness = DataFreshness.WAITING,
    val lastSuccessMs: Long? = null,
    val message: String? = null
)

data class MonitorSymbolSnapshot(
    val symbol: String,
    val price: PriceInfo? = null,
    val best: BestLimit1? = null,
    val limits: DayLimits? = null,
    val result: MonitorResult? = null,
    val tradable: Boolean? = null,
    val spark: List<Float> = emptyList(),
    val searchHealth: ApiHealth = ApiHealth(),
    val infoHealth: ApiHealth = ApiHealth(),
    val priceHealth: ApiHealth = ApiHealth(),
    val bestHealth: ApiHealth = ApiHealth(),
    val lastCompleteCycleMs: Long? = null
)

private class MutableSymbolState(val symbol: String) {
    var insCode: String? = null
    var price: PriceInfo? = null
    var best: BestLimit1? = null
    var limits: DayLimits? = null
    var result: MonitorResult? = null
    var searchHealth = ApiHealth()
    var infoHealth = ApiHealth()
    var priceHealth = ApiHealth()
    var bestHealth = ApiHealth()
    var lastCompleteCycleMs: Long? = null
    val queueHistory = ArrayList<Pair<Long, Double>>()
}

object MonitorDataStore {
    private const val TAG = "OH-DIAG-Store"
    // نمادهای موقت آزمون اتصال مرحله 3؛ واچ‌لیست نهایی جداگانه ساخته می‌شود.
    private val testSymbols = listOf("فولاد", "وبملت", "گوهر")
    private val states = testSymbols.associateWith(::MutableSymbolState)
    private val monitors = testSymbols.associateWith { SymbolMonitor(it) }
    private val repository = InstrumentRepository()
    private val refreshMutex = Mutex()

    private val mutableSnapshots = MutableStateFlow(testSymbols.map { states.getValue(it).snapshot() })
    val snapshots: StateFlow<List<MonitorSymbolSnapshot>> = mutableSnapshots.asStateFlow()

    suspend fun refreshAll() {
        refreshMutex.withLock {
            DiagnosticLog.i(TAG, "REFRESH_START symbols=$testSymbols")
            coroutineScope {
                testSymbols.map { symbol -> async { refreshSymbol(states.getValue(symbol)) } }.forEach { it.await() }
            }
            mutableSnapshots.value = testSymbols.map { states.getValue(it).snapshot() }
            DiagnosticLog.i(TAG, "SNAPSHOT_PUBLISHED " + mutableSnapshots.value.joinToString { snap ->
                "${snap.symbol}(price=${snap.price != null},best=${snap.best != null},limits=${snap.limits != null},priceStatus=${snap.priceHealth.status},bestStatus=${snap.bestHealth.status},infoStatus=${snap.infoHealth.status},searchStatus=${snap.searchHealth.status})"
            })
        }
    }

    private suspend fun refreshSymbol(state: MutableSymbolState) {
        val now = System.currentTimeMillis()
        DiagnosticLog.i(TAG, "SYMBOL_REFRESH_START symbol=${state.symbol} time=$now")
        val insCode = try {
            state.insCode ?: repository.resolveInsCode(state.symbol).also {
                state.insCode = it
                state.searchHealth = ApiHealth(DataFreshness.FRESH, now)
                DiagnosticLog.i(TAG, "API_SUCCESS symbol=${state.symbol} api=InstrumentSearch insCode=$it")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            logFailure(state.symbol, "InstrumentSearch", error)
            state.searchHealth = ApiHealth(DataFreshness.ERROR, state.searchHealth.lastSuccessMs, error.message)
            return
        }

        state.infoHealth = state.infoHealth.pending()
        state.priceHealth = state.priceHealth.pending()
        state.bestHealth = state.bestHealth.pending()

        val results = coroutineScope {
            val info = async { capture { repository.fetchDayLimits(insCode) } }
            val price = async { capture { repository.fetchPriceInfo(insCode) } }
            val best = async { capture { repository.fetchBestLimit1(insCode) } }
            Triple(info.await(), price.await(), best.await())
        }

        logResult(state.symbol, "InstrumentInfo", results.first)
        logResult(state.symbol, "ClosingPrice", results.second)
        logResult(state.symbol, "BestLimits", results.third)

        state.infoHealth = results.first.toHealth(now, state.infoHealth.lastSuccessMs)
        state.priceHealth = results.second.toHealth(now, state.priceHealth.lastSuccessMs)
        state.bestHealth = results.third.toHealth(now, state.bestHealth.lastSuccessMs)
        results.first.getOrNull()?.let { state.limits = it }
        results.second.getOrNull()?.let { state.price = it }
        results.third.getOrNull()?.let { state.best = it }

        if (results.first.isSuccess && results.second.isSuccess && results.third.isSuccess) {
            val tehran = ZonedDateTime.now(ZoneId.of("Asia/Tehran"))
            val input = MonitorInput(
                timeMs = now,
                best = results.third.getOrThrow(),
                info = results.second.getOrThrow(),
                limits = results.first.getOrThrow(),
                preOpen = QueueLogic.isPreOpen(tehran.hour * 60 + tehran.minute)
            )
            val output = monitors.getValue(state.symbol).process(input)
            state.result = output
            state.lastCompleteCycleMs = now
            state.queueHistory.add(now to output.queueVolume)
            while (state.queueHistory.size > 225) state.queueHistory.removeAt(0)
            DiagnosticLog.i(TAG, "ANALYSIS_UPDATED symbol=${state.symbol} time=$now queueVolume=${output.queueVolume}")
        } else {
            DiagnosticLog.w(TAG, "ANALYSIS_SKIPPED symbol=${state.symbol} reason=one_or_more_APIs_failed")
        }
    }

    private fun logResult(symbol: String, api: String, result: Result<*>) {
        result.fold(
            onSuccess = { DiagnosticLog.i(TAG, "API_SUCCESS symbol=$symbol api=$api") },
            onFailure = { logFailure(symbol, api, it) }
        )
    }

    private fun logFailure(symbol: String, api: String, error: Throwable) {
        val causes = generateSequence(error) { it.cause }
            .map { "${it.javaClass.name}: ${it.message}" }
            .joinToString(" <- ")
        DiagnosticLog.e(TAG, "API_FAILURE symbol=$symbol api=$api exception=${error.javaClass.name} message=${error.message} causes=$causes\n${DiagnosticLog.stackTrace(error)}")
    }

    private fun ApiHealth.pending() = copy(
        status = if (lastSuccessMs == null) DataFreshness.WAITING else DataFreshness.STALE,
        message = null
    )

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun <T> Result<T>.toHealth(now: Long, previousSuccess: Long?): ApiHealth =
        fold(
            onSuccess = { ApiHealth(DataFreshness.FRESH, now) },
            onFailure = { ApiHealth(DataFreshness.ERROR, previousSuccess, it.message ?: "خطای نامشخص") }
        )

    private fun MutableSymbolState.snapshot(): MonitorSymbolSnapshot {
        val values = queueHistory.map { it.second }
        val max = values.maxOrNull() ?: 0.0
        val spark = if (max <= 0.0) values.map { 0f } else values.map { (it / max).toFloat().coerceIn(0f, 1f) }
        return MonitorSymbolSnapshot(
            symbol = symbol,
            price = price,
            best = best,
            limits = limits,
            result = result,
            tradable = price?.cEtaval?.let { QueueLogic.isTradable(it) },
            spark = spark,
            searchHealth = searchHealth,
            infoHealth = infoHealth,
            priceHealth = priceHealth,
            bestHealth = bestHealth,
            lastCompleteCycleMs = lastCompleteCycleMs
        )
    }
}
