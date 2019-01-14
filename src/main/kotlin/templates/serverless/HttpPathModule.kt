package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.resource.aws.apigateway.Resource
import io.kloudformation.resource.aws.apigateway.resource
import io.kloudformation.module.*

class HttpPathModule(val resource: Resource): Module {

    class Predefined(var parentId: Value<String>, var restApi: Value<String>): Properties
    class Props(val path: Value<String>): Properties

    class Parts(
            val httpResource: Modification<Resource.Builder, Resource, ResourceProps> = modification()
    ){
        class ResourceProps(var path: Value<String>, var parentId: Value<String>, var restApi: Value<String>): Properties
    }

    class Builder(pre: Predefined, val props: Props): SubModuleBuilder<HttpPathModule, Parts, Predefined, Props>(pre, Parts()){
        override fun KloudFormation.buildModule(): Parts.() -> HttpPathModule = {
            val resourceResource = httpResource(Parts.ResourceProps(props.path, pre.parentId, pre.restApi)) { props ->
                resource(parentId = props.parentId, restApiId = props.restApi, pathPart = props.path){
                    modifyBuilder(props)
                }
            }
            HttpPathModule(resourceResource)
        }
    }
}