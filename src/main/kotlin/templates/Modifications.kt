package templates

import io.kloudformation.KloudFormation

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

fun <Builds: Module, Parts> builder(builder: ModuleBuilder<Builds, Parts>): Builder<Parts> = {
    builder.run { create(it) }
}
fun <Builds: Module, Parts> KloudFormation.builder(builder: ModuleBuilder<Builds, Parts>, partBuilder: Parts.()->Unit): Builds =
    builder.run { create(partBuilder) }

interface Module

abstract class ModuleBuilder<Builds: Module, Parts>(val parts: Parts){
    fun KloudFormation.create(builder: Parts.()->Unit): Builds {
        return parts.apply(builder).run(buildModule())
    }
    abstract fun KloudFormation.buildModule(): Parts.() -> Builds
}


interface ExtraParts

interface PartialProps<P:Props, X: ExtraParts>{
    fun fullProps(extraProps: X): P
}

class SubModules<Builds: Module, Parts, Builder: ModuleBuilder<Builds, Parts>, UserProps: PartialProps<P,X>, P: Props, X: ExtraParts>(
        val builder: (P) -> Builder,
        private val subModules: MutableList<SubModule<Builds, Parts, Builder, UserProps, P, X>> = mutableListOf()
){
    operator fun invoke(props: UserProps, modifications: Modification<Parts,Builds,P>.() -> Unit = {}){
        val module: SubModule<Builds, Parts, Builder, UserProps, P, X> = SubModule(builder)
        module(props, modifications)
        subModules.add(module)
    }
    fun modules() = subModules
}
class SubModule<Builds: Module, Parts, Builder: ModuleBuilder<Builds, Parts>, UserProps: PartialProps<P,X>, P: Props, X: ExtraParts>(
        val builder: (P) -> Builder,
        private var modification: Modification<Parts,Builds,P> = modification(),
        private var subModule: (KloudFormation.(X) -> Builds)? = null
){

    fun module(extraParts: X): KloudFormation.() -> Unit = {
        subModule?.invoke(this,extraParts)
    }
    operator fun KloudFormation.invoke(extraParts: X): Builds? = subModule?.invoke(this, extraParts)

    operator fun invoke(props: UserProps, modifications: Modification<Parts,Builds,P>.() -> Unit = {}){
        subModule = { subProps ->
            modification(props.fullProps(subProps)) { props ->
                apply(modifications)
                with(builder(props)){
                    this.parts.modifyBuilder(props)
                    create { }
                }
            }
        }
    }
}