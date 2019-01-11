package templates

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.model.iam.PolicyDocument
import kotlin.math.abs

typealias Builder<Parts> = KloudFormation.(Parts.()->Unit) -> Unit

interface Props
class NoProps: Props

interface Mod<T, R, P: Props>

abstract class Modification<T, R, P: Props>: Mod<T,R,P>{
    open var item: R? = null
    open var replaceWith: R? = null
    open var modifyBuilder: T.(P) -> T = { this }
    open var modifyProps: P.() -> Unit = {}
    operator fun invoke(modify: Modification<T, R, P>.() -> Unit){
        replaceWith = null
        run(modify)
    }
    operator fun invoke(props: P, modify: Modification<T, R, P>.(P)->R): R{
        return if(replaceWith != null) replaceWith!! else {
            item = modify(props(props))
            return item!!
        }
    }
    fun modify(mod: T.(P) -> Unit){ modifyBuilder = { mod(it); this } }
    fun props(mod: P.() -> Unit){ modifyProps = mod }
    fun props(defaults: P) = defaults.apply(modifyProps)
    fun replaceWith(item: R){ replaceWith = item }
}

abstract class OptionalModification<T, R, P: Props>(private var remove: Boolean = false): Mod<T,R,P>{
    open var item: R? = null
    open var replaceWith: R? = null
    open var modifyBuilder: T.(P) -> T = { this }
    open var modifyProps: P.() -> Unit = {}
    operator fun invoke(modify: OptionalModification<T, R, P>.() -> Unit) {
        replaceWith = null
        remove = false
        run(modify)
    }
    operator fun invoke(props: P, modify: OptionalModification<T, R, P>.(P)->R): R?{
        return if(!remove){
            if(replaceWith != null) replaceWith else {
                item = modify(props(props))
                item
            }
        } else item
    }
    fun modify(mod: T.(P) -> T){ modifyBuilder = { mod(it); this } }
    fun props(mod: P.() -> Unit){ modifyProps = mod }
    fun props(defaults: P) = defaults.apply(modifyProps)
    fun remove(){ remove = true }
    fun keep(){ remove = false }
    fun replaceWith(item: R){ replaceWith = item }
}

fun <Builder, R, P: Props> modification() = object: Modification<Builder, R, P>(){}
fun <Builder, R, P: Props> optionalModification(absent: Boolean = false) = (object: OptionalModification<Builder, R, P>(){}).apply {
    if(absent) remove()
}

fun <Builds, Parts> builder(builder: ModuleBuilder<Builds, Parts>): Builder<Parts> = {
    builder.run { create(it) }
}
fun <Builds, Parts> KloudFormation.builder(builder: ModuleBuilder<Builds, Parts>, partBuilder: Parts.()->Unit): Builds =
    builder.run { create(partBuilder) }

abstract class ModuleBuilder<Builds, Parts>(val parts: Parts){
    fun KloudFormation.create(builder: Parts.()->Unit): Builds {
        return parts.apply(builder).run(buildModule())
    }
    abstract fun KloudFormation.buildModule(): Parts.() -> Builds
}