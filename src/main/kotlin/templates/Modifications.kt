package templates

import io.kloudformation.Value
import io.kloudformation.model.iam.PolicyDocument

interface Props
class NoProps: Props

interface Mod

abstract class Modification<T, R, P: Props>: Mod{
    open var item: R? = null
    open var modifyBuilder: T.(P) -> Unit = {  }
    open var modifyProps: P.() -> Unit = {}
    operator fun invoke(modify: Modification<T, R, P>.() -> Unit) = run(modify)
    operator fun invoke(props: P, modify: Modification<T, R, P>.(P)->R): R{
        item = modify(props(props))
        return item!!
    }
    fun modify(mod: T.(P) -> Unit){ modifyBuilder = mod }
    fun props(mod: P.() -> Unit){ modifyProps = mod }
    fun props(defaults: P) = defaults.apply(modifyProps)
}

abstract class OptionalModification<T, R, P: Props>(private var remove: Boolean = false): Mod{
    open var item: R? = null
    open var modifyBuilder: T.(P) -> T = { this }
    open var modifyProps: P.() -> Unit = {}
    operator fun invoke(modify: OptionalModification<T, R, P>.() -> Unit) = run(modify)
    operator fun invoke(props: P, modify: OptionalModification<T, R, P>.(P)->R): R?{
        if(!remove) item = modify(props(props))
        return item
    }
    fun modify(mod: T.(P) -> T){ modifyBuilder = mod }
    fun props(mod: P.() -> Unit){ modifyProps = mod }
    fun props(defaults: P) = defaults.apply(modifyProps)
    fun remove(){ remove = true }
}

fun <Builder, R, P: Props> modification() = object: Modification<Builder, R, P>(){}
fun <Builder, R, P: Props> optionalModification() = object: OptionalModification<Builder, R, P>(){}