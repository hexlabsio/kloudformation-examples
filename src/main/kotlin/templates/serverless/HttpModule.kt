package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId
import io.kloudformation.model.iam.*
import io.kloudformation.resource.aws.apigateway.RestApi
import io.kloudformation.resource.aws.apigateway.restApi
import io.kloudformation.module.*

class HttpModule(val restApi: RestApi, val paths: List<HttpPathModule>) : Module {
    class Predefined(var serviceName: String, var stage: String): Properties
    class Props(val cors: CorsConfig? = null, val vpcEndpoint: Value<String>? = null): Properties

    class CorsConfig

    class Parts{
        val httpRestApi = modification<RestApi.Builder, RestApi, NoProps>()
        val path = SubModules({ pre: HttpPathModule.Predefined, props: HttpPathModule.Props -> HttpPathModule.Builder(pre, props)})
    }

    class Builder(pre: Predefined, val props: Props): SubModuleBuilder<HttpModule, Parts, Predefined, Props>(pre, Parts()) {

        override fun KloudFormation.buildModule(): Parts.() -> HttpModule = {
            val restApiResource = httpRestApi(NoProps) {
                restApi {
                    name("${pre.stage}-${pre.serviceName}")
                    if(props.vpcEndpoint != null){
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
                                        ConditionKey<String>("aws:sourceVpce") to listOf(props.vpcEndpoint)
                                ))
                            }
                        })
                    }
                    modifyBuilder(it)
                }
            }
            val paths = path.modules().mapNotNull {
                it.module(HttpPathModule.Predefined(restApiResource.RootResourceId(), restApiResource.ref()))()
            }
            HttpModule(restApiResource, paths)
        }
    }
}