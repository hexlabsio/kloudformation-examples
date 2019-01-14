package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId
import io.kloudformation.model.iam.*
import io.kloudformation.resource.aws.apigateway.Resource
import io.kloudformation.resource.aws.apigateway.RestApi
import io.kloudformation.resource.aws.apigateway.resource
import io.kloudformation.resource.aws.apigateway.restApi
import templates.*

class HttpPathModule(val resource: Resource): Module {

    class FullProps(
            val path: Value<String>,
            val parentId: Value<String>,
            val restApi: Value<String>
    ) : Props

    class Parts(
            val httpResource: Modification<Resource.Builder, Resource, ResourceProps> = modification()
    ){
        class ResourceProps(val path: Value<String>,
                            val parentId: Value<String>,
                            val restApi: Value<String>): Props
    }

    class Builder(val props: FullProps): ModuleBuilder<HttpPathModule, Parts>(Parts()){
        override fun KloudFormation.buildModule(): Parts.() -> HttpPathModule = {
            val resourceResource = httpResource(Parts.ResourceProps(props.path, props.parentId, props.restApi)) { props ->
                resource(parentId = props.parentId, restApiId = props.restApi, pathPart = props.path){
                    modifyBuilder(props)
                }
            }
            HttpPathModule(resourceResource)
        }
    }
}

class HttpModule(val restApi: RestApi) : Module, ExtraParts {
    data class HttpFullProps(
            val serviceName: String,
            val stage: String,
            val cors: CorsConfig? = null,
            val vpcEndpoint: Value<String>? = null
    ) : Props

    class CorsConfig()

    class HttpPath(val path: Value<String>): PartialProps<HttpPathModule.FullProps,HttpModule>{
        override fun fullProps(extraProps: HttpModule): HttpPathModule.FullProps {
            return HttpPathModule.FullProps(path, extraProps.restApi.RootResourceId(), extraProps.restApi.ref())
        }
    }

    class Parts(
            val httpRestApi: Modification<RestApi.Builder, RestApi, RestApiProps> = modification()
    ) {
        val path = SubModules<HttpPathModule, HttpPathModule.Parts, HttpPathModule.Builder, HttpPath, HttpPathModule.FullProps, HttpModule>(
                builder = { props -> HttpPathModule.Builder(props)}
        )
        class RestApiProps() : Props
    }
//    RestApi*
//    Resource (/1) per path
//    Method (GET) per path per method
//    Method (OPTIONS) Cors only includes requests
//    Deployment *
//    Permission
    class Builder(
            val httpProps: HttpFullProps
    ) : ModuleBuilder<HttpModule, Parts>(Parts()) {

        override fun KloudFormation.buildModule(): Parts.() -> HttpModule = {
            val restApiResource = httpRestApi(Parts.RestApiProps()) { props ->
                restApi {
                    name("${httpProps.stage}-${httpProps.serviceName}")
                    if(httpProps.vpcEndpoint != null){
                        endpointConfiguration {
                            types(listOf(+"PRIVATE"))
                        }
                        policy(policyDocument(version = IamPolicyVersion.V2.version) {
                            statement(
                                    action = action("execute-api:Invoke"),
                                    resource = resource(+"arn:aws:execute-api:us-east-1:" + awsAccountId + ":*")
                            ){
                                allPrincipals()
                                condition(ConditionOperators.stringEquals,mapOf(
                                        ConditionKey<String>("aws:sourceVpce") to listOf(httpProps.vpcEndpoint)
                                ))
                            }
                        })
                    }
                    modifyBuilder(props)
                }
            }
            val http = HttpModule(restApiResource)
            path.modules().forEach{
                it.module(http)()
            }
            http
        }
    }
}