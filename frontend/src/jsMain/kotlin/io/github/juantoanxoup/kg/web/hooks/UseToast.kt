package io.github.juantoanxoup.kg.web.hooks

import kotlinx.coroutines.awaitCancellation
import react.useEffectOnce
import react.useState
import web.timers.setTimeout
import kotlin.time.Duration.Companion.seconds

enum class ToastVariant { DEFAULT, DESTRUCTIVE }

data class Toast(
    val id: Int,
    val title: String,
    val description: String? = null,
    val variant: ToastVariant = ToastVariant.DEFAULT,
)

/** Global toast queue (src/hooks/use-toast.ts). Toasts dismiss themselves after five seconds. */
object ToastStore {
    private var nextId = 0
    private val listeners = mutableSetOf<(List<Toast>) -> Unit>()

    var toasts: List<Toast> = emptyList()
        private set

    fun toast(
        title: String,
        description: String? = null,
        variant: ToastVariant = ToastVariant.DEFAULT,
    ) {
        val toast = Toast(++nextId, title, description, variant)
        toasts = toasts + toast
        notifyListeners()
        setTimeout(5.seconds) { dismiss(toast.id) }
    }

    fun dismiss(id: Int) {
        toasts = toasts.filter { it.id != id }
        notifyListeners()
    }

    fun subscribe(listener: (List<Toast>) -> Unit) {
        listeners += listener
    }

    fun unsubscribe(listener: (List<Toast>) -> Unit) {
        listeners -= listener
    }

    private fun notifyListeners() = listeners.forEach { it(toasts) }
}

/** Hook returning the current toasts, re-rendering the caller when they change. */
fun useToasts(): List<Toast> {
    val (toasts, setToasts) = useState(ToastStore.toasts)
    useEffectOnce {
        val listener: (List<Toast>) -> Unit = { setToasts(it) }
        ToastStore.subscribe(listener)
        try {
            awaitCancellation()
        } finally {
            ToastStore.unsubscribe(listener)
        }
    }
    return toasts
}

/** Convenience matching `const { toast } = useToast()`. */
fun useToast(): (
    String,
    String?,
    ToastVariant,
) -> Unit =
    { title, description, variant -> ToastStore.toast(title, description, variant) }
