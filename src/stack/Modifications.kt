import io.kloudformation.Value
import io.kloudformation.model.iam.PolicyDocument

interface Props
class NoProps: Props

abstract class Modification<T, P: Props>{
    var remove = false
    open var modifyBuilder: T.(P) -> T = { this }
    open var modifyProps: P.() -> Unit = {}
    operator fun invoke(modify: Modification<T, P>.() -> Unit) = run(modify)
    fun modify(mod: T.(P) -> T){ modifyBuilder = mod }
    fun props(mod: P.() -> Unit){ modifyProps = mod }
    fun props(defaults: P) = defaults.apply(modifyProps)
    fun remove(){ remove = true }
}

