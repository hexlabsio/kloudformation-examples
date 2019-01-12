package templates.serverless

import io.kloudformation.KloudFormation
import io.kloudformation.Value
import io.kloudformation.function.plus
import io.kloudformation.model.KloudFormationTemplate.Builder.Companion.awsAccountId
import io.kloudformation.model.iam.*
import io.kloudformation.resource.aws.apigateway.RestApi
import io.kloudformation.resource.aws.apigateway.restApi
import templates.*

class HttpModule(val restApi: RestApi) : Module {
    data class HttpFullProps(
            val method: String,
            val serviceName: String,
            val stage: String,
            val cors: CorsConfig? = null,
            val vpcEndpoint: Value<String>? = null
    ) : Props

    class CorsConfig()

    class Parts(
            val httpRestApi: Modification<RestApi.Builder, RestApi, RestApiProps> = modification()
    ) {
        class RestApiProps() : Props
    }

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
            HttpModule(restApiResource)
        }
    }
}